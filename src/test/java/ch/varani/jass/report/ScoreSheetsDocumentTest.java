package ch.varani.jass.report;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Regulation;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link ScoreSheetsDocument}: two sheets per landscape page, the sides, the deals of the plan in blocks of
 * four with their subtotals, the grand totals.
 */
final class ScoreSheetsDocumentTest {

  /** The number of deals of a plan longer than the regulation. */
  private static final int LONG_PLAN = 32;
  /** The number of sheets per page. */
  private static final int SHEETS_PER_PAGE = 2;
  /** The number of blocks of the plan of the regulation, one subtotal each. */
  private static final int BLOCKS = 4;
  /** The gap between the two lines separating the grand totals from the deals, in points. */
  private static final float DOUBLE_LINE = 1.5f;
  /** The index of the height in the operands of a rectangle. */
  private static final int HEIGHT = 3;
  /** The number of operands of a rectangle. */
  private static final int RECTANGLE = 4;
  /** The number of frames of a line of totals: the points of column A, the middle, the points of column B. */
  private static final int FRAMES_PER_TOTAL = 3;
  /** The tolerance of the comparison of two positions, in points. */
  private static final float TOLERANCE = 0.01f;

  /**
   * Rules 6, 7 and 8 to 11: a sheet per game, two per landscape page, with the round, the table, the giver at the odd
   * place dealing first, the suits of the imposed trumps, the names of the blocks of the regulation, the subtotals of
   * 628 points and the grand total of 2'512 points.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rules 6 to 11: a sheet per game, the giver dealing first, the deals of the regulation in blocks")
  void regulationTest() throws Exception {
    final byte[] pdf = Samples.pdf(out -> ScoreSheetsDocument.write(Samples.teamEdition(), Samples.OPTIONS, out));
    final String text = Samples.text(pdf);
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertAll(
          () -> assertEquals(2, document.getNumberOfPages()),
          () -> assertTrue(document.getPage(0).getMediaBox().getWidth() > document.getPage(0).getMediaBox()
                                                                                      .getHeight()),
          () -> assertTrue(text.contains("Feuille de résultats"), text),
          () -> assertTrue(text.contains(Samples.EDITION_LINE), text),
          () -> assertTrue(text.contains("Partie 1 – Table 2"), text),
          () -> assertTrue(text.contains("Place 3 – donne en premier"), text),
          () -> assertTrue(text.contains("Place 4"), text),
          () -> assertTrue(text.contains("Équipe 3\nAnne3 Fontaine & Bruno3 Rochat"), text),
          () -> assertTrue(text.contains("♥"), text),
          () -> assertTrue(text.contains("♣"), text),
          () -> assertTrue(text.contains("♦"), text),
          () -> assertTrue(text.contains("♠"), text),
          () -> assertTrue(text.contains("Atout Schieber"), text),
          () -> assertTrue(text.contains("Atout Kreuz"), text),
          () -> assertTrue(text.contains("Atout forcé"), text),
          () -> assertTrue(text.contains("628 points"), text),
          () -> assertTrue(text.contains("Grands totaux"), text),
          () -> assertTrue(text.contains(points(Regulation.POINTS_PER_GAME)), text),
          () -> assertTrue(text.contains("16"), text));
    }
  }

  /**
   * F15: the sheet follows the plan of the edition: a block of mixed types shows the type on each line, the number of
   * deals and the grand total follow the plan.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F15: the sheet follows the plan of the deals of the edition")
  void planTest() throws Exception {
    final List<DealType> deals = new ArrayList<>(Collections.nCopies(LONG_PLAN - Regulation.DEALS_PER_BLOCK,
                                                                      DealType.FORCED_TRUMP));
    deals.addAll(List.of(DealType.SCHIEBER, DealType.KREUZ, DealType.IMPOSED_SPADES, DealType.SCHIEBER));
    deals.subList(0, Regulation.DEALS_PER_BLOCK).replaceAll(deal -> DealType.IMPOSED_HEARTS);
    final Edition edition = Samples.teamEdition().withDeals(new DealPlan(deals));
    final String text = Samples.text(Samples.pdf(out -> ScoreSheetsDocument.write(edition, Samples.OPTIONS, out)));
    assertAll(
        () -> assertTrue(text.contains(points(LONG_PLAN * Regulation.POINTS_PER_DEAL)), text),
        () -> assertTrue(text.contains("32"), text),
        () -> assertTrue(text.contains("Atout Schieber"), text),
        () -> assertTrue(text.contains("♠"), text),
        () -> assertTrue(text.contains("♥"), text),
        () -> assertFalse(text.contains("♦"), text));
  }

  /**
   * The subtotal lines are shaded on the whole width of the grid, the points of both sides included; the grand totals
   * are not shaded and are separated from the deals by a double line.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F10: the subtotals are shaded on the whole width, the grand totals sit under a double line")
  void totalsTest() throws Exception {
    final byte[] pdf = Samples.pdf(out -> ScoreSheetsDocument.write(Samples.teamEdition(), Samples.OPTIONS, out));
    final List<float[]> shaded = new ArrayList<>();
    final List<float[]> framed = new ArrayList<>();
    try (PDDocument document = Loader.loadPDF(pdf)) {
      final List<Object> tokens = new PDFStreamParser(document.getPage(0)).parse();
      float[] rectangle = null;
      for (int i = 0; i < tokens.size(); i++) {
        if (tokens.get(i) instanceof final Operator operator) {
          switch (operator.getName()) {
            case "re" -> rectangle = rectangle(tokens, i);
            case "f" -> shaded.add(rectangle);
            case "S" -> Optional.ofNullable(rectangle).ifPresent(framed::add);
            default -> {
              // The other operators draw no rectangle.
            }
          }
          rectangle = "re".equals(operator.getName()) ? rectangle : null;
        }
      }
    }
    final float row = shaded.getFirst()[HEIGHT];
    final List<float[]> grandTotals = framed.stream().filter(box -> Math.abs(box[HEIGHT] - 2 * row) < TOLERANCE)
                                            .toList();
    final float[] grandTotal = grandTotals.stream().min(Comparator.comparingDouble(box -> box[0])).orElseThrow();
    final float lastSubtotal = (float) shaded.stream().filter(box -> box[0] == grandTotal[0])
                                             .mapToDouble(box -> box[1]).min().orElseThrow();
    assertAll(
        () -> assertEquals(SHEETS_PER_PAGE * BLOCKS, shaded.size()),
        () -> assertTrue(shaded.stream().allMatch(box -> spansRow(box, framed)), "shaded on the whole width"),
        () -> assertFalse(grandTotals.isEmpty()),
        () -> assertTrue(grandTotals.stream().noneMatch(box -> shaded.stream().anyMatch(fill -> fill[1] == box[1])),
                         "grand totals not shaded"),
        () -> assertEquals(DOUBLE_LINE, lastSubtotal - (grandTotal[1] + 2 * row), TOLERANCE));
  }

  /**
   * Reads the rectangle of a {@code re} operator: its four operands.
   *
   * @param tokens the tokens of the content of a page.
   * @param index the index of the operator.
   *
   * @return the left, the bottom, the width and the height.
   */
  private static float[] rectangle(final List<Object> tokens, final int index) {
    final float[] rectangle = new float[RECTANGLE];
    for (int i = 0; i < RECTANGLE; i++) {
      rectangle[i] = ((COSNumber) tokens.get(index - RECTANGLE + i)).floatValue();
    }
    return rectangle;
  }

