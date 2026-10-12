package ch.varani.jass.directory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link DirectoryExport}: every value held about a person is exported, under the header of the user
 * interface.
 */
final class DirectoryExportTest {

  /** The labels of the columns, as given by the user interface. */
  private static final List<String> HEADER = List.of("First", "Last", "Mail", "Phone", "Notes", "Status", "Since",
                                                     "Editions");

  /**
   * The table has the header, then one row per person with all its values; the participations are listed from the
   * most recent.
   */
  @Test
  @DisplayName("Every value held about a person is exported")
  void tableTest() {
    final Person anne = People.person("Anne", "Fontaine", "anne@example.ch", "079 000 00 01")
                              .withNotes("Captain")
                              .withStatus(ContactStatus.DO_NOT_CONTACT, People.DATE)
                              .withParticipations(List.of(
                                  new Participation(People.OTHER_EDITION, "Old", People.DATE.minusYears(1), null),
                                  new Participation(People.EDITION, "New", People.DATE, People.OTHER)));
    final Person zoe = People.person("Zoé", "");
    assertEquals(List.of(HEADER,
                         List.of("Anne", "Fontaine", "anne@example.ch", "+41790000001", "Captain", "DO_NOT_CONTACT",
                                 "2026-11-20", "2026-11-20 New" + DirectoryExport.PARTICIPATION_SEPARATOR
                                               + "2025-11-20 Old"),
                         List.of("Zoé", "", "", "", "", "CONTACTABLE", "", "")),
                 DirectoryExport.table(List.of(anne, zoe), HEADER));
  }

  /**
   * The header must have one label per column.
   */
  @Test
  @DisplayName("The header must have one label per column")
  void headerTest() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> DirectoryExport.table(List.of(), List.of("x"))),
        () -> assertEquals(1, DirectoryExport.table(List.of(), HEADER).size()));
  }

}
