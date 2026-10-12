package ch.varani.jass.app;

import ch.varani.jass.directory.ColumnMapping;
import ch.varani.jass.directory.Csv;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.ImportRow;
import ch.varani.jass.directory.Person;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link ImportModel}: separator guessed, columns named, preview and persons to import. The persons are
 * fictitious.
 */
final class ImportModelTest {

  /**
   * The separator is guessed from the first line: tabulation, then semicolon, then comma; otherwise it is kept.
   */
  @Test
  @DisplayName("The separator is guessed from the first line")
  void separatorTest() {
    final ImportModel model = new ImportModel();
    model.setText("a\tb;c,d");
    final char tab = model.separator();
    model.setText("a;b,c");
    final char semicolon = model.separator();
    model.setText("a,b");
    final char comma = model.separator();
    model.setText("ab");
    final char kept = model.separator();
    model.setText("");
    assertAll(
        () -> assertEquals(Csv.TAB, tab),
        () -> assertEquals(Csv.SEMICOLON, semicolon),
        () -> assertEquals(Csv.COMMA, comma),
        () -> assertEquals(Csv.COMMA, kept),
        () -> assertEquals(Csv.COMMA, model.separator()));
  }

  /**
   * The columns are named by the header, or by their number; a column without title gets its number.
   */
  @Test
  @DisplayName("The columns are named by the header, or by their number")
  void columnNamesTest() {
    final ImportModel model = new ImportModel();
    model.setText("Prénom;;Mail\nAnne;Fontaine;anne@example.ch;x");
    final List<String> titled = model.columnNames();
    model.setHeader(false);
    assertAll(
        () -> assertEquals(List.of("Prénom", "2", "Mail", "4"), titled),
        () -> assertFalse(model.header()),
        () -> assertEquals(List.of("1", "2", "3", "4"), model.columnNames()));
  }

  /**
   * The preview reads the rows into persons; the valid rows are imported, the possible duplicates only when asked.
   */
  @Test
  @DisplayName("The valid rows are imported, the duplicates only when asked")
  void previewTest() {
    final Directory directory = Directory.empty().add(Person.create("Zoé", "Müller"));
    final ImportModel model = new ImportModel();
    model.setText("Prénom;Nom;Mail;Tél\nAnne;Fontaine;anne@example.ch;\nZoe;MULLER;;\nLéo;Berger;bad;");
    model.setColumn(ImportModel.PHONE, ColumnMapping.NONE);
    final List<ImportRow> preview = model.preview(directory);
    assertAll(
        () -> assertEquals(ColumnMapping.NONE, model.column(ImportModel.PHONE)),
        () -> assertEquals(Optional.empty(), model.problem()),
        () -> assertEquals(List.of("Anne Fontaine"), ImportModel.persons(preview, false).stream()
                                                                .map(Person::fullName).toList()),
        () -> assertEquals(2, ImportModel.persons(preview, true).size()),
        () -> assertFalse(preview.get(2).isValid()));
  }

  /**
   * Without the column of the first names, or with a table that is not valid, nothing is previewed.
   */
  @Test
  @DisplayName("A missing first name column or an invalid table gives no preview")
  void problemTest() {
    final ImportModel model = new ImportModel();
    model.setText("a;\"b");
    final Optional<String> malformed = model.problem();
    final List<String> names = model.columnNames();
    model.setText("a;b");
    model.setColumn(ImportModel.FIRST_NAME, ColumnMapping.NONE);
    assertAll(
        () -> assertEquals(Optional.of("error.validation.MALFORMED_TABLE"), malformed),
        () -> assertTrue(names.isEmpty()),
        () -> assertEquals(Optional.of("import.first.name.missing"), model.problem()),
        () -> assertTrue(model.preview(Directory.empty()).isEmpty()));
  }

}
