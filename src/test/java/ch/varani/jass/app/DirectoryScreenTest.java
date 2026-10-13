package ch.varani.jass.app;

import ch.varani.jass.directory.ColumnMapping;
import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Csv;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.EmailAddress;
import ch.varani.jass.directory.Participation;
import ch.varani.jass.directory.Person;
import ch.varani.jass.directory.PhoneNumber;
import ch.varani.jass.persistence.DirectoryStore;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.disabled;
import static ch.varani.jass.app.UiSupport.menu;
import static ch.varani.jass.app.UiSupport.menuItem;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.text;
import static ch.varani.jass.app.UiSupport.type;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the directory and of its import (phase 10, F14): search, filter by edition, form, contact status,
 * duplicates, deletion, erasure, merge, copies of the contact details, export and import with preview; in French,
 * on a temporary data folder with fictitious persons.
 */
@ExtendWith(ApplicationExtension.class)
final class DirectoryScreenTest {

  /** The number of persons of the directory of the tests. */
  private static final int PERSONS = 4;
  /** The column of the phone numbers in the pasted rows, recognised by its title. */
  private static final int PHONE_COLUMN = 3;
  /** The number of times the pointer is moved onto a tool before its tooltip is expected. */
  private static final int HOVER_ATTEMPTS = 3;
  /** The time given to a tooltip to appear after each move of the pointer, in seconds. */
  private static final long HOVER_SECONDS = 5;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** Another temporary folder, for the exported and imported files. */
  @TempDir
  private Path other;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();
  /** The person Anne. */
  private Person anne;
  /** The person Bruno. */
  private Person bruno;

