package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The score cards of the participants (F13): one card per team (or player in individual mode), on which the
 * participants write the points of each game, the running subtotal and the total (the rank is known from the final
 * ranking); four cards per A4 page,
 * separated by dashed cutting marks, as in the sheet "Points par équipe" of the former spreadsheet.
 *
 * <p>Each card shows the logo, the title, the edition (name and date), the participant (label and names) and a grid
 * of one line per game the participant plays, with the round and the place where it sits (rule 6), so that the card
 * follows the schedule: a round sat out has no line (rule 1, catch-up round); then the total, in bold, under a double
 * line, its boxes of the place and of the points of a game shaded: only its subtotal is written. The numbers (place,
 * points, subtotal) and their titles are aligned to the right, the rounds and the total to the left.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class ScoreCardsDocument {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The number of cards per row and per column of a page. */
  private static final int PER_SIDE = 2;
  /** The margin inside a card. */
  private static final float MARGIN = 10 * PdfCanvas.MM;
  /** The scale of the header of a card. */
  private static final float HEADER_SCALE = 0.75f;
  /** The size of the label of the participant. */
  private static final float LABEL = 13f;
  /** The size of the names and of the grid. */
  private static final float TEXT = 10f;
  /** The height of a line of names. */
  private static final float LINE = 5.5f * PdfCanvas.MM;
  /** The greatest height of a line of the grid. */
  private static final float ROW = 9 * PdfCanvas.MM;
  /** The space between the participant and the grid. */
  private static final float SPACE = 4 * PdfCanvas.MM;
  /** The position of the baseline in a line of the grid, from its bottom. */
  private static final float BASELINE = 0.35f;
  /** The shares of the width of the card taken by the columns: round, place, points, subtotal. */
  private static final float[] COLUMNS = {0.24f, 0.16f, 0.26f, 0.34f};
  /**
   * The alignments of the columns, the same for their titles and their cells: the round at the left, the numbers
   * (place, points, subtotal) at the right.
   */
  private static final List<PdfCanvas.Align> ALIGNS = List.of(PdfCanvas.Align.LEFT, PdfCanvas.Align.RIGHT,
                                                              PdfCanvas.Align.RIGHT, PdfCanvas.Align.RIGHT);
  /** The space between the text and the border of a cell of the grid. */
  private static final float PADDING = 1.5f * PdfCanvas.MM;
  /** The width of the lines of the grid. */
  private static final float GRID = 0.5f;
  /** The width of the cutting marks. */
  private static final float CUT = 0.3f;
  /** The gap between the two lines separating the total from the games. */
  private static final float DOUBLE_LINE = 1.5f;
  /** The columns of the grid shaded on the line of the total: the place and the points of a game. */
  private static final Set<Integer> NOT_FILLED = Set.of(1, 2);
  /** The columns of the grid shaded on the line of the column titles: all. */
  private static final Set<Integer> ALL = Set.of(0, 1, 2, 3);

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private ScoreCardsDocument() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Writes the score cards of the participants of an edition as a PDF document.
   *
   * @param edition the edition, with its schedule.
   * @param options the options of the documents.
   * @param out the stream to which the document is written, not closed.
   *
   * @throws IllegalStateException if the edition has no schedule.
   * @throws IOException if the document cannot be written.
   */
  public static void write(final Edition edition, final DocumentOptions options, final OutputStream out)
      throws IOException {
    write(edition, options, Documents.everyone(edition), out);
  }

  /**
   * Writes the score cards of some participants of an edition as a PDF document, e.g. of a participant who replaces
   * another one.
   *
   * @param edition the edition, with its schedule.
   * @param options the options of the documents.
   * @param selected the numbers of the participants whose cards are written, in increasing order.
   * @param out the stream to which the document is written, not closed.
   *
   * @throws IllegalStateException if the edition has no schedule.
   * @throws IllegalArgumentException if no participant is selected, or a number is not a registered participant.
   * @throws IOException if the document cannot be written.
   */
  public static void write(final Edition edition, final DocumentOptions options, final Set<Integer> selected,
                           final OutputStream out) throws IOException {
    final List<Pairing> pairings = Documents.pairings(edition);
    final List<Integer> numbers = Documents.selection(edition, selected);
    final Texts texts = new Texts(options.locale());
    final Participants participants = new Participants(edition, texts);
    final String title = texts.get("cards.title");
    final float width = PdfCanvas.PORTRAIT.getWidth() / PER_SIDE;
    final float height = PdfCanvas.PORTRAIT.getHeight() / PER_SIDE;
    final int perPage = PER_SIDE * PER_SIDE;
    try (PdfCanvas canvas = new PdfCanvas(options)) {
      for (int i = 0; i < numbers.size(); i++) {
        final int slot = i % perPage;
        if (slot == 0) {
          canvas.newPage(PdfCanvas.PORTRAIT);
          canvas.cutMark(width, 0, width, PdfCanvas.PORTRAIT.getHeight(), CUT);
          canvas.cutMark(0, height, PdfCanvas.PORTRAIT.getWidth(), height, CUT);
        }
        final int column = slot % PER_SIDE;
        final int row = slot / PER_SIDE;
        final float left = column * width;
        final float top = PdfCanvas.PORTRAIT.getHeight() - row * height;
        card(canvas, new Card(texts, title, edition, participants, pairings), numbers.get(i), left, top, width,
             height);
      }
      canvas.save(Documents.title(title, edition), out);
    }
  }

  /**
   * Draws the card of a participant.
   *
   * @param canvas the document.
   * @param card the values shared by the cards.
   * @param number the number of the participant.
   * @param left the left of the card.
   * @param top the top of the card.
   * @param width the width of the card.
   * @param height the height of the card.
   *
   * @throws IOException if the page cannot be written.
   */
  private static void card(final PdfCanvas canvas, final Card card, final int number, final float left,
                           final float top, final float width, final float height) throws IOException {
    final Texts texts = card.texts();
    final float x = left + MARGIN;
    final float inner = width - 2 * MARGIN;
    float y = Header.draw(canvas, texts, card.title(), card.edition(), x, top - MARGIN, inner, HEADER_SCALE) - SPACE;
    canvas.text(card.participants().label(number), x, y - LABEL, LABEL, PdfCanvas.Style.BOLD, PdfCanvas.Align.LEFT,
                inner);
    y -= LABEL + LINE / 2;
    for (final String name : card.participants().names(number)) {
      y -= LINE;
      canvas.text(name, x, y, TEXT, PdfCanvas.Style.REGULAR, PdfCanvas.Align.LEFT, inner);
    }
    y -= SPACE;
    final List<List<String>> lines = new ArrayList<>();
    for (final int round : card.pairings().stream().map(Pairing::round).distinct().toList()) {
      Documents.place(card.pairings(), round, number).ifPresent(
          place -> lines.add(List.of(texts.get("round", round), place.toString(), "", "")));
    }
    final float row = Math.min(ROW, (y - DOUBLE_LINE - (top - height + MARGIN)) / (lines.size() + 2));
    final List<Float> widths = new ArrayList<>();
    for (final float share : COLUMNS) {
      widths.add(inner * share);
    }
    gridRow(canvas, List.of(texts.get("card.game"), texts.get("card.place"), texts.get("card.points"),
                            texts.get("card.subtotal")), new Grid(x, widths, row, PdfCanvas.Style.BOLD, ALL),
            y - row);
    final Grid games = new Grid(x, widths, row, PdfCanvas.Style.REGULAR, Set.of());
    for (int i = 0; i < lines.size(); i++) {
      gridRow(canvas, lines.get(i), games, y - (i + 2) * row);
    }
    gridRow(canvas, List.of(texts.get("card.total"), "", "", ""),
            new Grid(x, widths, row, PdfCanvas.Style.BOLD, NOT_FILLED), y - (lines.size() + 2) * row - DOUBLE_LINE);
  }

  /**
   * Draws a line of the grid of a card: its cells, with their texts (the round and the place, the column titles or
   * the title of the total), the shaded ones first.
   *
   * @param canvas the document.
   * @param cells the texts of the cells.
   * @param grid the columns of the grid and the look of the line.
   * @param y the bottom of the line.
   *
   * @throws IOException if the page cannot be written.
   */
  private static void gridRow(final PdfCanvas canvas, final List<String> cells, final Grid grid, final float y)
      throws IOException {
    float left = grid.x();
    for (int i = 0; i < grid.widths().size(); i++) {
      final float width = grid.widths().get(i);
      if (grid.shaded().contains(i)) {
        canvas.shade(left, y, width, grid.row());
      }
      canvas.box(left, y, width, grid.row(), GRID);
      canvas.text(cells.get(i), left + PADDING, y + grid.row() * BASELINE, TEXT, grid.style(), ALIGNS.get(i),
                  width - 2 * PADDING);
      left += width;
    }
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The values shared by the cards of a document.
   *
   * @param texts the texts of the documents.
   * @param title the title of the cards.
   * @param edition the edition.
   * @param participants the participants of the edition.
   * @param pairings the games of the schedule.
   */
  private record Card(Texts texts, String title, Edition edition, Participants participants,
                      List<Pairing> pairings) {
  }

  /**
   * The columns of the grid of a card and the look of one of its lines.
   *
   * @param x the left of the grid.
   * @param widths the widths of the columns.
   * @param row the height of a line.
   * @param style the style of the texts of the line.
   * @param shaded the indexes of the columns whose boxes are shaded on the line.
   */
  private record Grid(float x, List<Float> widths, float row, PdfCanvas.Style style, Set<Integer> shaded) {
  }

}
