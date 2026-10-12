package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes tables of text cells: CSV files (RFC 4180) and rows pasted from a spreadsheet (separated by
 * tabulations).
 *
 * <p><b>Reading</b> ({@link #parse(String, char)}): a byte order mark at the start is ignored; the rows end with
 * {@code \r\n}, {@code \n} or {@code \r}; a cell enclosed in double quotes may contain the separator, line breaks and
 * doubled quotes; a quote that is not closed makes the table invalid. The rows whose cells are all blank are left out.
 * At most {@link #MAX_ROWS} rows are read.</p>
 *
 * <p><b>Writing</b> ({@link #format(List, char)}): the rows end with {@code \r\n}; a cell containing the separator, a
 * quote or a line break is enclosed in quotes. <b>Formula injection</b> (OWASP, CSV injection): a cell starting with
 * {@code =}, {@code +}, {@code -}, {@code @}, a tabulation or a carriage return is prefixed with a single quote, so
 * that a spreadsheet never evaluates it (a phone number in the format E.164 is thus written {@code '+41…}).</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class Csv {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The separator of the CSV files written by the application and usual in Switzerland. */
  public static final char SEMICOLON = ';';
  /** The separator of the CSV files of most other spreadsheets. */
  public static final char COMMA = ',';
  /** The separator of the rows copied from a spreadsheet. */
  public static final char TAB = '\t';
  /** The maximum number of rows of a table, header included. */
  public static final int MAX_ROWS = 10_000;
  /** The quote enclosing a cell. */
  private static final char QUOTE = '"';
  /** The byte order mark, ignored at the start of a text. */
  private static final char BYTE_ORDER_MARK = '\uFEFF';
  /** The end of a row written by the application. */
  private static final String LINE_END = "\r\n";
  /** The characters that start a formula in a spreadsheet. */
  private static final String FORMULA_STARTS = "=+-@\t\r";
  /** The prefix that prevents a spreadsheet from evaluating a cell. */
  private static final char FORMULA_GUARD = '\'';

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private Csv() {
  }

  // *******************************************************************************************************************
  // Reading
  // *******************************************************************************************************************

  /**
   * Reads a table.
   *
   * <p>Algorithm: a single pass over the characters with a state, inside or outside quotes; outside quotes, the
   * separator ends a cell and a line break ends a row ({@code \r\n} counting as one); inside quotes, a doubled quote is
   * a quote and a single quote closes the quotes.</p>
   *
   * @param text the text of the table.
   * @param separator the separator of the cells, e.g. {@link #SEMICOLON} or {@link #TAB}.
   *
   * @return the rows that are not blank, each a list of cells.
   *
   * @throws DomainException with {@link ValidationError#MALFORMED_TABLE} if a quote is not closed, or with
   *                         {@link ValidationError#TOO_MANY_ROWS} if the table has more than {@link #MAX_ROWS} rows.
   */
  public static List<List<String>> parse(final String text, final char separator) {
    final Parser parser = new Parser(separator);
    int next = !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? 1 : 0;
    while (next < text.length()) {
      next = parser.accept(text, next) + 1;
    }
    return parser.finish();
  }

  // *******************************************************************************************************************
  // Writing
  // *******************************************************************************************************************

  /**
   * Writes a table.
   *
   * @param rows the rows, each a list of cells.
   * @param separator the separator of the cells.
   *
   * @return the text of the table, each row ended by {@code \r\n}.
   */
  public static String format(final List<List<String>> rows, final char separator) {
    final StringBuilder text = new StringBuilder();
    for (final List<String> row : rows) {
      for (int i = 0; i < row.size(); i++) {
        if (i > 0) {
          text.append(separator);
        }
        text.append(formatCell(row.get(i), separator));
      }
      text.append(LINE_END);
    }
    return text.toString();
  }

  /**
   * Writes a cell: guarded against formula injection, then quoted when needed.
   *
   * @param cell the content of the cell.
   * @param separator the separator of the cells.
   *
   * @return the cell as written.
   */
  private static String formatCell(final String cell, final char separator) {
    final String guarded = !cell.isEmpty() && FORMULA_STARTS.indexOf(cell.charAt(0)) >= 0 ? FORMULA_GUARD + cell : cell;
    if (guarded.indexOf(separator) < 0 && guarded.indexOf(QUOTE) < 0 && guarded.indexOf('\n') < 0
        && guarded.indexOf('\r') < 0) {
      return guarded;
    }
    return QUOTE + guarded.replace("\"", "\"\"") + QUOTE;
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The state of the reading of a table.
   *
   * <p>Not thread-safe: confined to one reading.</p>
   */
  private static final class Parser {

    /** The separator of the cells. */
    private final char separator;
    /** The rows read, without the blank ones. */
    private final List<List<String>> rows = new ArrayList<>();
    /** The cells of the current row. */
    private List<String> row = new ArrayList<>();
    /** The content of the current cell. */
    private final StringBuilder cell = new StringBuilder();
    /** Whether the reading is inside quotes. */
    private boolean quoted;

    /**
     * Creates the state of a reading.
     *
     * @param separator the separator of the cells.
     */
    Parser(final char separator) {
      this.separator = separator;
    }

    /**
     * Reads a character of the text.
     *
     * @param text the text of the table.
     * @param index the index of the character.
     *
     * @return the index of the last character consumed (the next one for a doubled quote or {@code \r\n}).
     *
     * @throws DomainException with {@link ValidationError#TOO_MANY_ROWS} if the table has too many rows.
     */
    int accept(final String text, final int index) {
      final char c = text.charAt(index);
      final boolean hasNext = index + 1 < text.length();
      if (quoted) {
        return acceptQuoted(c, hasNext && text.charAt(index + 1) == QUOTE, index);
      }
      if (c == QUOTE && cell.isEmpty()) {
        quoted = true;
      } else if (c == separator) {
        endCell();
      } else if (c == '\n' || c == '\r') {
        endRow();
        return c == '\r' && hasNext && text.charAt(index + 1) == '\n' ? index + 1 : index;
      } else {
        cell.append(c);
      }
      return index;
    }

    /**
     * Reads a character inside quotes.
     *
     * @param c the character.
     * @param quoteFollows whether the next character is a quote.
     * @param index the index of the character.
     *
     * @return the index of the last character consumed.
     */
    private int acceptQuoted(final char c, final boolean quoteFollows, final int index) {
      if (c != QUOTE) {
        cell.append(c);
        return index;
      }
      if (quoteFollows) {
        cell.append(QUOTE);
        return index + 1;
      }
      quoted = false;
      return index;
    }

    /**
     * Ends the current cell.
     */
    private void endCell() {
      row.add(cell.toString());
      cell.setLength(0);
    }

    /**
     * Ends the current row, kept unless all its cells are blank.
     *
     * @throws DomainException with {@link ValidationError#TOO_MANY_ROWS} if the table has too many rows.
     */
    private void endRow() {
      endCell();
      if (row.stream().anyMatch(value -> !value.isBlank())) {
        if (rows.size() == MAX_ROWS) {
          throw new DomainException(ValidationError.TOO_MANY_ROWS);
        }
        rows.add(List.copyOf(row));
      }
      row = new ArrayList<>();
    }

    /**
     * Ends the reading.
     *
     * @return the rows read.
     *
     * @throws DomainException with {@link ValidationError#MALFORMED_TABLE} if a quote is not closed, or with
     *                         {@link ValidationError#TOO_MANY_ROWS} if the table has too many rows.
     */
    List<List<String>> finish() {
      if (quoted) {
        throw new DomainException(ValidationError.MALFORMED_TABLE);
      }
      endRow();
      return List.copyOf(rows);
    }

  }

}
