package ch.varani.jass.directory;

import ch.varani.jass.domain.ValidationError;

import java.util.List;

/**
 * A row of the preview of an import into the directory ({@link DirectoryImport}): the person read from the row, or
 * the reasons why it cannot be read, and the persons it may duplicate.
 *
 * <p>An invalid row is reported, never silently dropped; nothing is added to the directory before the user
 * confirms.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param line the number of the row in the table, starting at 1 (header included).
 * @param person the person read from the row, or {@code null} if the row is invalid.
 * @param errors the reasons why the row is invalid (blank first name, invalid character, e-mail or phone), empty if
 *               it is valid.
 * @param duplicates the persons of the directory, or of the previous rows of the import, that may be the same person.
 */
public record ImportRow(int line, Person person, List<ValidationError> errors, List<Person> duplicates) {

  /**
   * Creates a row of the preview.
   *
   * @param line the number of the row.
   * @param person the person, or {@code null}.
   * @param errors the reasons why the row is invalid.
   * @param duplicates the possible duplicates.
   */
  public ImportRow {
    errors = List.copyOf(errors);
    duplicates = List.copyOf(duplicates);
  }

  /**
   * Tells whether the row can be imported.
   *
   * @return {@code true} if the row has no error.
   */
  public boolean isValid() {
    return errors.isEmpty();
  }

}
