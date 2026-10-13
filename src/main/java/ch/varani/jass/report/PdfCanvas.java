package ch.varani.jass.report;

import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A PDF document being drawn with PDFBox: pages of A4 size, texts in the embedded font DejaVu Sans (regular and bold,
 * subsets embedded), lines, boxes, the logo of the application and the page numbers.
 *
 * <p><b>Font</b>: DejaVu Sans (licence of Bitstream Vera and public domain, {@code fonts/DejaVu-LICENSE.txt}) covers
 * the accents, the typographic signs and the suits ♥ ♣ ♦ ♠ (Q20). A character that the font cannot draw is replaced by
 * U+FFFD, so that a name never prevents a document from being generated.</p>
 *
 * <p><b>Texts</b> ({@link #text(String, float, float, float, Style, Align, float)}): a text too wide for its box is
 * reduced down to {@link #MIN_SCALE} of its size, then cut and ended by an ellipsis.</p>
 *
 * <p>The coordinates are in PDF points from the bottom left corner of the page; {@link #MM} converts millimetres.</p>
 *
 * <p><b>Printing</b>: the document asks the viewer to print on one side of the paper (viewer preference
 * {@code /Duplex /Simplex}), so that the cards and the sheets to cut have nothing on their back. It is a preference
 * only: the viewers that honour it (Adobe Acrobat Reader) preselect one-sided printing, the others ignore it.</p>
 *
 * <p>Not thread-safe: an instance draws one document, from one thread.</p>
 */
final class PdfCanvas implements Closeable {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The number of PDF points in a millimetre. */
  static final float MM = 72f / 25.4f;
  /** The A4 page, portrait. */
  static final PDRectangle PORTRAIT = PDRectangle.A4;
  /** The A4 page, landscape. */
  static final PDRectangle LANDSCAPE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
  /** The smallest scale applied to a text too wide for its box, before cutting it. */
  static final float MIN_SCALE = 0.7f;
  /** The step of the reduction of a text too wide for its box. */
  private static final float SCALE_STEP = 0.95f;
  /** The character replacing the characters that the font cannot draw. */
  private static final int REPLACEMENT = 0xFFFD;
  /** The ellipsis ending a text cut to fit its box. */
  private static final String ELLIPSIS = "…";
  /** The units of the widths of the glyphs in an em. */
  private static final float GLYPH_UNITS = 1000f;
  /** The length of the dashes and of the gaps of a dashed line. */
  private static final float DASH = 3f;
  /** The grey of the shaded boxes. */
  private static final float SHADE = 0.9f;
  /** The red of the hearts and diamonds. */
  private static final float RED = 0.8f;
  /** The resource of the regular font. */
  private static final String REGULAR_FONT = "fonts/DejaVuSans.ttf";
  /** The resource of the bold font. */
  private static final String BOLD_FONT = "fonts/DejaVuSans-Bold.ttf";

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The horizontal alignment of a text in its box.
   */
  enum Align {

    /** The text starts at the left of the box. */
    LEFT,

    /** The text is centred in the box. */
    CENTER,

    /** The text ends at the right of the box, e.g. a number. */
    RIGHT

  }

  /**
   * The style of a text.
   */
  enum Style {

    /** Regular black text. */
    REGULAR,

    /** Bold black text. */
    BOLD,

    /** Regular red text, for the suits ♥ and ♦. */
    RED

  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The document. */
  private final PDDocument document;
  /** The parsed regular font, to look up the glyphs. */
  private final TrueTypeFont regularFile;
  /** The parsed bold font, to look up the glyphs. */
  private final TrueTypeFont boldFile;
  /** The regular font of the document. */
  private final PDType0Font regular;
  /** The bold font of the document. */
  private final PDType0Font bold;
  /** The characters of the fonts. */
  private final CmapLookup characters;
  /** The logo, or {@code null} without logo. */
  private final PDImageXObject logo;
  /** The content of the current page, or {@code null} before the first page. */
  private PDPageContentStream content;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates an empty document with its fonts and logo.
   *
   * @param options the options of the documents (the logo).
   *
   * @throws IOException if a font or the logo cannot be read.
   */
  PdfCanvas(final DocumentOptions options) throws IOException {
    document = new PDDocument();
    final PDViewerPreferences preferences = new PDViewerPreferences(new COSDictionary());
    preferences.setDuplex(PDViewerPreferences.DUPLEX.Simplex);
    document.getDocumentCatalog().setViewerPreferences(preferences);
    regularFile = parse(REGULAR_FONT);
    boldFile = parse(BOLD_FONT);
    regular = PDType0Font.load(document, regularFile, true);
    bold = PDType0Font.load(document, boldFile, true);
    characters = regularFile.getUnicodeCmapLookup();
    final byte[] image = options.logo().orElse(null);
    logo = image == null ? null : PDImageXObject.createFromByteArray(document, image, "logo");
  }

  /**
   * Parses a font of the resources.
   *
   * @param resource the name of the resource, relative to this class.
   *
   * @return the parsed font.
   *
   * @throws IOException if the font cannot be read.
   */
  private static TrueTypeFont parse(final String resource) throws IOException {
    try (InputStream in = PdfCanvas.class.getResourceAsStream(resource)) {
      return new TTFParser().parse(new RandomAccessReadBuffer(in));
    }
  }

  // *******************************************************************************************************************
  // Pages
  // *******************************************************************************************************************

  /**
   * Starts a new page, which becomes the page drawn.
   *
   * @param size the size of the page, {@link #PORTRAIT} or {@link #LANDSCAPE}.
   *
   * @throws IOException if the page cannot be created.
   */
  void newPage(final PDRectangle size) throws IOException {
    closePage();
    final PDPage page = new PDPage(size);
    document.addPage(page);
    content = new PDPageContentStream(document, page);
  }

  /**
   * Ends the content of the current page, if any.
   *
   * @throws IOException if the content cannot be written.
   */
  private void closePage() throws IOException {
    if (content != null) {
      content.close();
      content = null;
    }
  }

  /**
   * Writes the number of each page and the number of pages at the bottom of every page, centred.
   *
   * @param texts the texts of the documents.
   * @param bottom the height of the baseline above the bottom of the page.
   * @param size the size of the font.
   *
   * @throws IOException if a page cannot be written.
   */
  void numberPages(final Texts texts, final float bottom, final float size) throws IOException {
    closePage();
    final int pages = document.getNumberOfPages();
    for (int i = 0; i < pages; i++) {
      final PDPage page = document.getPage(i);
      content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true);
      text(texts.get("page", i + 1, pages), 0, bottom, size, Style.REGULAR, Align.CENTER,
           page.getMediaBox().getWidth());
      closePage();
    }
  }

  /**
   * Writes the document with its title.
   *
   * @param title the title of the document, in its properties.
   * @param out the stream to which the document is written, not closed.
   *
   * @throws IOException if the document cannot be written.
   */
  void save(final String title, final OutputStream out) throws IOException {
    closePage();
    document.getDocumentInformation().setTitle(title);
    document.getDocumentInformation().setProducer("Jass Tournament");
    document.save(out);
  }

  /**
   * Releases the document and its fonts.
   *
   * @throws IOException if a resource cannot be released.
   */
  @Override
  public void close() throws IOException {
    closePage();
    document.close();
    regularFile.close();
    boldFile.close();
  }

  // *******************************************************************************************************************
  // Drawing
  // *******************************************************************************************************************

  /**
   * Draws a text on one line, in a box of a given width: the text is reduced, then cut, to fit the box.
   *
   * @param text the text.
   * @param x the left of the box.
   * @param y the baseline of the text.
   * @param size the size of the font.
   * @param style the style of the text.
   * @param align the alignment of the text in the box.
   * @param width the width of the box.
   *
   * @throws IOException if the page cannot be written.
   */
  void text(final String text, final float x, final float y, final float size, final Style style, final Align align,
            final float width) throws IOException {
    final PDType0Font font = style == Style.BOLD ? bold : regular;
    float fitted = size;
    String shown = printable(text);
    while (width(shown, fitted, font) > width && fitted > size * MIN_SCALE) {
      fitted *= SCALE_STEP;
    }
    if (width(shown, fitted, font) > width) {
      shown = cut(shown, fitted, font, width);
    }
    final float shift = switch (align) {
      case LEFT -> 0;
      case CENTER -> (width - width(shown, fitted, font)) / 2;
      case RIGHT -> width - width(shown, fitted, font);
    };
    content.beginText();
    content.setFont(font, fitted);
    if (style == Style.RED) {
      content.setNonStrokingColor(RED, 0, 0);
    } else {
      content.setNonStrokingColor(0f);
    }
    content.newLineAtOffset(x + shift, y);
    content.showText(shown);
    content.endText();
  }

  /**
   * Cuts a text too wide for its box: removes its last characters until the text followed by an ellipsis fits.
   *
   * @param text the text, printable.
   * @param size the size of the font.
   * @param font the font.
   * @param width the width of the box.
   *
   * @return the cut text ended by an ellipsis (the ellipsis alone in a very narrow box).
   *
   * @throws IOException if the font cannot measure the text.
   */
  private static String cut(final String text, final float size, final PDType0Font font, final float width)
      throws IOException {
    String kept = text;
    while (!kept.isEmpty() && width(kept + ELLIPSIS, size, font) > width) {
      kept = kept.substring(0, kept.offsetByCodePoints(kept.length(), -1));
    }
    return kept + ELLIPSIS;
  }

  /**
   * Returns the width of a text.
   *
   * @param text the text, printable.
   * @param size the size of the font.
   * @param font the font.
   *
   * @return the width in points.
   *
   * @throws IOException if the font cannot measure the text.
   */
  private static float width(final String text, final float size, final PDType0Font font) throws IOException {
    return font.getStringWidth(text) / GLYPH_UNITS * size;
  }

  /**
   * Replaces the characters that the font cannot draw by U+FFFD.
   *
   * @param text the text.
   *
   * @return the printable text.
   */
  String printable(final String text) {
    final StringBuilder printable = new StringBuilder();
    text.codePoints().forEach(codePoint -> printable.appendCodePoint(
        characters.getGlyphId(codePoint) > 0 ? codePoint : REPLACEMENT));
    return printable.toString();
  }

  /**
   * Draws a dashed line, a cutting mark.
   *
   * @param x1 the horizontal position of the start.
   * @param y1 the vertical position of the start.
   * @param x2 the horizontal position of the end.
   * @param y2 the vertical position of the end.
   * @param width the width of the line.
   *
   * @throws IOException if the page cannot be written.
   */
  void cutMark(final float x1, final float y1, final float x2, final float y2, final float width) throws IOException {
    content.setLineWidth(width);
    content.setLineDashPattern(new float[] {DASH, DASH}, 0);
    content.moveTo(x1, y1);
    content.lineTo(x2, y2);
    content.stroke();
  }

  /**
   * Draws the frame of a box.
   *
   * @param x the left of the box.
   * @param y the bottom of the box.
   * @param width the width of the box.
   * @param height the height of the box.
   * @param lineWidth the width of the frame.
   *
   * @throws IOException if the page cannot be written.
   */
  void box(final float x, final float y, final float width, final float height, final float lineWidth)
      throws IOException {
    content.setLineWidth(lineWidth);
    content.setLineDashPattern(new float[0], 0);
    content.addRect(x, y, width, height);
    content.stroke();
  }

  /**
   * Fills a box in light grey, e.g. a header or a subtotal.
   *
   * @param x the left of the box.
   * @param y the bottom of the box.
   * @param width the width of the box.
   * @param height the height of the box.
   *
   * @throws IOException if the page cannot be written.
   */
  void shade(final float x, final float y, final float width, final float height) throws IOException {
    content.setNonStrokingColor(SHADE);
    content.addRect(x, y, width, height);
    content.fill();
    content.setNonStrokingColor(0f);
  }

  /**
   * Draws the logo in a box, keeping its proportions, at the top left of the box.
   *
   * @param x the left of the box.
   * @param top the top of the box.
   * @param maxWidth the width of the box.
   * @param maxHeight the height of the box.
   *
   * @return the width of the logo drawn, 0 without logo.
   *
   * @throws IOException if the page cannot be written.
   */
  float logo(final float x, final float top, final float maxWidth, final float maxHeight) throws IOException {
    if (logo == null) {
      return 0;
    }
    final float scale = Math.min(maxWidth / logo.getWidth(), maxHeight / logo.getHeight());
    final float width = logo.getWidth() * scale;
    final float height = logo.getHeight() * scale;
    content.drawImage(logo, x, top - height, width, height);
    return width;
  }

}
