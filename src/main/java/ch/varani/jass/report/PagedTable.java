package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.IOException;
import java.util.List;

/**
 * A table spread over A4 pages, portrait or landscape: each page starts with the header of the document (logo,
 * title, edition); the table may be split into sections, each with a heading; the row of the column titles is
 * repeated after each heading and at the top of each page. A section, its column titles and its first row are kept on
 * the same page. A document may start a new page itself ({@link #newPage()}), e.g. one per section, knowing how many
 * rows a page holds ({@link #capacity(int)}).
 *
 * <p>Not thread-safe: an instance lays out one document, from one thread.</p>
 */
final class PagedTable {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The margins of the page. */
  static final float MARGIN = 15 * PdfCanvas.MM;
  /** The height of a row. */
  static final float ROW_HEIGHT = 7 * PdfCanvas.MM;
  /** The size of the texts of the cells. */
  private static final float TEXT = 9.5f;
  /** The size of the headings of the sections. */
  private static final float HEADING = 13f;
  /** The space between the baseline of a heading and the table below it. */
  private static final float DESCENT = 1.5f * PdfCanvas.MM;
  /** The space above a heading. */
  private static final float HEADING_SPACE = 4 * PdfCanvas.MM;
  /** The height of the bottom margin kept for the page numbers. */
  private static final float BOTTOM = 20 * PdfCanvas.MM;
  /** The height of the baseline of the page numbers. */
  private static final float PAGE_NUMBER = 10 * PdfCanvas.MM;
  /** The size of the page numbers. */
  private static final float PAGE_NUMBER_SIZE = 8f;
  /** The space between the text and the border of a cell. */
  private static final float PADDING = 1.5f * PdfCanvas.MM;
  /** The position of the baseline in a row, from its bottom. */
  private static final float BASELINE = 2.3f * PdfCanvas.MM;
  /** The width of the lines of the table. */
  private static final float LINE = 0.5f;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The document. */
  private final PdfCanvas canvas;
  /** The size of the pages. */
  private final PDRectangle page;
  /** The texts of the documents. */
  private final Texts texts;
  /** The title of the document. */
  private final String title;
  /** The edition. */
  private final Edition edition;
  /** The widths of the columns, the last one taking the remaining width when it is 0. */
  private final float[] widths;
  /** The titles of the columns. */
  private final List<String> columns;
  /** The top of the next element on the current page. */
  private float y;
  /** The top of the first element of a page, under its header. */
  private float pageTop;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a table and its first page.
   *
   * @param canvas the document.
   * @param page the size of the pages, e.g. {@link PdfCanvas#PORTRAIT}.
   * @param texts the texts of the documents.
   * @param title the title of the document.
   * @param edition the edition.
   * @param widths the widths of the columns; a width of 0 shares the remaining width with the other ones of 0.
   * @param columns the titles of the columns.
   *
   * @throws IOException if the page cannot be written.
   */
  PagedTable(final PdfCanvas canvas, final PDRectangle page, final Texts texts, final String title,
             final Edition edition, final float[] widths, final List<String> columns) throws IOException {
    this.canvas = canvas;
    this.page = page;
    this.texts = texts;
    this.title = title;
    this.edition = edition;
    this.columns = List.copyOf(columns);
    this.widths = share(widths, page.getWidth() - 2 * MARGIN);
    newPage();
  }

  /**
   * Shares the remaining width of the table among the columns of width 0.
   *
   * @param widths the widths of the columns, 0 for a flexible column.
   * @param total the width of the table.
   *
   * @return the widths of all the columns.
   */
  private static float[] share(final float[] widths, final float total) {
    float fixed = 0;
    int flexible = 0;
    for (final float width : widths) {
      fixed += width;
      flexible += width == 0 ? 1 : 0;
    }
    final float[] shared = widths.clone();
    for (int i = 0; i < shared.length; i++) {
      if (shared[i] == 0) {
        shared[i] = (total - fixed) / flexible;
      }
    }
    return shared;
  }

  // *******************************************************************************************************************
  // Layout
  // *******************************************************************************************************************

  /**
   * Starts a new page with the header of the document.
   *
   * @throws IOException if the page cannot be written.
   */
  void newPage() throws IOException {
    canvas.newPage(page);
    final float top = page.getHeight() - MARGIN;
    y = Header.draw(canvas, texts, title, edition, MARGIN, top, page.getWidth() - 2 * MARGIN, 1) - HEADING_SPACE;
    pageTop = y;
  }

  /**
   * Returns the number of rows that a section started at the top of a page holds on that page: the lines under its
   * heading and its column titles, as {@link #row(List)} and {@link #note(String)} lay them out, less the lines kept
   * for other uses (e.g. a note).
   *
   * @param kept the number of lines kept for other uses than rows, at least 0.
   *
   * @return the number of rows, at least 1.
   */
  int capacity(final int kept) {
    float at = pageTop - (HEADING_SPACE + HEADING) - ROW_HEIGHT;
    int lines = 0;
    while (at - ROW_HEIGHT >= BOTTOM) {
      at -= ROW_HEIGHT;
      lines++;
    }
    return Math.max(1, lines - kept);
  }

  /**
   * Starts a new page if the given height does not fit on the current one.
   *
   * @param height the height needed.
   *
   * @return {@code true} if a new page was started.
   *
   * @throws IOException if the page cannot be written.
   */
  private boolean ensure(final float height) throws IOException {
    if (y - height >= BOTTOM) {
      return false;
    }
    newPage();
    return true;
  }

  /**
   * Draws the row of the column titles, shaded.
   *
   * @throws IOException if the page cannot be written.
   */
  void titles() throws IOException {
    ensure(2 * ROW_HEIGHT);
    canvas.shade(MARGIN, y - ROW_HEIGHT, page.getWidth() - 2 * MARGIN, ROW_HEIGHT);
    cells(columns, PdfCanvas.Style.BOLD);
  }

  /**
   * Draws the heading of a section, then the row of the column titles, on a new page if they do not fit with one
   * row.
   *
   * @param heading the heading.
   *
   * @throws IOException if the page cannot be written.
   */
  void section(final String heading) throws IOException {
    ensure(HEADING_SPACE + HEADING + 2 * ROW_HEIGHT);
    y -= HEADING_SPACE + HEADING;
    canvas.text(heading, MARGIN, y + DESCENT, HEADING, PdfCanvas.Style.BOLD, PdfCanvas.Align.LEFT,
                page.getWidth() - 2 * MARGIN);
    titles();
  }

  /**
   * Draws a row of the table, on a new page (with the column titles) if it does not fit.
   *
   * @param cells the texts of the cells.
   *
   * @throws IOException if the page cannot be written.
   */
  void row(final List<String> cells) throws IOException {
    if (ensure(ROW_HEIGHT)) {
      titles();
    }
    cells(cells, PdfCanvas.Style.REGULAR);
  }

  /**
   * Draws a line of text below the table, e.g. the participants sitting out.
   *
   * @param text the text.
   *
   * @throws IOException if the page cannot be written.
   */
  void note(final String text) throws IOException {
    ensure(ROW_HEIGHT);
    y -= ROW_HEIGHT;
    canvas.text(text, MARGIN, y + BASELINE, TEXT, PdfCanvas.Style.REGULAR, PdfCanvas.Align.LEFT,
                page.getWidth() - 2 * MARGIN);
  }

  /**
   * Draws the cells of a row with their borders and moves below the row.
   *
   * @param cells the texts of the cells.
   * @param style the style of the texts.
   *
   * @throws IOException if the page cannot be written.
   */
  private void cells(final List<String> cells, final PdfCanvas.Style style) throws IOException {
    y -= ROW_HEIGHT;
    float x = MARGIN;
    for (int i = 0; i < widths.length; i++) {
      canvas.box(x, y, widths[i], ROW_HEIGHT, LINE);
      canvas.text(cells.get(i), x + PADDING, y + BASELINE, TEXT, style, PdfCanvas.Align.LEFT,
                  widths[i] - 2 * PADDING);
      x += widths[i];
    }
  }

  /**
   * Ends the document: numbers the pages.
   *
   * @throws IOException if a page cannot be written.
   */
  void finish() throws IOException {
    canvas.numberPages(texts, PAGE_NUMBER, PAGE_NUMBER_SIZE);
  }

}