  /**
   * Tells whether a shaded rectangle covers the whole line of the grid: from the left of the leftmost frame to the
   * right of the rightmost frame of the same line (the points of column A, the middle, the points of column B).
   *
   * @param box the shaded rectangle.
   * @param framed the framed rectangles of the page.
   *
   * @return {@code true} when the shaded rectangle spans the three frames of its line.
   */
  private static boolean spansRow(final float[] box, final List<float[]> framed) {
    final List<float[]> line = framed.stream().filter(frame -> frame[1] == box[1] && frame[HEIGHT] == box[HEIGHT]
                                                               && frame[0] >= box[0] - TOLERANCE
                                                               && frame[0] < box[0] + box[2]).toList();
    final double left = line.stream().mapToDouble(frame -> frame[0]).min().orElseThrow();
    final double right = line.stream().mapToDouble(frame -> frame[0] + frame[2]).max().orElseThrow();
    return line.size() == FRAMES_PER_TOTAL && Math.abs(left - box[0]) < TOLERANCE
           && Math.abs(right - box[0] - box[2]) < TOLERANCE;
  }

  /**
   * In individual mode, each side shows its two partners with their numbers.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("In individual mode, each side shows its two partners")
  void individualTest() throws Exception {
    final String text = Samples.text(Samples.pdf(out -> ScoreSheetsDocument.write(Samples.individualEdition(),
                                                                                  Samples.OPTIONS, out)));
    assertTrue(text.contains("Zoé1 Müller (1)\nZoé2 Müller (2)"), text);
  }

  /**
   * Returns a number of points as written on the sheets in Swiss French.
   *
   * @param points the points.
   *
   * @return e.g. {@code 2 512 points}, with the grouping of the language.
   */
  private static String points(final int points) {
    return NumberFormat.getIntegerInstance(Samples.FRENCH).format(points) + " points";
  }

