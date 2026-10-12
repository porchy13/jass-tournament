package ch.varani.jass.directory;

/**
 * The columns of an imported table that hold the values of the persons, chosen by the user in the preview of the
 * import.
 *
 * <p>The columns are numbered from 0; {@link #NONE} marks a value that is not in the table. The first name is
 * mandatory.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param firstName the column of the first names.
 * @param lastName the column of the last names, or {@link #NONE}.
 * @param email the column of the e-mail addresses, or {@link #NONE}.
 * @param phone the column of the phone numbers, or {@link #NONE}.
 * @param header whether the first row of the table is a header, left out of the import.
 */
public record ColumnMapping(int firstName, int lastName, int email, int phone, boolean header) {

  /** The column of a value that is not in the table. */
  public static final int NONE = -1;

  /**
   * Creates a mapping of the columns.
   *
   * @param firstName the column of the first names.
   * @param lastName the column of the last names, or {@link #NONE}.
   * @param email the column of the e-mail addresses, or {@link #NONE}.
   * @param phone the column of the phone numbers, or {@link #NONE}.
   * @param header whether the first row is a header.
   *
   * @throws IllegalArgumentException if the column of the first names is missing, or if a column is lower than
   *                                  {@link #NONE}.
   */
  public ColumnMapping {
    if (firstName < 0 || lastName < NONE || email < NONE || phone < NONE) {
      throw new IllegalArgumentException("Invalid column mapping.");
    }
  }

}