  /**
   * Fills the directory with four fictitious persons, Anne and Bruno having played together in 2025, then starts
   * the application and shows the directory.
   *
   * @param stage the stage provided by TestFX.
   *
   * @throws Exception never.
   */
  @Start
  void start(final Stage stage) throws Exception {
    final UUID edition = UUID.randomUUID();
    bruno = Person.create("Bruno", "Rochat").withPhone(new PhoneNumber("079 000 00 02"));
    anne = Person.create("Anne", "Fontaine").withEmail(new EmailAddress("anne@example.ch"))
                 .withParticipations(List.of(new Participation(edition, "Match 2025", UiSupport.TODAY.minusYears(1),
                                                               bruno.id())));
    bruno = bruno.withParticipations(List.of(new Participation(edition, "Match 2025",
                                                               UiSupport.TODAY.minusYears(1), anne.id())));
    final Person leo = Person.create("Léo", "Berger").withEmail(new EmailAddress("leo@example.ch"))
                             .withStatus(ContactStatus.DO_NOT_CONTACT, UiSupport.TODAY)
                             .withParticipations(List.of(new Participation(UUID.randomUUID(), "Match 2024",
                                                                           UiSupport.TODAY.minusYears(2), null)));
    new DirectoryStore(folder).save(Directory.empty().add(anne).add(bruno).add(leo).add(Person.create("Zoé", "")));
    new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(), UiSupport.CLOCK, Solving::of,
                                       false, FakeUpdater.NONE))
        .start(stage);
  }

  /**
   * Shows the directory from the menu.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if it is not shown in time.
   */
  private static void showDirectory(final FxRobot robot) throws Exception {
    await(() -> shown(robot, "openButton"));
    menu(robot, "directoryItem");
    await(() -> shown(robot, "search"));
  }

  /**
   * Returns the table of the persons.
   *
   * @param robot the TestFX robot.
   *
   * @return the table.
   */
  @SuppressWarnings("unchecked")
  private static TableView<Person> persons(final FxRobot robot) {
    return robot.lookup("#table").queryAs(TableView.class);
  }

  /**
   * Selects persons in the table by their first names.
   *
   * @param robot the TestFX robot.
   * @param firstNames the first names.
   */
  private static void select(final FxRobot robot, final String... firstNames) {
    final TableView<Person> table = persons(robot);
    robot.interact(() -> {
      table.getSelectionModel().clearSelection();
      for (final String first : firstNames) {
        table.getSelectionModel().select(table.getItems().stream().filter(person -> person.firstName().equals(first))
                                              .findFirst().orElseThrow());
      }
    });
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Presses Delete on the table of the persons.
   *
   * @param robot the TestFX robot.
   */
  private static void deleteKey(final FxRobot robot) {
    final TableView<Person> table = persons(robot);
    robot.interact(() -> Event.fireEvent(table, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE, false,
                                                             false, false, false)));
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Reads the saved directory.
   *
   * @return the directory of the data folder.
   *
   * @throws Exception if it cannot be read.
   */
  private Directory saved() throws Exception {
    return new DirectoryStore(folder).load();
  }

  /**
   * F14: the persons are searched and filtered by edition; a person shows its participations with its team mate,
   * on as many lines as needed; an invalid value is explained; a person is corrected, marked not to be contacted
   * with its contact details erased, erased, and deleted.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F14: search, filter, form, contact status and deletion of the persons")
  @SuppressWarnings("unchecked")
  void personsTest(final FxRobot robot) throws Exception {
    showDirectory(robot);
    final String all = text(robot, "count");
    type(robot, "search", "FONT");
    final int searched = persons(robot).getItems().size();
    type(robot, "search", "");
    final ChoiceBox<Participation> filter = robot.lookup("#editionFilter").queryAs(ChoiceBox.class);
    robot.interact(() -> filter.setValue(filter.getItems().get(1)));
    final int filtered = persons(robot).getItems().size();
    final String edition = filter.getConverter().toString(filter.getValue());
    select(robot, "Anne");
    final List<String> participations =
        List.copyOf(robot.lookup("#participations").queryAs(ListView.class).getItems());
    final List<String> wrapped = robot.lookup("#participations .list-cell").queryAllAs(ListCell.class).stream()
                                      .filter(cell -> cell.isWrapText() && cell.getText() != null)
                                      .map(ListCell::getText).toList();
    final String email = text(robot, "emailField");
    type(robot, "emailField", "anne");
    press(robot, "saveButton");
    final String invalid = text(robot, "problem");
    type(robot, "emailField", "anne.fontaine@example.ch");
    type(robot, "notesField", "Capitaine");
    press(robot, "saveButton");
    await(() -> saved().get(anne.id()).notes().equals("Capitaine"));
    robot.interact(() -> filter.setValue(null));
    select(robot, "Léo");
    final List<String> alone =
        List.copyOf(robot.lookup("#participations").queryAs(ListView.class).getItems());
    type(robot, "notesField", "Ancien");
    press(robot, "saveButton");
    await(() -> saved().search("Léo").getFirst().notes().equals("Ancien"));
    select(robot, "Bruno");
    robot.interact(() -> robot.lookup("#statusBox").queryAs(ChoiceBox.class).setValue(ContactStatus.DO_NOT_CONTACT));
    natives.answer(false);
    press(robot, "saveButton");
    await(() -> saved().get(bruno.id()).status() == ContactStatus.DO_NOT_CONTACT);
    final boolean phoneKept = saved().get(bruno.id()).phone() != null;
    select(robot, "Anne");
    robot.interact(() -> robot.lookup("#statusBox").queryAs(ChoiceBox.class).setValue(ContactStatus.DO_NOT_CONTACT));
    natives.answer(true);
    press(robot, "saveButton");
    await(() -> saved().get(anne.id()).status() == ContactStatus.DO_NOT_CONTACT);
    select(robot, "Bruno");
    natives.answer(false);
    press(robot, "eraseButton");
    natives.answer(true);
    press(robot, "eraseButton");
    await(() -> saved().get(bruno.id()).phone() == null);
    select(robot, "Zoé");
    robot.interact(() -> robot.lookup("#statusBox").queryAs(ChoiceBox.class).setValue(ContactStatus.DO_NOT_CONTACT));
    press(robot, "saveButton");
    await(() -> saved().search("Zoé").getFirst().status() == ContactStatus.DO_NOT_CONTACT);
    select(robot, "Zoé");
    natives.answer(false);
    press(robot, "deleteButton");
    natives.answer(true);
    press(robot, "deleteButton");
    await(() -> saved().persons().size() == PERSONS - 1);
    final String deleted = text(robot, "notice");
    final ToggleButton editions = robot.lookup("#editionsNav").queryAs(ToggleButton.class);
    robot.interact(editions::fire);
    await(() -> shown(robot, "openButton"));
    assertAll(
        () -> assertEquals("4 personnes", all),
        () -> assertEquals(1, searched),
        () -> assertEquals(2, filtered),
        () -> assertEquals("Match 2025 (20 nov. 2025)", edition),
        () -> assertEquals(List.of("Match 2025 (20 nov. 2025), avec Bruno Rochat"), participations),
        () -> assertEquals(participations, wrapped),
        () -> assertEquals(List.of("Match 2024 (20 nov. 2024)"), alone),
        () -> assertTrue(phoneKept),
        () -> assertEquals("anne@example.ch", email),
        () -> assertEquals("L’adresse e-mail n’est pas valable.", invalid),
        () -> assertNull(saved().get(anne.id()).email()),
        () -> assertEquals(UiSupport.TODAY, saved().get(anne.id()).statusDate()),
        () -> assertEquals("Zoé a été supprimé de l’annuaire.", deleted),
        () -> assertTrue(shown(robot, "openButton")));
  }

  /**
   * F14: a new person that may duplicate another one is created only after confirmation; two persons are merged.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F14: new persons, duplicates confirmed, merge of two persons")
  void duplicatesTest(final FxRobot robot) throws Exception {
    showDirectory(robot);
    select(robot, "Anne");
    press(robot, "newButton");
    final String cleared = text(robot, "firstField");
    type(robot, "firstField", "Hugo");
    type(robot, "lastField", "Meyer");
    press(robot, "saveButton");
    await(() -> saved().persons().size() == PERSONS + 1);
    press(robot, "newButton");
    type(robot, "firstField", "anne");
    type(robot, "lastField", "FONTAINE");
    natives.answer(false);
    press(robot, "saveButton");
    WaitForAsyncUtils.waitForFxEvents();
    final int refused = saved().persons().size();
    natives.answer(true);
    press(robot, "saveButton");
    await(() -> saved().persons().size() == PERSONS + 2);
    select(robot, "Anne");
    final boolean single = disabled(robot, "mergeButton");
    select(robot, "Anne", "anne");
    final boolean pair = disabled(robot, "mergeButton");
    final int asked = natives.questions().size();
    deleteKey(robot);
    final int afterPair = natives.questions().size();
    select(robot, "Bruno");
    natives.answer(false);
    deleteKey(robot);
    final int afterOne = natives.questions().size();
    select(robot, "Anne", "anne");
    natives.answer(false);
    press(robot, "mergeButton");
    natives.answer(true);
    press(robot, "mergeButton");
    await(() -> saved().persons().size() == PERSONS + 1);
    assertAll(
        () -> assertEquals("", cleared),
        () -> assertEquals(PERSONS + 1, refused),
        () -> assertTrue(single),
        () -> assertFalse(pair),
        () -> assertEquals(asked, afterPair),
        () -> assertEquals(asked + 1, afterOne),
        () -> assertEquals(1, saved().get(anne.id()).participations().size()));
  }

  /**
   * Q16: the e-mail addresses and phone numbers of the selection, or of all the persons shown, are copied without
   * the persons not to be contacted; the directory is exported as CSV with every value.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Q16: contact details copied without the persons not to be contacted; CSV export")
  void contactTest(final FxRobot robot) throws Exception {
    showDirectory(robot);
    press(robot, "emailsButton");
    final String emails = clipboard(robot);
    final String copied = text(robot, "notice");
    select(robot, "Bruno");
    press(robot, "phonesButton");
    final String phones = clipboard(robot);
    final Path export = other.resolve("annuaire.csv");
    natives.choose(export.toFile());
    press(robot, "exportButton");
    await(() -> Files.exists(export));
    final String csv = Files.readString(export, StandardCharsets.UTF_8);
    assertAll(
        () -> assertEquals("anne@example.ch", emails),
        () -> assertEquals("+41790000002", phones),
        () -> assertEquals("1 adresses e-mail copiées.", copied),
        () -> assertTrue(csv.startsWith("﻿Prénom;Nom;E-mail;Téléphone;Remarques;Statut;Statut depuis;Éditions"),
                         csv),
        () -> assertTrue(csv.contains("Léo;Berger;leo@example.ch;;;DO_NOT_CONTACT;2026-11-20;"), csv));
  }

  /**
   * Returns the text of the clipboard.
   *
   * @param robot the TestFX robot.
   *
   * @return the text.
   */
  private static String clipboard(final FxRobot robot) {
    final String[] text = new String[1];
    robot.interact(() -> text[0] = Clipboard.getSystemClipboard().getString());
    return text[0];
  }

  /**
   * Q17: rows pasted from a spreadsheet are previewed with their errors and duplicates, mapped by the user, and only
   * the valid rows are imported, the duplicates when asked; a CSV file is read into the preview; an import can be
   * cancelled.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Q17: import with preview, mapping, errors and duplicates")
  @SuppressWarnings("unchecked")
  void importTest(final FxRobot robot) throws Exception {
    showDirectory(robot);
    press(robot, "importButton");
    await(() -> shown(robot, "separatorBox"));
    type(robot, "text", """
        Prénom\tNom\tMail\tNatel
        Ivan\tNouveau\tivan@example.ch\t079 000 00 09
        Anne\tFontaine\t\t
        Jean\tFaux\tfaux\t
        """);
    final char separator = ((ChoiceBox<Character>) robot.lookup("#separatorBox").query()).getValue();
    final String summary = text(robot, "summary");
    final int rows = robot.lookup("#table").queryAs(TableView.class).getItems().size();
    final ChoiceBox<Integer> phone = robot.lookup("#phoneBox").queryAs(ChoiceBox.class);
    final Integer phoneColumn = phone.getValue();
    robot.interact(() -> robot.lookup("#headerBox").queryAs(CheckBox.class).setSelected(false));
    final String noHeader = text(robot, "summary");
    robot.interact(() -> robot.lookup("#headerBox").queryAs(CheckBox.class).setSelected(true));
    final ChoiceBox<Integer> first = robot.lookup("#firstBox").queryAs(ChoiceBox.class);
    robot.interact(() -> first.setValue(ColumnMapping.NONE));
    final String noFirst = text(robot, "summary");
    final boolean blocked = disabled(robot, "importButton");
    robot.interact(() -> first.setValue(0));
    robot.interact(() -> ((ChoiceBox<Character>) robot.lookup("#separatorBox").query()).setValue(Csv.COMMA));
    robot.interact(() -> ((ChoiceBox<Character>) robot.lookup("#separatorBox").query()).setValue(Csv.TAB));
    robot.interact(() -> robot.lookup("#duplicatesBox").queryAs(CheckBox.class).setSelected(true));
    final String withDuplicates = text(robot, "summary");
    robot.interact(() -> robot.lookup("#duplicatesBox").queryAs(CheckBox.class).setSelected(false));
    press(robot, "importButton");
    await(() -> saved().persons().size() == PERSONS + 1 && shown(robot, "search"));
    final String imported = text(robot, "notice");
    press(robot, "importButton");
    await(() -> shown(robot, "fileButton"));
    final Path file = Files.writeString(other.resolve("personnes.csv"), "Prénom;Nom\nKarin;Neu\n",
                                        StandardCharsets.UTF_8);
    natives.choose(file.toFile());
    press(robot, "fileButton");
    await(() -> text(robot, "text").contains("Karin"));
    final String fromFile = text(robot, "summary");
    press(robot, "cancelButton");
    await(() -> shown(robot, "search"));
    assertAll(
        () -> assertEquals(Csv.TAB, separator),
        () -> assertEquals("1 personnes à importer, 1 lignes non valables, 1 doublons possibles", summary),
        () -> assertEquals(PERSONS - 1, rows),
        () -> assertEquals(PHONE_COLUMN, phoneColumn),
        () -> assertEquals("1 personnes à importer, 2 lignes non valables, 1 doublons possibles", noHeader),
        () -> assertEquals("Choisissez la colonne des prénoms.", noFirst),
        () -> assertTrue(blocked),
        () -> assertTrue(withDuplicates.startsWith("2 personnes"), withDuplicates),
        () -> assertEquals("1 personnes importées.", imported),
        () -> assertTrue(fromFile.startsWith("1 personnes"), fromFile),
        () -> assertEquals("+41790000009", saved().search("Ivan").getFirst().phone().value()));
  }

  /**
   * F14, keyboard: Shortcut+N starts a person and Shortcut+S saves it; a new person started at once, before the save
   * is done, keeps its form, which Shortcut+S saves in turn; the saved person is then selected. Without a form to
   * save (list of the editions), the item is disabled.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F14: persons are typed one after the other with Shortcut+N and Shortcut+S")
  void keyboardTest(final FxRobot robot) throws Exception {
    await(() -> shown(robot, "openButton"));
    final boolean listDisabled = menuItem(robot, "saveItem").isDisable();
    showDirectory(robot);
    final boolean directoryEnabled = !menuItem(robot, "saveItem").isDisable();
    menu(robot, "newItem");
    type(robot, "firstField", "Hugo");
    type(robot, "lastField", "Vuilleumier");
    final MenuItem save = menuItem(robot, "saveItem");
    final MenuItem create = menuItem(robot, "newItem");
    robot.interact(() -> {
      save.fire();
      create.fire();
    });
    type(robot, "firstField", "Inès");
    type(robot, "lastField", "Jaquier");
    await(() -> saved().persons().stream().anyMatch(person -> person.firstName().equals("Hugo")));
    final String kept = text(robot, "firstField") + " " + text(robot, "lastField");
    final boolean nothingSelected = persons(robot).getSelectionModel().isEmpty();
    menu(robot, "saveItem");
    await(() -> saved().persons().stream().anyMatch(person -> person.firstName().equals("Inès")));
    await(() -> !persons(robot).getSelectionModel().isEmpty());
    assertAll(
        () -> assertTrue(listDisabled),
        () -> assertTrue(directoryEnabled),
        () -> assertEquals("Inès Jaquier", kept),
        () -> assertTrue(nothingSelected),
        () -> assertEquals("Inès", persons(robot).getSelectionModel().getSelectedItem().firstName()),
        () -> assertEquals("Inès Jaquier", text(robot, "personTitle")));
  }

  /**
   * DESIGN.md §8: the tooltip of a tool appears when the pointer rests on it, and stays as long as the pointer does. On
   * a slow runner the move of the pointer may be lost: the pointer leaves and comes back, a few times. The duration is
   * read on the JavaFX thread once its events are processed: the tooltip may be showing before its style sheet sets it.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The tooltip of a tool stays while the pointer rests on it")
  void tooltipTest(final FxRobot robot) throws Exception {
    showDirectory(robot);
    final Button export = robot.lookup("#exportButton").queryButton();
    boolean shown = false;
    for (int attempt = 0; attempt < HOVER_ATTEMPTS && !shown; attempt++) {
      robot.moveTo(export);
      robot.moveBy(1, 1);
      try {
        WaitForAsyncUtils.waitFor(HOVER_SECONDS, TimeUnit.SECONDS, () -> export.getTooltip().isShowing());
        shown = true;
      } catch (TimeoutException _) {
        robot.moveTo(robot.lookup("#table").queryAs(TableView.class));
      }
    }
    final boolean appeared = shown;
    WaitForAsyncUtils.waitForFxEvents();
    final Duration[] duration = new Duration[1];
    robot.interact(() -> duration[0] = export.getTooltip().getShowDuration());
    assertAll(
        () -> assertTrue(appeared),
        () -> assertEquals(Duration.INDEFINITE, duration[0]));
  }

  /**
   * A directory that could not be read is shown empty and cannot be changed, not even by Shortcut+S.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A damaged directory is shown empty and cannot be changed")
  void unavailableTest(final FxRobot robot) throws Exception {
    Files.writeString(other.resolve("directory.jassdir"), "damaged");
    final Stage[] stage = new Stage[1];
    robot.interact(() -> {
      stage[0] = new Stage();
      new JassApplication(new AppContext(other, Languages.FRENCH, natives.natives(), UiSupport.CLOCK,
                                         Solving::of, false, FakeUpdater.NONE)).start(stage[0]);
    });
    WaitForAsyncUtils.waitForFxEvents();
    final FxRobot window = new FxRobot();
    final Parent root = stage[0].getScene().getRoot();
    final MenuItem item = window.from(root).lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
                                .flatMap(menu -> menu.getItems().stream())
                                .filter(candidate -> "directoryItem".equals(candidate.getId()))
                                .findFirst().orElseThrow();
    robot.interact(item::fire);
    await(() -> window.from(stage[0].getScene().getRoot()).lookup("#unavailable").tryQuery().isPresent());
    final boolean visible = window.from(stage[0].getScene().getRoot()).lookup("#unavailable").query().isVisible();
    final boolean disabledContent = window.from(stage[0].getScene().getRoot()).lookup("#body").query()
                                          .isDisabled()
                                    && window.from(stage[0].getScene().getRoot()).lookup("#toolbar").query()
                                             .isDisabled();
    final boolean saveDisabled = window.from(root).lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
                                       .flatMap(menu -> menu.getItems().stream())
                                       .filter(candidate -> "saveItem".equals(candidate.getId()))
                                       .findFirst().orElseThrow().isDisable();
    robot.interact(stage[0]::close);
    assertAll(
        () -> assertTrue(visible),
        () -> assertTrue(disabledContent),
        () -> assertTrue(saveDisabled));
  }

}
