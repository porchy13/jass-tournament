package ch.varani.jass.report;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.TrumpMode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * The score sheets of the games (F10, rule 7), modelled on the former sheet {@code F-MAT-1}: two sheets per A4
 * landscape page, separated by a dashed cutting mark, one sheet per game, sorted by round then table.
 *
 * <p><b>Content of a sheet</b>: the logo, the title and the edition (name and date); the round and the table; the two
 * sides, each with its place and its players, the giver (place {@code 2k − 1}, column A) marked as dealing first
 * (rule 6); then the grid of the deals of the plan of the edition (F15), in blocks of
 * {@value Regulation#DEALS_PER_BLOCK} deals: each line has the box of column A, the number of the deal, its type and
 * the box of column B; each block ends with a subtotal line, shaded on the whole width, showing the points of a block;
 * the sheet ends, under a double line, with the grand totals and the points of a game of the plan (2'512 for the
 * regulation), not shaded.</p>
 *
 * <p><b>Type of the deals</b>: an imposed trump shows its suit on its line (♥ ♦ in red, ♣ ♠ in black); the other
 * types show their name once for the whole block when the four deals of the block have that type (e.g. "Atout
 * Schieber"), otherwise on each line.</p>
 *
 * <p>The height of the lines adapts to the number of deals of the plan. Utility class without state, safe for
 * concurrent use.</p>
 */
public final class ScoreSheetsDocument {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The number of sheets per page. */
  private static final int PER_PAGE = 2;
  /** The margin inside a sheet. */
  private static final float MARGIN = 8 * PdfCanvas.MM;
  /** The scale of the header of a sheet. */
  private static final float HEADER_SCALE = 0.7f;
  /** The size of the line of the round and the table. */
  private static final float GAME = 12f;
  /** The size of the texts. */
  private static final float TEXT = 9f;
  /** The size of the suits. */
  private static final float SUIT = 12f;
  /** The height of a line of the boxes of the sides. */
  private static final float SIDE_LINE = 4.5f * PdfCanvas.MM;
  /** The number of lines of the boxes of the sides. */
  private static final int SIDE_LINES = 3;
  /** The space between two elements. */
  private static final float SPACE = 3 * PdfCanvas.MM;
  /** The space between the text and the border of a box. */
  private static final float PADDING = 1.5f * PdfCanvas.MM;
  /** The greatest height of a line of the grid. */
  private static final float ROW = 7 * PdfCanvas.MM;
  /** The number of grid lines taken by the grand totals. */
  private static final int TOTAL_ROWS = 2;
  /** The position of the baseline in a line, as a share of its height. */
  private static final float BASELINE = 0.33f;
  /** The share of the width taken by each column of points. */
  private static final float POINTS_COLUMN = 0.32f;
  /** The share of the width taken by each column of deal numbers. */
  private static final float NUMBER_COLUMN = 0.08f;
  /** The width of the frames of the points. */
  private static final float FRAME = 0.8f;
  /** The width of the other lines. */
  private static final float THIN = 0.4f;
  /** The width of the cutting mark. */
  private static final float CUT = 0.3f;
  /** The gap between the two lines separating the grand totals from the deals. */
  private static final float DOUBLE_LINE = 1.5f;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private ScoreSheetsDocument() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Writes the score sheets of the games of an edition as a PDF document.
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
    final List<Pairing> pairings = Documents.pairings(edition);
    final Texts texts = new Texts(options.locale());
    final Sheet sheet = new Sheet(texts, edition, new Participants(edition, texts));
    final float width = PdfCanvas.LANDSCAPE.getWidth() / PER_PAGE;
    final float height = PdfCanvas.LANDSCAPE.getHeight();
    try (PdfCanvas canvas = new PdfCanvas(options)) {
      for (int i = 0; i < pairings.size(); i++) {
        if (i % PER_PAGE == 0) {
          canvas.newPage(PdfCanvas.LANDSCAPE);
          canvas.cutMark(width, 0, width, height, CUT);
        }
        sheet.draw(canvas, pairings.get(i), i % PER_PAGE * width + MARGIN, height - MARGIN, width - 2 * MARGIN,
                   height - 2 * MARGIN);
      }
      canvas.save(Documents.title(texts.get("sheets.title"), edition), out);
    }
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The drawing of one score sheet.
   *
   * <p>Not thread-safe: confined to the writing of one document.</p>
   *
   * @param texts the texts of the documents.
   * @param edition the edition.
   * @param participants the participants of the edition.
   */
  private record Sheet(Texts texts, Edition edition, Participants participants) {

    /**
     * Draws the sheet of a game in its half of the page.
     *
     * @param canvas the document.
     * @param game the game.
     * @param left the left of the sheet.
     * @param top the top of the sheet.
     * @param width the width of the sheet.
     * @param height the height of the sheet.
     *
     * @throws IOException if the page cannot be written.
     */
    void draw(final PdfCanvas canvas, final Pairing game, final float left, final float top, final float width,
              final float height) throws IOException {
      float y = Header.draw(canvas, texts, texts.get("sheets.title"), edition, left, top, width, HEADER_SCALE);
      y -= SPACE + GAME;
      canvas.text(texts.get("sheet.game", game.round(), game.table()), left, y, GAME, PdfCanvas.Style.BOLD,
                  PdfCanvas.Align.LEFT, width);
      y -= SPACE;
      final float half = (width - SPACE) / 2;
      side(canvas, texts.get("place", 2 * game.table() - 1) + " – " + texts.get("sheet.gives"), game.giver(), left,
           y, half);
      side(canvas, texts.get("place", 2 * game.table()), game.receiver(), left + half + SPACE, y, half);
      y -= SIDE_LINES * SIDE_LINE + SPACE;
      grid(canvas, left, y, width, y - (top - height));
    }

    /**
     * Draws the box of a side: its place, then its players.
     *
     * @param canvas the document.
     * @param place the line of the place.
     * @param side the participants of the side.
     * @param x the left of the box.
     * @param top the top of the box.
     * @param width the width of the box.
     *
     * @throws IOException if the page cannot be written.
     */
    private void side(final PdfCanvas canvas, final String place, final List<Integer> side, final float x,
                      final float top, final float width) throws IOException {
      canvas.box(x, top - SIDE_LINES * SIDE_LINE, width, SIDE_LINES * SIDE_LINE, THIN);
      canvas.text(place, x + PADDING, top - SIDE_LINE + SIDE_LINE * BASELINE, TEXT, PdfCanvas.Style.BOLD,
                  PdfCanvas.Align.LEFT, width - 2 * PADDING);
      final List<String> lines = participants.sideLines(side);
      for (int i = 0; i < lines.size(); i++) {
        canvas.text(lines.get(i), x + PADDING, top - (i + 2) * SIDE_LINE + SIDE_LINE * BASELINE, TEXT,
                    PdfCanvas.Style.REGULAR, PdfCanvas.Align.LEFT, width - 2 * PADDING);
      }
    }

    /**
     * Draws the grid of the deals: the blocks of deals with their subtotal lines, then the grand totals.
     *
     * @param canvas the document.
     * @param left the left of the grid.
     * @param top the top of the grid.
     * @param width the width of the grid.
     * @param height the height available for the grid.
     *
     * @throws IOException if the page cannot be written.
     */
    private void grid(final PdfCanvas canvas, final float left, final float top, final float width,
                      final float height) throws IOException {
      final DealPlan plan = edition.deals();
      final float row = Math.min(ROW, (height - DOUBLE_LINE) / (plan.size() + plan.blocks() + TOTAL_ROWS));
      final Columns columns = new Columns(left, width);
      float y = top;
      for (int block = 0; block < plan.blocks(); block++) {
        final List<DealType> deals = plan.deals().subList(block * Regulation.DEALS_PER_BLOCK,
                                                          (block + 1) * Regulation.DEALS_PER_BLOCK);
        final boolean merged = deals.stream().allMatch(deal -> deal == deals.getFirst())
                               && deals.getFirst().mode() != TrumpMode.IMPOSED_TRUMP;
        for (int i = 0; i < deals.size(); i++) {
          y -= row;
          deal(canvas, columns, y, row, block * Regulation.DEALS_PER_BLOCK + i + 1, merged ? null : deals.get(i));
        }
        if (merged) {
          canvas.box(columns.label, y, columns.labelWidth, deals.size() * row, THIN);
          canvas.text(trump(deals.getFirst().mode()), columns.label, y + deals.size() * row / 2 - TEXT / 2, TEXT,
                      PdfCanvas.Style.BOLD, PdfCanvas.Align.CENTER, columns.labelWidth);
        }
        y -= row;
        canvas.shade(columns.left, y, columns.width(), row);
        total(canvas, columns, y, row, row,
              texts.get("sheet.points", texts.number(Regulation.POINTS_PER_BLOCK)));
      }
      y -= TOTAL_ROWS * row + DOUBLE_LINE;
      total(canvas, columns, y, TOTAL_ROWS * row, row, texts.get("sheet.grand.total"));
      canvas.text(texts.get("sheet.points", texts.number(plan.pointsPerGame())), columns.first, y + row * BASELINE,
                  TEXT, PdfCanvas.Style.BOLD, PdfCanvas.Align.CENTER, columns.middleWidth());
    }

    /**
     * Draws the line of a deal: the boxes of the points, the numbers of the deal and its type.
     *
     * @param canvas the document.
     * @param columns the columns of the grid.
     * @param y the bottom of the line.
     * @param row the height of the line.
     * @param number the number of the deal, from 1.
     * @param type the type of the deal, or {@code null} when its type is shown once for its block.
     *
     * @throws IOException if the page cannot be written.
     */
    private void deal(final PdfCanvas canvas, final Columns columns, final float y, final float row,
                      final int number, final DealType type) throws IOException {
      canvas.box(columns.left, y, columns.pointsWidth, row, FRAME);
      canvas.box(columns.right, y, columns.pointsWidth, row, FRAME);
      for (final float x : new float[] {columns.first, columns.second}) {
        canvas.box(x, y, columns.numberWidth, row, THIN);
        canvas.text(Integer.toString(number), x, y + row * BASELINE, TEXT, PdfCanvas.Style.REGULAR,
                    PdfCanvas.Align.CENTER, columns.numberWidth);
      }
      if (type == null) {
        return;
      }
      canvas.box(columns.label, y, columns.labelWidth, row, THIN);
      if (type.mode() == TrumpMode.IMPOSED_TRUMP) {
        canvas.text(suit(type), columns.label, y + row * BASELINE, SUIT, isRed(type)
                    ? PdfCanvas.Style.RED : PdfCanvas.Style.REGULAR, PdfCanvas.Align.CENTER, columns.labelWidth);
      } else {
        canvas.text(trump(type.mode()), columns.label, y + row * BASELINE, TEXT, PdfCanvas.Style.REGULAR,
                    PdfCanvas.Align.CENTER, columns.labelWidth);
      }
    }

    /**
     * Draws a line of totals: the framed boxes of the points and a label in the middle.
     *
     * @param canvas the document.
     * @param columns the columns of the grid.
     * @param y the bottom of the line.
     * @param height the height of the line, one or more lines of the grid.
     * @param row the height of a line of the grid, to place the label in the first one.
     * @param label the label of the line.
     *
     * @throws IOException if the page cannot be written.
     */
    private void total(final PdfCanvas canvas, final Columns columns, final float y, final float height,
                       final float row, final String label) throws IOException {
      canvas.box(columns.first, y, columns.middleWidth(), height, THIN);
      canvas.box(columns.left, y, columns.pointsWidth, height, FRAME);
      canvas.box(columns.right, y, columns.pointsWidth, height, FRAME);
      canvas.text(label, columns.first, y + height - row + row * BASELINE, TEXT, PdfCanvas.Style.BOLD,
                  PdfCanvas.Align.CENTER, columns.middleWidth());
    }

    /**
     * Returns the name of a trump mode in the language of the documents.
     *
     * @param mode the trump mode.
     *
     * @return e.g. {@code Atout Schieber}.
     */
    private String trump(final TrumpMode mode) {
      return texts.get("trump." + mode.name());
    }

  }

  /**
   * The positions and widths of the columns of the grid: points of column A, number of the deal, type of the deal,
   * number of the deal, points of column B.
   *
   * @param left the left of the points of column A.
   * @param first the left of the first column of numbers.
   * @param label the left of the column of the types.
   * @param second the left of the second column of numbers.
   * @param right the left of the points of column B.
   * @param pointsWidth the width of a column of points.
   * @param numberWidth the width of a column of numbers.
   * @param labelWidth the width of the column of the types.
   */
  private record Columns(float left, float first, float label, float second, float right, float pointsWidth,
                         float numberWidth, float labelWidth) {

    /**
     * Computes the columns of a grid.
     *
     * @param left the left of the grid.
     * @param width the width of the grid.
     */
    Columns(final float left, final float width) {
      this(left, left + width * POINTS_COLUMN, left + width * (POINTS_COLUMN + NUMBER_COLUMN),
           left + width * (1 - POINTS_COLUMN - NUMBER_COLUMN), left + width * (1 - POINTS_COLUMN),
           width * POINTS_COLUMN, width * NUMBER_COLUMN, width * (1 - 2 * POINTS_COLUMN - 2 * NUMBER_COLUMN));
    }

    /**
     * Returns the width between the columns of points: numbers and types.
     *
     * @return the width of the middle of the grid.
     */
    float middleWidth() {
      return right - first;
    }

    /**
     * Returns the width of the whole grid, from the points of column A to the points of column B.
     *
     * @return the width of the grid.
     */
    float width() {
      return right + pointsWidth - left;
    }

  }

  /**
   * Returns the symbol of the suit of an imposed trump.
   *
   * @param type the type of the deal, an imposed trump.
   *
   * @return ♥, ♣, ♦ or ♠.
   */
  static String suit(final DealType type) {
    return switch (type) {
      case IMPOSED_HEARTS -> "♥";
      case IMPOSED_CLUBS -> "♣";
      case IMPOSED_DIAMONDS -> "♦";
      default -> "♠";
    };
  }

  /**
   * Tells whether the suit of an imposed trump is printed in red.
   *
   * @param type the type of the deal, an imposed trump.
   *
   * @return {@code true} for hearts and diamonds.
   */
  static boolean isRed(final DealType type) {
    return type == DealType.IMPOSED_HEARTS || type == DealType.IMPOSED_DIAMONDS;
  }

}