  /**
   * The suits of the imposed trumps and their colours.
   */
  @Test
  @DisplayName("Rule 8: the imposed suits are ♥ ♦ in red and ♣ ♠ in black")
  void suitTest() {
    assertAll(
        () -> assertEquals("♥", ScoreSheetsDocument.suit(DealType.IMPOSED_HEARTS)),
        () -> assertEquals("♣", ScoreSheetsDocument.suit(DealType.IMPOSED_CLUBS)),
        () -> assertEquals("♦", ScoreSheetsDocument.suit(DealType.IMPOSED_DIAMONDS)),
        () -> assertEquals("♠", ScoreSheetsDocument.suit(DealType.IMPOSED_SPADES)),
        () -> assertTrue(ScoreSheetsDocument.isRed(DealType.IMPOSED_HEARTS)),
        () -> assertTrue(ScoreSheetsDocument.isRed(DealType.IMPOSED_DIAMONDS)),
        () -> assertFalse(ScoreSheetsDocument.isRed(DealType.IMPOSED_CLUBS)),
        () -> assertFalse(ScoreSheetsDocument.isRed(DealType.IMPOSED_SPADES)));
  }

  /**
   * The sheets need the schedule of the edition.
   */
  @Test
  @DisplayName("An edition without schedule has no sheets")
  void noScheduleTest() {
    final Edition edition = Samples.teamEdition().withoutSchedule();
    assertThrows(IllegalStateException.class,
                 () -> ScoreSheetsDocument.write(edition, Samples.OPTIONS, new ByteArrayOutputStream()));
  }

  /**
   * The sheets of the games of some participants alone, e.g. of a team that replaces another one: team 5 plays only
   * the first game of the second round.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Replacement: the sheets of the games of the selected teams alone")
  void selectionTest() throws Exception {
    final Edition edition = Samples.teamEdition();
    final byte[] pdf = Samples.pdf(out -> ScoreSheetsDocument.write(edition, Samples.OPTIONS, Set.of(Samples.TEAMS),
                                                                    out));
    final String text = Samples.text(pdf);
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertAll(
          () -> assertEquals(1, document.getNumberOfPages()),
          () -> assertTrue(text.contains("Partie 2 – Table 1"), text),
          () -> assertFalse(text.contains("Partie 1 – Table"), text),
          () -> assertThrows(IllegalArgumentException.class, () -> ScoreSheetsDocument.write(
              edition, Samples.OPTIONS, Set.of(), new ByteArrayOutputStream())));
    }
  }

}
