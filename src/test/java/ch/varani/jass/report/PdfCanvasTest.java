package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Player;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link PdfCanvas}, {@link Header}, {@link Texts} and {@link DocumentOptions}: logo, characters the font
 * cannot draw, texts too long, language of the texts.
 */
final class PdfCanvasTest {

  /** A name too long for a cell. */
  private static final String LONG_NAME = "Maximilien-Alexandre-Constantin de la Tour d'Auvergne-Lauraguais";

  /**
   * Q11: the logo of the application is drawn on every document, in PNG or JPEG.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Q11: the logo is printed on the documents")
  void logoTest() throws Exception {
    for (final String format : List.of("png", "jpg")) {
      final DocumentOptions options = new DocumentOptions(Samples.FRENCH, Samples.logo(format));
      final byte[] pdf = Samples.pdf(out -> ScheduleDocument.write(Samples.teamEdition(), options, out));
      try (PDDocument document = Loader.loadPDF(pdf)) {
        assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext(), format);
      }
    }
    final byte[] none = Samples.pdf(out -> ScheduleDocument.write(Samples.teamEdition(), Samples.OPTIONS, out));
    try (PDDocument document = Loader.loadPDF(none)) {
      assertTrue(!document.getPage(0).getResources().getXObjectNames().iterator().hasNext());
    }
  }

  /**
   * A character that the font cannot draw is replaced, a name too long for its cell is cut with an ellipsis; the
   * suits and the accents are drawn as they are.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Unknown characters are replaced, long names are cut")
  void textTest() throws Exception {
    final Edition edition = Samples.individualEdition().withRegistrations(new IndividualRegistrations(List.of(
        new Player("漢字", "♥ Zoé"), new Player(LONG_NAME, LONG_NAME), new Player("C", ""), new Player("D", ""))));
    final String text = Samples.text(Samples.pdf(out -> PlacesDocument.write(edition, Samples.OPTIONS, out)));
    try (PdfCanvas canvas = new PdfCanvas(Samples.OPTIONS)) {
      assertAll(
          () -> assertTrue(text.contains("�� ♥ Zoé"), text),
          () -> assertTrue(text.contains("Maximilien-Alexandre-Constantin de la Tour d'Auvergne-Lauraguais Max"), text),
          () -> assertTrue(text.contains("…"), text),
          () -> assertEquals("a�é🃏", canvas.printable("a一é🃏")));
    }
  }

  /**
   * Q13: the texts are in French, German, Italian or English; a language without texts falls back to English,
   * whatever the language of the system.
   */
  @Test
  @DisplayName("Q13: the texts are in French, German, Italian, English by default")
  void languageTest() {
    final Locale system = Locale.getDefault();
    try {
      Locale.setDefault(Locale.FRENCH);
      assertAll(
          () -> assertEquals("Schedule", new Texts(Locale.forLanguageTag("es-ES")).get("schedule.title")),
          () -> assertEquals("Spielplan", new Texts(Locale.forLanguageTag("de-CH")).get("schedule.title")),
          () -> assertEquals("Tabellone degli incontri",
                             new Texts(Locale.forLanguageTag("it-CH")).get("schedule.title")),
          () -> assertEquals("20. November 2026",
                             new Texts(Locale.forLanguageTag("de-CH")).date(Samples.DATE)),
          () -> assertEquals("Schedule", new Texts(Locale.ENGLISH).get("schedule.title")),
          () -> assertEquals("Tableau des rencontres", new Texts(Samples.FRENCH).get("schedule.title")),
          () -> assertEquals("Équipe 2", new Texts(Samples.FRENCH).get("team", 2)));
    } finally {
      Locale.setDefault(system);
    }
  }

  /**
   * The options copy the logo, and are equal when their locale and logo are equal.
   */
  @Test
  @DisplayName("The options copy the logo")
  void optionsTest() {
    final byte[] logo = Samples.logo("png");
    final DocumentOptions options = new DocumentOptions(Samples.FRENCH, logo);
    logo[0] = 0;
    final Object notOptions = "options";
    assertAll(
        () -> assertNotEquals(0, options.logo().orElseThrow()[0]),
        () -> assertEquals(options, new DocumentOptions(Samples.FRENCH, Samples.logo("png"))),
        () -> assertEquals(options.hashCode(), new DocumentOptions(Samples.FRENCH, Samples.logo("png")).hashCode()),
        () -> assertNotEquals(options, Samples.OPTIONS),
        () -> assertNotEquals(options, new DocumentOptions(Locale.ENGLISH, Samples.logo("png"))),
        () -> assertNotEquals(options, notOptions),
        () -> assertEquals(Samples.FRENCH, options.locale()),
        () -> assertTrue(Samples.OPTIONS.logo().isEmpty()));
  }

  /**
   * In a box too narrow for any character, only the ellipsis is drawn.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A box too narrow shows an ellipsis")
  void narrowTest() throws Exception {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (PdfCanvas canvas = new PdfCanvas(Samples.OPTIONS)) {
      canvas.newPage(PdfCanvas.PORTRAIT);
      canvas.text("Anne", 0, PdfCanvas.MM, PdfCanvas.MM, PdfCanvas.Style.BOLD, PdfCanvas.Align.LEFT, 1);
      canvas.save("Narrow", out);
    }
    assertEquals("…", Samples.text(out.toByteArray()).strip());
  }

}
