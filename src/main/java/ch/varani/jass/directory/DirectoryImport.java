package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.ValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The preview of an import of persons into the directory, from a CSV file or from rows pasted from a spreadsheet,
 * read by {@link Csv#parse(String, char)}.
 *
 * <p>Sequence: the user maps the columns ({@link ColumnMapping}); {@link #preview(Directory, List, ColumnMapping)}
 * reads each row into a new person (new identifier, contactable), checks every value (names, e-mail address, phone
 * number) and reports all the errors of the row, then looks for the duplicates among the persons of the directory and
 * the previous valid rows; the user reviews the preview and confirms; the application then adds the chosen persons
 * ({@link Directory#addAll(java.util.Collection)}). Nothing is added before the confirmation.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class DirectoryImport {

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private DirectoryImport() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Reads the rows of a table into the preview of an import.
   *
   * @param directory the directory into which the persons are imported.
   * @param table the rows of the table.
   * @param mapping the columns of the values.
   *
   * @return one row of preview per row of the table, the header left out.
   */
  public static List<ImportRow> preview(final Directory directory, final List<List<String>> table,
                                        final ColumnMapping mapping) {
    final List<ImportRow> preview = new ArrayList<>();
    final List<Person> accepted = new ArrayList<>();
    for (int i = mapping.header() ? 1 : 0; i < table.size(); i++) {
      final ImportRow row = readRow(i + 1, table.get(i), mapping, directory.addAll(accepted));
      if (row.isValid()) {
        accepted.add(row.person());
      }
      preview.add(row);
    }
    return preview;
  }

  /**
   * Reads a row into a person, collecting all its errors.
   *
   * @param line the number of the row, starting at 1.
   * @param cells the cells of the row.
   * @param mapping the columns of the values.
   * @param known the directory and the previous valid rows, to look for the duplicates.
   *
   * @return the row of the preview.
   */
  private static ImportRow readRow(final int line, final List<String> cells, final ColumnMapping mapping,
                                   final Directory known) {
    final List<ValidationError> errors = new ArrayList<>();
    final Player name = check(errors, cell(cells, mapping.firstName()), false,
                              first -> new Player(first, cell(cells, mapping.lastName())));
    final EmailAddress email = check(errors, cell(cells, mapping.email()), true, EmailAddress::new);
    final PhoneNumber phone = check(errors, cell(cells, mapping.phone()), true, PhoneNumber::new);
    if (!errors.isEmpty()) {
      return new ImportRow(line, null, errors, List.of());
    }
    final Person person = Person.create(name.firstName(), name.lastName()).withEmail(email).withPhone(phone);
    return new ImportRow(line, person, List.of(), known.duplicatesOf(person));
  }

  /**
   * Converts a cell into a value, recording the error of the domain when the value is rejected.
   *
   * @param <T> the type of the value.
   * @param errors the errors of the row, completed when the value is rejected.
   * @param cell the content of the cell.
   * @param optional whether the value is optional: a blank cell then gives no value and no error.
   * @param conversion the conversion of the cell, which rejects an invalid value.
   *
   * @return the value, or {@code null} if the cell is blank and the value optional, or if the value is rejected.
   */
  private static <T> T check(final List<ValidationError> errors, final String cell, final boolean optional,
                             final Function<String, T> conversion) {
    if (optional && cell.isBlank()) {
      return null;
    }
    try {
      return conversion.apply(cell);
    } catch (final DomainException e) {
      errors.add(e.getError());
      return null;
    }
  }

  /**
   * Returns the content of a cell of a row.
   *
   * @param cells the cells of the row.
   * @param column the column, or {@link ColumnMapping#NONE}.
   *
   * @return the content of the cell, or an empty text if the column is missing or beyond the end of the row.
   */
  private static String cell(final List<String> cells, final int column) {
    return column >= 0 && column < cells.size() ? cells.get(column) : "";
  }

}
