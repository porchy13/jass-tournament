package ch.varani.jass.app;

import ch.varani.jass.directory.ColumnMapping;
import ch.varani.jass.directory.Csv;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.DirectoryImport;
import ch.varani.jass.directory.ImportRow;
import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.DomainException;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The values of the import of persons into the directory (Q17): the text of a CSV file or of rows pasted from a
 * spreadsheet, its separator, whether its first row is a header, and the columns of the values; and their preview.
 *
 * <p><b>Sequence</b>: the text is read as a table ({@link Csv#parse(String, char)}); the user maps the columns; the
 * preview ({@link DirectoryImport#preview(Directory, List, ColumnMapping)}) reports every invalid value and the
 * possible duplicates; only the valid rows are imported, the possible duplicates only when the user asks for them.
 * The separator is guessed from the first line of a new text (tabulation, then semicolon, then comma). The class holds
 * no JavaFX object: it is tested without the toolkit.</p>
 *
 * <p>Not thread-safe: confined to the JavaFX Application Thread.</p>
 */
final class ImportModel {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The text of the table. */
  private String text = "";
  /** The separator of the cells. */
  private char separator = Csv.SEMICOLON;
  /** Whether the first row is a header. */
  private boolean header = true;
  /** The field of the first names. */
  static final int FIRST_NAME = 0;
  /** The field of the last names. */
  static final int LAST_NAME = 1;
  /** The field of the e-mail addresses. */
  static final int EMAIL = 2;
  /** The field of the phone numbers. */
  static final int PHONE = 3;

  /** The columns of the fields, {@link ColumnMapping#NONE} for none; at first, the four first columns in order. */
  private final int[] columns = {FIRST_NAME, LAST_NAME, EMAIL, PHONE};

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates an empty import: no text, semicolons, a header line and the columns in the usual order.
   */
  ImportModel() {
    // Nothing more to initialize: the attributes have their initial values.
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Replaces the text of the table, and guesses its separator from its first line.
   *
   * @param value the text.
   */
  void setText(final String value) {
    text = value;
    final String first = value.lines().findFirst().orElse("");
    if (first.indexOf(Csv.TAB) >= 0) {
      separator = Csv.TAB;
    } else if (first.indexOf(Csv.SEMICOLON) >= 0) {
      separator = Csv.SEMICOLON;
    } else if (first.indexOf(Csv.COMMA) >= 0) {
      separator = Csv.COMMA;
    }
  }

  /**
   * Returns the separator of the cells.
   *
   * @return the separator.
   */
  char separator() {
    return separator;
  }

  /**
   * Changes the separator of the cells.
   *
   * @param value the separator.
   */
  void setSeparator(final char value) {
    separator = value;
  }

  /**
   * Tells whether the first row is a header.
   *
   * @return {@code true} if the first row is left out.
   */
  boolean header() {
    return header;
  }

  /**
   * Tells whether the first row is a header.
   *
   * @param value {@code true} to leave the first row out.
   */
  void setHeader(final boolean value) {
    header = value;
  }

  /**
   * Returns the column of a value.
   *
   * @param field the field: {@link #FIRST_NAME}, {@link #LAST_NAME}, {@link #EMAIL} or {@link #PHONE}.
   *
   * @return the column, from 0, or {@link ColumnMapping#NONE}.
   */
  int column(final int field) {
    return columns[field];
  }

  /**
   * Changes the column of a value.
   *
   * @param field the field: {@link #FIRST_NAME}, {@link #LAST_NAME}, {@link #EMAIL} or {@link #PHONE}.
   * @param column the column, from 0, or {@link ColumnMapping#NONE}.
   */
  void setColumn(final int field, final int column) {
    columns[field] = column;
  }

  // *******************************************************************************************************************
  // Preview
  // *******************************************************************************************************************

  /**
   * Reads the text as a table.
   *
   * @return the rows that are not blank.
   *
   * @throws DomainException if the table is not valid (quote not closed, too many rows).
   */
  List<List<String>> table() {
    return Csv.parse(text, separator);
  }

  /**
   * Returns the names of the columns: the cells of the header, or the numbers of the columns.
   *
   * @return one name per column of the widest row; empty if the table is not valid.
   */
  List<String> columnNames() {
    final List<List<String>> table;
    try {
      table = table();
    } catch (DomainException _) {
      return List.of();
    }
    final int width = table.stream().mapToInt(List::size).max().orElse(0);
    return IntStream.range(0, width)
        .mapToObj(i -> header && i < table.getFirst().size() && !table.getFirst().get(i).isBlank()
                       ? table.getFirst().get(i).strip() : Integer.toString(i + 1))
        .toList();
  }

  /**
   * Tells why the text cannot be previewed.
   *
   * @return the key of the message for the user, or nothing.
   */
  Optional<String> problem() {
    if (columns[FIRST_NAME] == ColumnMapping.NONE) {
      return Optional.of("import.first.name.missing");
    }
    try {
      table();
      return Optional.empty();
    } catch (final DomainException e) {
      return Optional.of("error.validation." + e.getError().name());
    }
  }

  /**
   * Previews the import into a directory.
   *
   * @param directory the directory.
   *
   * @return one row of preview per row of the table, the header left out; empty if there is a problem.
   */
  List<ImportRow> preview(final Directory directory) {
    if (problem().isPresent()) {
      return List.of();
    }
    return DirectoryImport.preview(directory, table(),
                                   new ColumnMapping(columns[FIRST_NAME], columns[LAST_NAME], columns[EMAIL],
                                                     columns[PHONE], header));
  }

  /**
   * Returns the persons to import from a preview: the valid rows, without the possible duplicates unless asked.
   *
   * @param preview the preview.
   * @param duplicates {@code true} to import the possible duplicates too.
   *
   * @return the new persons.
   */
  static List<Person> persons(final List<ImportRow> preview, final boolean duplicates) {
    return preview.stream()
                  .filter(ImportRow::isValid)
                  .filter(row -> duplicates || row.duplicates().isEmpty())
                  .map(ImportRow::person)
                  .toList();
  }

}
