package ch.varani.jass.directory;

import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link DirectoryImport} and {@link ImportRow}: reading of the rows into persons, report of every error,
 * detection of the duplicates. The persons are fictitious.
 */
final class DirectoryImportTest {

  /** The mapping of a table with a header: first name, last name, e-mail address, phone number. */
  private static final ColumnMapping ALL_COLUMNS = new ColumnMapping(0, 1, 2, 3, true);
  /** The number of the third row of a table. */
  private static final int THIRD = 3;
  /** The number of the fourth row of a table. */
  private static final int FOURTH = 4;

  /**
   * A valid row gives a new contactable person with its values; the header is left out; the line numbers count it.
   */
  @Test
  @DisplayName("A valid row gives a new person; the header is left out")
  void validRowTest() {
    final List<ImportRow> preview = DirectoryImport.preview(
        Directory.empty(), Csv.parse("First;Last;Mail;Phone\nAnne ; Fontaine;ANNE@example.ch;079 000 00 01\nZoé",
                                     Csv.SEMICOLON), ALL_COLUMNS);
    final Person anne = preview.get(0).person();
    assertAll(
        () -> assertEquals(2, preview.size()),
        () -> assertEquals(2, preview.get(0).line()),
        () -> assertTrue(preview.get(0).isValid()),
        () -> assertEquals("Anne Fontaine", anne.fullName()),
        () -> assertEquals("anne@example.ch", anne.email().value()),
        () -> assertEquals("+41790000001", anne.phone().value()),
        () -> assertEquals(ContactStatus.CONTACTABLE, anne.status()),
        () -> assertEquals("Zoé", preview.get(1).person().fullName()),
        () -> assertNull(preview.get(1).person().email()),
        () -> assertNull(preview.get(1).person().phone()));
  }

  /**
   * Every error of an invalid row is reported, and the row has no person.
   */
  @Test
  @DisplayName("Every error of an invalid row is reported")
  void invalidRowTest() {
    final List<ImportRow> preview = DirectoryImport.preview(
        Directory.empty(), List.of(List.of(" ", "Fontaine", "not-an-address", "12"), List.of("A\u0007")),
        new ColumnMapping(0, 1, 2, 3, false));
    assertAll(
        () -> assertFalse(preview.get(0).isValid()),
        () -> assertNull(preview.get(0).person()),
        () -> assertEquals(List.of(ValidationError.BLANK_FIRST_NAME, ValidationError.INVALID_EMAIL,
                                   ValidationError.INVALID_PHONE), preview.get(0).errors()),
        () -> assertEquals(List.of(), preview.get(0).duplicates()),
        () -> assertEquals(List.of(ValidationError.INVALID_CHARACTER), preview.get(1).errors()));
  }

  /**
   * The duplicates are looked for in the directory and in the previous valid rows of the import.
   */
  @Test
  @DisplayName("The duplicates are looked for in the directory and in the previous rows")
  void duplicatesTest() {
    final Person anne = People.person("Anne", "Fontaine", "anne@example.ch", "079 000 00 01");
    final List<ImportRow> preview = DirectoryImport.preview(
        Directory.empty().add(anne),
        List.of(List.of("Eve", "Rochat", "anne@example.ch"), List.of("Léo", "Berger"), List.of(" "),
                List.of("leo", "BERGER")),
        new ColumnMapping(0, 1, 2, ColumnMapping.NONE, false));
    assertAll(
        () -> assertEquals(List.of(anne), preview.get(0).duplicates()),
        () -> assertEquals(List.of(), preview.get(1).duplicates()),
        () -> assertEquals(THIRD, preview.get(2).line()),
        () -> assertEquals(List.of(preview.get(1).person()), preview.get(THIRD).duplicates()),
        () -> assertEquals(FOURTH, preview.get(THIRD).line()));
  }

  /**
   * The rows of a preview copy their lists.
   */
  @Test
  @DisplayName("A row of the preview copies its lists")
  void importRowTest() {
    final ImportRow row = new ImportRow(1, null, List.of(ValidationError.INVALID_EMAIL), List.of());
    assertAll(
        () -> assertThrows(UnsupportedOperationException.class, () -> row.errors().clear()),
        () -> assertThrows(NullPointerException.class, () -> new ImportRow(1, null, null, List.of())));
  }

}
