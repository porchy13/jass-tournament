package ch.varani.jass.directory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The export of persons of the directory as a table, e.g. to answer a request for access of a person (Swiss data
 * protection act) or to keep a copy in a spreadsheet; written by {@link Csv#format(List, char)}.
 *
 * <p>The table has a header row, whose labels are given by the user interface in the language of the user, then one
 * row per person with every value held about it, in {@link #COLUMNS} columns: first name, last name, e-mail address,
 * phone number (E.164), notes, contact status (constant name), date of the status (ISO 8601, empty if none), and the
 * participations (date and name of each edition, separated by {@link #PARTICIPATION_SEPARATOR}, the most recent
 * first).</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class DirectoryExport {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The number of columns of the table. */
  public static final int COLUMNS = 8;
  /** The separator of the participations in their cell. */
  static final String PARTICIPATION_SEPARATOR = " | ";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private DirectoryExport() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Builds the table of the export.
   *
   * @param persons the exported persons, in the order of the rows.
   * @param header the labels of the {@link #COLUMNS} columns, localized by the user interface.
   *
   * @return the rows of the table, the header first.
   *
   * @throws IllegalArgumentException if the header does not have {@link #COLUMNS} labels.
   */
  public static List<List<String>> table(final List<Person> persons, final List<String> header) {
    if (header.size() != COLUMNS) {
      throw new IllegalArgumentException("The header must have " + COLUMNS + " labels.");
    }
    final List<List<String>> rows = new ArrayList<>();
    rows.add(List.copyOf(header));
    for (final Person person : persons) {
      rows.add(List.of(person.firstName(), person.lastName(),
                       person.email() == null ? "" : person.email().value(),
                       person.phone() == null ? "" : person.phone().value(),
                       person.notes(), person.status().name(),
                       Objects.toString(person.statusDate(), ""), participations(person)));
    }
    return rows;
  }

  /**
   * Writes the participations of a person in one cell.
   *
   * @param person the person.
   *
   * @return the date and name of each edition, the most recent first.
   */
  private static String participations(final Person person) {
    return person.participations().stream()
                 .sorted((first, second) -> second.editionDate().compareTo(first.editionDate()))
                 .map(participation -> participation.editionDate() + " " + participation.editionName())
                 .collect(Collectors.joining(PARTICIPATION_SEPARATOR));
  }

}
