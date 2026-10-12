package ch.varani.jass.app;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.EntryType;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Money;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.EditionStore;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.disabled;
import static ch.varani.jass.app.UiSupport.menu;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.text;
import static ch.varani.jass.app.UiSupport.type;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the accounting (phase 12, F8): summary, registration fee and players who paid, entries added,
 * corrected and deleted, supporting documents added, opened and removed; in French, on a temporary data folder with
 * fictitious teams ({@link ScoreFixtures}).
 */
@ExtendWith(ApplicationExtension.class)
final class AccountingScreenTest {

  /** The team edition with a fee and two entries. */
  private static final String TEAMS = "Match compta";
  /** The individual edition. */
  private static final String INDIVIDUAL = "Compta individuelle";
  /** The edition without registration nor entry. */
  private static final String EMPTY = "Compta vide";
  /** The registration fee of the team edition. */
  private static final Money FEE = Money.of("30");
  /** The amount of the expense of the team edition. */
  private static final Money CARDS = Money.of("45.5");
  /** The amount of the income of the team edition. */
  private static final Money SPONSOR = Money.of("200");
  /** The players of the individual edition. */
  private static final int PLAYERS = 2;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** Another temporary folder, for the documents. */
  @TempDir
  private Path other;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();
  /** The texts of the tests, in French. */
  private final Messages messages = new Messages(Languages.FRENCH);

