package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;

import java.io.IOException;

/**
 * The header of the documents and of the cards and sheets: the logo of the application, the title of the document
 * and the edition it concerns, i.e. its name and its date (F4).
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class Header {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The size of the title at scale 1. */
  private static final float TITLE = 16f;
  /** The size of the line of the edition at scale 1. */
  private static final float EDITION = 11f;
  /** The space between the logo and the texts. */
  private static final float GAP = 3 * PdfCanvas.MM;
  /** The ratio of the line height to the font size. */
  private static final float LEADING = 1.3f;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private Header() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Draws a header: the logo at the left, the title and the edition (name – date) beside it.
   *
   * @param canvas the document.
   * @param texts the texts of the documents.
   * @param title the title of the document.
   * @param edition the edition.
   * @param left the left of the header.
   * @param top the top of the header.
   * @param width the width of the header.
   * @param scale the scale of the header, 1 for a page, less for a card or a sheet.
   *
   * @return the bottom of the header.
   *
   * @throws IOException if the page cannot be written.
   */
  static float draw(final PdfCanvas canvas, final Texts texts, final String title, final Edition edition,
                    final float left, final float top, final float width, final float scale) throws IOException {
    final float titleSize = TITLE * scale;
    final float editionSize = EDITION * scale;
    final float height = (titleSize + editionSize) * LEADING;
    final float logo = canvas.logo(left, top, width / 2, height);
    final float x = logo > 0 ? left + logo + GAP * scale : left;
    canvas.text(title, x, top - titleSize, titleSize, PdfCanvas.Style.BOLD, PdfCanvas.Align.LEFT, left + width - x);
    canvas.text(line(texts, edition), x, top - height + editionSize * (LEADING - 1), editionSize,
                PdfCanvas.Style.REGULAR, PdfCanvas.Align.LEFT, left + width - x);
    return top - height;
  }

  /**
   * Returns the line identifying an edition: its name and its date.
   *
   * @param texts the texts of the documents.
   * @param edition the edition.
   *
   * @return e.g. {@code Match aux cartes 2026 – 20 novembre 2026}.
   */
  static String line(final Texts texts, final Edition edition) {
    return edition.name() + " – " + texts.date(edition.date());
  }

}
