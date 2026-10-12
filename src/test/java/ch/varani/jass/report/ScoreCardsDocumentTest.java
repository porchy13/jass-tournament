package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link ScoreCardsDocument}: one card per participant, four per page, with the place of each game.
 */
final class ScoreCardsDocumentTest {

  /** The number of cards on a page. */
  private static final int CARDS_PER_PAGE = 4;
  /** The number of shaded boxes of a card: the 4 column titles, the place and the points of the total. */
  private static final int SHADED_PER_CARD = 6;

  /**
   * F13: each team has a card with the edition, its names, one line per game it plays with its round and its place,
   * then the total, without rank; five teams need two pages of four cards.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F13: a card per team, with the round and the place of each game it plays")
  void cardsTest() throws Exception {
    final byte[] pdf = Samples.pdf(out -> ScoreCardsDocument.write(Samples.teamEdition(), Samples.OPTIONS, out));
    final String text = Samples.text(pdf);
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertAll(
          () -> assertEquals(2, document.getNumberOfPages()),
          () -> assertTrue(text.contains("Carte de points"), text),
          () -> assertTrue(text.contains(Samples.EDITION_LINE), text),
          () -> assertTrue(text.contains("Équipe 5\nAnne5 Fontaine\nBruno5 Rochat"), text),
          () -> assertTrue(text.contains("Partie Place Points Sous-total"), text),
          () -> assertTrue(text.contains("""
              Équipe 1
              Anne1 Fontaine
              Bruno1 Rochat
              Partie Place Points Sous-total
              Partie 1 1
              Total"""), text),
          () -> assertTrue(text.contains("""
              Équipe 2
              Anne2 Fontaine
              Bruno2 Rochat
              Partie Place Points Sous-total
              Partie 1 2
              Partie 2 2
              Total"""), text),
          () -> assertTrue(text.contains("""
              Équipe 5
              Anne5 Fontaine
              Bruno5 Rochat
              Partie Place Points Sous-total
              Partie 2 1
              Total"""), text),
          () -> assertFalse(text.contains("Rang"), text),
          () -> assertFalse(text.contains("Page 1"), text));
    }
  }

  /**
   * F13: the line of the total is written in bold, under a double line, and its boxes of the place and of the points
   * are shaded, as the column titles: per card, 4 shaded title boxes and 2 shaded total boxes.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F13: the total is bold, its place and points boxes are shaded")
  void totalTest() throws Exception {
    final byte[] pdf = Samples.pdf(out -> ScoreCardsDocument.write(Samples.teamEdition(), Samples.OPTIONS, out));
    final List<String> bold = new ArrayList<>();
    final List<Object> operators = new ArrayList<>();
    try (PDDocument document = Loader.loadPDF(pdf)) {
      final PDFTextStripper stripper = new PDFTextStripper() {

        /**
         * Keeps the texts written in bold.
         *
         * @param text the text.
         * @param positions its characters.
         */
        @Override
        protected void writeString(final String text, final List<TextPosition> positions) {
          if (positions.getFirst().getFont().getName().contains("Bold")) {
            bold.add(text);
          }
        }

      };
      stripper.getText(document);
      operators.addAll(new PDFStreamParser(document.getPage(0)).parse());
    }
    final long fills = operators.stream().filter(token -> token instanceof final Operator operator
                                                          && "f".equals(operator.getName())).count();
    assertAll(
        () -> assertTrue(bold.contains("Total"), bold.toString()),
        () -> assertTrue(bold.contains("Équipe 1"), bold.toString()),
        () -> assertFalse(bold.contains("Partie 1"), bold.toString()),
        () -> assertEquals(CARDS_PER_PAGE * SHADED_PER_CARD, fills));
  }

  /**
   * The cards need the schedule of the edition, for the places.
   */
  @Test
  @DisplayName("An edition without schedule has no cards")
  void noScheduleTest() {
    final Edition edition = Samples.teamEdition().withoutSchedule();
    assertThrows(IllegalStateException.class,
                 () -> ScoreCardsDocument.write(edition, Samples.OPTIONS, new ByteArrayOutputStream()));
  }

  /**
   * In individual mode, each player has a card with its place.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("In individual mode, each player has a card")
  void individualTest() throws Exception {
    final String text = Samples.text(Samples.pdf(out -> ScoreCardsDocument.write(Samples.individualEdition(),
                                                                                 Samples.OPTIONS, out)));
    assertTrue(text.contains("Joueur 3\nZoé3 Müller\nPartie Place Points Sous-total\nPartie 1 2"), text);
  }


  /**
   * The places and the titles of the columns of numbers (place, points, subtotal) are aligned to the right of their
   * column, the rounds to the left.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F13: the places are aligned to the right, as the titles of the columns of numbers")
  void alignTest() throws Exception {
    final List<Samples.Word> words = Samples.words(Samples.pdf(
        out -> ScoreCardsDocument.write(Samples.teamEdition(), Samples.OPTIONS, out)));
    final Samples.Word game = Samples.first(words, "Partie");
    final Samples.Word place = Samples.first(words, "Place");
    final Samples.Word round = Samples.below(words, "Partie 1", game);
    final List<Samples.Word> line = words.stream().filter(word -> word.y() == round.y()).toList();
    assertAll(
        () -> assertEquals(game.start(), round.start(), Samples.TOLERANCE),
        () -> assertEquals(place.end(), line.get(1).end(), Samples.TOLERANCE));
  }

}