  /**
   * Fills the data folder with the editions of the scenarios, then starts the application in French.
   *
   * @param stage the stage provided by TestFX.
   *
   * @throws Exception never.
   */
  @Start
  void start(final Stage stage) throws Exception {
    final EditionStore store = new EditionStore(folder);
    final Edition teams = ScoreFixtures.registered(TEAMS);
    store.create(teams.withLedger(Ledger.EMPTY.withFee(FEE)
        .add(LedgerEntry.create(UiSupport.TODAY, EntryType.EXPENSE, "Matériel", "Cartes", CARDS))
        .add(LedgerEntry.create(UiSupport.TODAY.minusDays(1), EntryType.INCOME, "Sponsors", "", SPONSOR))));
    store.create(Edition.create(INDIVIDUAL, UiSupport.TODAY, TournamentMode.INDIVIDUAL)
        .withRegistrations(new IndividualRegistrations(List.of(new Player("Gilles", "Huguenin"),
                                                               new Player("Iris", "Jaquet")))));
    store.create(Edition.create(EMPTY, UiSupport.TODAY, TournamentMode.TEAM));
    new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(), UiSupport.CLOCK,
                                       Solving::of, false, FakeUpdater.NONE)).start(stage);
  }

  /**
   * Opens the accounting of an edition.
   *
   * @param robot the TestFX robot.
   * @param name the name of the edition.
   *
   * @throws Exception if the edition is not opened in time.
   */
  private static void open(final FxRobot robot, final String name) throws Exception {
    ScoreFixtures.open(robot, name, "accountingNav");
    await(() -> shown(robot, "fee"));
  }

  /**
   * Returns the texts of the summary, line by line.
   *
   * @param robot the TestFX robot.
   *
   * @return the texts of its cells.
   */
  private static List<String> summary(final FxRobot robot) {
    return robot.lookup("#summary").queryAs(GridPane.class).getChildren().stream()
                .map(node -> ((Label) node).getText().replace(' ', ' ')).toList();
  }

  /**
   * Returns the table of the entries.
   *
   * @param robot the TestFX robot.
   *
   * @return the table.
   */
  @SuppressWarnings("unchecked")
  private static TableView<LedgerEntry> entries(final FxRobot robot) {
    return robot.lookup("#entries").queryAs(TableView.class);
  }

  /**
   * Returns the box of a player in the table of the registration fees.
   *
   * @param robot the TestFX robot.
   * @param index the index of the player.
   *
   * @return the box.
   */
  private static CheckBox paid(final FxRobot robot, final int index) {
    return robot.lookup("#payments").lookup(".check-box").queryAllAs(CheckBox.class).stream()
                .sorted((first, second) -> Double.compare(first.localToScene(0, 0).getY(),
                                                          second.localToScene(0, 0).getY()))
                .toList().get(index);
  }

  /**
   * Presses a key in a node.
   *
   * @param robot the TestFX robot.
   * @param node the node.
   * @param code the key.
   */
  private static void key(final FxRobot robot, final Node node, final KeyCode code) {
    robot.interact(() -> node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false,
                                                     false)));
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * F8: the summary shows the fees paid, the incomes and expenses per category, the totals, the balance and the fees
   * outstanding; a box records that a player paid, in team and in individual mode.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F8: the summary follows the fees paid and the entries")
  void summaryTest(final FxRobot robot) throws Exception {
    open(robot, TEAMS);
    final List<String> before = summary(robot);
    final String fee = text(robot, "fee");
    final String status = text(robot, "feeStatus");
    final CheckBox second = paid(robot, 1);
    robot.interact(second::fire);
    await(() -> ScoreFixtures.stored(folder, TEAMS).teamRegistrations().get(1).players().get(1).paid());
    final List<String> after = summary(robot);
    robot.interact(() -> paid(robot, 1).fire());
    await(() -> !ScoreFixtures.stored(folder, TEAMS).teamRegistrations().get(1).players().get(1).paid());
    menu(robot, "listItem");
    open(robot, INDIVIDUAL);
    robot.interact(() -> paid(robot, 0).fire());
    await(() -> ScoreFixtures.stored(folder, INDIVIDUAL).individualRegistrations().get(1).paid());
    assertAll(
        () -> assertEquals(List.of("Finances d’inscription payées (joueurs : 0 sur 8)", "0,00",
                                   "Sponsors (écritures : 1)", "200,00", "Total des recettes", "200,00",
                                   "Matériel (écritures : 1)", "45,50", "Total des dépenses", "45,50",
                                   "Solde", "154,50", "Finances d’inscription à encaisser (joueurs : 8)", "240,00"),
                           before),
        () -> assertEquals("30,00", fee),
        () -> assertEquals("Joueurs ayant payé : 0 sur 8", status),
        () -> assertEquals("30,00", after.get(1)),
        () -> assertEquals("184,50", after.get(after.indexOf("Solde") + 1)),
        () -> assertEquals("Joueurs ayant payé : 1 sur 2", text(robot, "feeStatus")),
        () -> assertEquals(PLAYERS, robot.lookup("#payments").queryAs(TableView.class).getItems().size()));
  }

  /**
   * F8: the registration fee is typed with a comma or a dot, saved when it changes, and rejected when it is not an
   * amount; a negative balance is shown in red.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F8: the registration fee is saved when it changes")
  void feeTest(final FxRobot robot) throws Exception {
    open(robot, TEAMS);
    final TextField fee = robot.lookup("#fee").queryAs(TextField.class);
    type(robot, "fee", "trente");
    robot.interact(() -> fee.fireEvent(new ActionEvent()));
    final String rejected = text(robot, "feeProblem");
    robot.interact(() -> {
      fee.requestFocus();
      fee.setText("1 000,50");
      robot.lookup("#description").query().requestFocus();
    });
    await(() -> ScoreFixtures.stored(folder, TEAMS).ledger().fee().equals(Money.of("1000.5")));
    type(robot, "fee", "1’000.50");
    robot.interact(() -> fee.fireEvent(new ActionEvent()));
    menu(robot, "listItem");
    open(robot, EMPTY);
    assertAll(
        () -> assertEquals(messages.get("error.validation.INVALID_AMOUNT"), rejected),
        () -> assertEquals(Money.of("1000.5"), ScoreFixtures.stored(folder, TEAMS).ledger().fee()),
        () -> assertEquals("Aucun joueur inscrit pour l’instant.",
                           ((Label) robot.lookup("#payments").lookup(".empty-state .label").query()).getText()));
  }

  /**
   * F8: an entry is added, corrected and deleted after confirmation; a missing date or amount is explained; the
   * form follows the selected entry; Enter moves to the form, Delete deletes; Shortcut+N starts a new entry.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F8: an entry is added, corrected and deleted")
  void entryTest(final FxRobot robot) throws Exception {
    open(robot, EMPTY);
    final boolean placeholder = robot.lookup("#entries").lookup(".empty-state .button").tryQuery().isPresent();
    robot.interact(() -> robot.lookup("#entries").lookup(".empty-state .button").queryButton().fire());
    final String newTitle = text(robot, "entryTitle");
    final boolean documentsLocked = disabled(robot, "documents");
    press(robot, "saveButton");
    final String noAmount = text(robot, "problem");
    final DatePicker date = robot.lookup("#datePicker").queryAs(DatePicker.class);
    robot.interact(() -> date.setValue(null));
    press(robot, "saveButton");
    final String noDate = text(robot, "problem");
    robot.interact(() -> date.setValue(UiSupport.TODAY));
    @SuppressWarnings("unchecked")
    final ComboBox<String> categories = robot.lookup("#categoryBox").queryAs(ComboBox.class);
    robot.interact(() -> categories.getEditor().setText("Buvette"));
    type(robot, "description", "Boissons");
    type(robot, "amount", "120,00");
    press(robot, "saveButton");
    await(() -> ScoreFixtures.stored(folder, EMPTY).ledger().entries().size() == 1);
    final String editTitle = text(robot, "entryTitle");
    final String notice = text(robot, "notice");
    final List<String> negative = summary(robot);
    final boolean red = robot.lookup("#summary").queryAs(GridPane.class).getChildren().stream()
                             .anyMatch(node -> node.getStyleClass().contains("error"));
    type(robot, "amount", "150");
    press(robot, "saveButton");
    await(() -> ScoreFixtures.stored(folder, EMPTY).ledger().entries().getFirst().amount()
                                 .equals(Money.of("150")));
    robot.interact(() -> entries(robot).getSelectionModel().select(0));
    key(robot, entries(robot), KeyCode.ENTER);
    final boolean dateFocused = date.getScene().getFocusOwner() == date;
    natives.answer(false);
    key(robot, entries(robot), KeyCode.DELETE);
    final boolean kept = entries(robot).getItems().size() == 1;
    natives.answer(true);
    press(robot, "deleteButton");
    await(() -> ScoreFixtures.stored(folder, EMPTY).ledger().entries().isEmpty());
    assertAll(
        () -> assertTrue(placeholder),
        () -> assertEquals("Nouvelle écriture", newTitle),
        () -> assertTrue(documentsLocked),
        () -> assertEquals(messages.get("error.validation.INVALID_AMOUNT"), noAmount),
        () -> assertEquals("Indiquez la date de l’écriture.", noDate),
        () -> assertEquals("Écriture", editTitle),
        () -> assertEquals("L’écriture a été enregistrée.", notice),
        () -> assertEquals("−120,00", negative.get(negative.indexOf("Solde") + 1).replace('-', '−')),
        () -> assertTrue(red),
        () -> assertTrue(dateFocused),
        () -> assertTrue(kept),
        () -> assertEquals("L’écriture « Boissons » a été supprimée.", text(robot, "notice")),
        () -> assertEquals("Nouvelle écriture", text(robot, "entryTitle")),
        () -> assertEquals(List.of(), entries(robot).getItems()));
  }

  /**
   * F8: a supporting document is added to a saved entry, opened through a copy, and removed after confirmation; a
   * file that is not a PDF, PNG or JPEG document is rejected; a drop without files adds nothing; then Shortcut+N
   * starts a new entry and Shortcut+S saves it.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F8: a supporting document is added, opened and removed; Shortcut+N and Shortcut+S add an entry")
  void documentsTest(final FxRobot robot) throws Exception {
    final Path invoice = other.resolve("facture.pdf");
    Files.write(invoice, "%PDF-1.7 facture fictive".getBytes(StandardCharsets.US_ASCII));
    final Path text = other.resolve("notes.txt");
    Files.writeString(text, "pas un justificatif");
    open(robot, TEAMS);
    robot.interact(() -> entries(robot).getSelectionModel().select(1));
    final String category = robot.lookup("#categoryBox").queryAs(ComboBox.class).getEditor().getText();
    final Node documents = robot.lookup("#documents").query();
    robot.interact(() -> {
      documents.fireEvent(new DragEvent(DragEvent.DRAG_OVER, null, 0, 0, 0, 0, null, null, null, null));
      documents.fireEvent(new DragEvent(DragEvent.DRAG_DROPPED, null, 0, 0, 0, 0, null, null, null, null));
    });
    natives.choose(text.toFile());
    press(robot, "addDocumentButton");
    await(() -> !natives.notices().isEmpty());
    final boolean enabled = !robot.lookup("#fee").query().getScene().getRoot().isDisabled();
    natives.choose(invoice.toFile());
    press(robot, "addDocumentButton");
    await(() -> ScoreFixtures.stored(folder, TEAMS).ledger().attachments().size() == 1);
    @SuppressWarnings("unchecked")
    final ListView<Object> list = robot.lookup("#attachments").queryAs(ListView.class);
    final String line = String.valueOf(robot.lookup("#attachments").lookup(".list-cell").queryAs(ListCell.class)
                                            .getText());
    robot.interact(() -> list.getSelectionModel().select(0));
    press(robot, "openDocumentButton");
    await(() -> !natives.opened().isEmpty());
    natives.answer(false);
    press(robot, "removeDocumentButton");
    final int kept = ScoreFixtures.stored(folder, TEAMS).ledger().attachments().size();
    natives.answer(true);
    robot.interact(() -> list.getSelectionModel().select(0));
    press(robot, "removeDocumentButton");
    await(() -> ScoreFixtures.stored(folder, TEAMS).ledger().attachments().isEmpty());
    final String removed = text(robot, "notice");
    robot.interact(() -> entries(robot).getSelectionModel().select(0));
    natives.answer(true);
    press(robot, "deleteButton");
    await(() -> ScoreFixtures.stored(folder, TEAMS).ledger().entries().size() == 1);
    final String deleted = text(robot, "notice");
    menu(robot, "newItem");
    final String fresh = text(robot, "entryTitle");
    @SuppressWarnings("unchecked")
    final ComboBox<String> categories = robot.lookup("#categoryBox").queryAs(ComboBox.class);
    robot.interact(() -> categories.getEditor().setText("Loto"));
    type(robot, "amount", "12.50");
    menu(robot, "saveItem");
    await(() -> ScoreFixtures.stored(folder, TEAMS).ledger().entries().stream()
                             .anyMatch(entry -> entry.category().equals("Loto")
                                                && entry.amount().equals(Money.of("12.5"))));
    assertAll(
        () -> assertEquals("Nouvelle écriture", fresh),
        () -> assertEquals("Matériel", category),
        () -> assertEquals(messages.get("error.format.UNSUPPORTED_ATTACHMENT"),
                           natives.notices().getFirst().getContentText()),
        () -> assertTrue(enabled),
        () -> assertTrue(line.startsWith("facture.pdf · PDF"), line),
        () -> assertTrue(natives.opened().getFirst().endsWith("/facture.pdf"), natives.opened().getFirst()),
        () -> assertEquals(1, kept),
        () -> assertEquals(List.of(), list.getItems()),
        () -> assertEquals("La pièce « facture.pdf » a été retirée.", removed),
        () -> assertEquals("L’écriture « Sponsors » a été supprimée.", deleted),
        () -> assertEquals("L’écriture a été enregistrée.", text(robot, "notice")));
  }

  /**
   * F8: an amount is read with a dot or a comma, spaces and apostrophes ignored.
   */
  @Test
  @DisplayName("F8: an amount is typed with a dot or a comma")
  void amountTest() {
    assertAll(
        () -> assertEquals(new Money(new BigDecimal("1234.5")), AccountingController.amount("1 234,50")),
        () -> assertEquals(new Money(new BigDecimal("1234.5")), AccountingController.amount("1’234.50")),
        () -> assertEquals(new Money(new BigDecimal("1234.5")), AccountingController.amount("1'234.5")),
        () -> assertThrows(DomainException.class, () -> AccountingController.amount("12.345")));
  }

}
