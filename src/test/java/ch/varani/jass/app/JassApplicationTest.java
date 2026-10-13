package ch.varani.jass.app;

import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.EditionStore;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.Pane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the user interface of phase 9 ({@link JassApplication}, {@link MainController} and the screens), run
 * headless with TestFX and Monocle, in French, on a temporary data folder holding two fictitious editions and an
 * unreadable file; the windows of the system are replaced ({@link FakeNatives}).
 */
@ExtendWith(ApplicationExtension.class)
final class JassApplicationTest {

  /** The longest wait for the background operations, in seconds. */
  private static final long WAIT = 10;
  /** The date of today in the tests. */
  private static final LocalDate TODAY = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** The number of games set in the form. */
  private static final int FIVE = 5;
  /** The number of editions once one has been imported. */
  private static final int IMPORTED = 3;
  /** The size of the generated logo, in pixels. */
  private static final int LOGO = 16;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** The other temporary folder, for the exported files. */
  @TempDir
  private Path other;
  /** The window. */
  private Stage stage;
  /** The application. */
  private JassApplication application;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();
  /** The identifier of the edition of 2025. */
  private UUID older;

  /**
   * Fills the data folder with two editions and an unreadable file, then starts the application.
   *
   * @param primaryStage the stage provided by TestFX.
   *
   * @throws Exception never.
   */
  @Start
  void start(final Stage primaryStage) throws Exception {
    stage = primaryStage;
    final EditionStore store = new EditionStore(folder);
    older = store.create(Edition.create("Match 2025", TODAY.minusYears(1), TournamentMode.TEAM));
    store.create(Edition.create("Match 2026", TODAY, TournamentMode.INDIVIDUAL));
    Files.writeString(folder.resolve("editions").resolve(UUID.randomUUID() + ".jass"), "damaged");
    application = new JassApplication(context(folder));
    application.start(stage);
  }

  /**
   * Returns the environment of the tests: a data folder, French, the replaced windows, today fixed.
   *
   * @param data the data folder.
   *
   * @return the environment.
   */
  private AppContext context(final Path data) {
    return new AppContext(data, Languages.FRENCH, natives.natives(),
                          Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()),
                          Solving::of, false, FakeUpdater.NONE);
  }

  /**
   * Waits until a condition holds, the background operations and the events being processed.
   *
   * @param condition the condition.
   *
   * @throws Exception if the condition does not hold in time.
   */
  private static void await(final Callable<Boolean> condition) throws Exception {
    WaitForAsyncUtils.waitFor(WAIT, TimeUnit.SECONDS, condition);
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Returns the table of the current screen.
   *
   * @param robot the TestFX robot.
   *
   * @return the table.
   */
  @SuppressWarnings("unchecked")
  private static TableView<EditionHeader> table(final FxRobot robot) {
    return robot.lookup("#table").queryAs(TableView.class);
  }

  /**
   * Fires a button of the current screen.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the button.
   */
  private static void press(final FxRobot robot, final String id) {
    final Button button = robot.lookup("#" + id).queryButton();
    robot.interact(button::fire);
  }

  /**
   * Fires an item of the menu.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the item.
   */
  private static void menu(final FxRobot robot, final String id) {
    robot.interact(() -> item(robot, id).fire());
  }

  /**
   * Returns an item of the menu.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the item.
   *
   * @return the item.
   */
  private static MenuItem item(final FxRobot robot, final String id) {
    return robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
                .flatMap(menu -> menu.getItems().stream())
                .filter(item -> id.equals(item.getId())).findFirst().orElseThrow();
  }

  /**
   * Fires an item of the side navigation.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the item.
   */
  private static void nav(final FxRobot robot, final String id) {
    final ToggleButton item = robot.lookup("#" + id).queryAs(ToggleButton.class);
    robot.interact(item::fire);
  }

  /**
   * Selects the row of an edition in the table of the current screen.
   *
   * @param robot the TestFX robot.
   * @param name the name of the edition.
   */
  private static void select(final FxRobot robot, final String name) {
    final TableView<EditionHeader> table = table(robot);
    robot.interact(() -> table.getSelectionModel().select(table.getItems().stream()
        .filter(header -> header.name().equals(name)).findFirst().orElseThrow()));
  }

  /**
   * Returns the names of the editions of the table of the current screen.
   *
   * @param robot the TestFX robot.
   *
   * @return the names, in the order of the table.
   */
  private static List<String> names(final FxRobot robot) {
    return robot.lookup("#table").tryQuery().map(node -> table(robot).getItems().stream()
        .map(EditionHeader::name).toList()).orElse(List.of());
  }

  /**
   * Returns the text of a label of the current screen.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the label.
   *
   * @return the text.
   */
  private static String text(final FxRobot robot, final String id) {
    return robot.lookup("#" + id).queryAs(Label.class).getText();
  }

  /**
   * F2: the start screen lists the editions, the most recent first, reports the unreadable file, and disables the
   * actions on a selected edition and on an open edition.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F2: the start screen lists the editions and reports the unreadable files")
  void startTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    assertAll(
        () -> assertEquals("Tournoi de Jass", stage.getTitle()),
        () -> assertEquals(List.of("Match 2026", "Match 2025"), names(robot)),
        () -> assertEquals(List.of("Nom", "Mode", "Inscriptions", "En attente", "Limite maximale", "Date"),
                           table(robot).getColumns().stream().map(TableColumn::getText).toList()),
        () -> assertEquals(List.of("Match 2026", "Individuel", "0", "0", "20", "20 nov. 2026"),
                           table(robot).getColumns().stream().map(column -> String.valueOf(column.getCellData(0)))
                                       .toList()),
        () -> assertEquals("Fichiers d’édition illisibles : 1. S’ils viennent d’une version plus récente de "
                           + "l’application, vérifiez que vous utilisez la dernière version (Aide › Rechercher les "
                           + "mises à jour…).", text(robot, "unreadable")),
        () -> assertTrue(robot.lookup("#openButton").queryButton().isDisabled()),
        () -> assertTrue(item(robot, "exportItem").isDisable()),
        () -> assertTrue(item(robot, "deleteItem").isDisable()));
  }

  /**
   * F2, F4, F15: a new edition is created from the form, which reports what is missing, edits the plan of the deals
   * and creates the edition, opened at once.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F2, F4, F15: a new edition is created from the form and opened")
  @SuppressWarnings("unchecked")
  void newEditionTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    menu(robot, "newItem");
    await(() -> robot.lookup("#nameField").tryQuery().isPresent());
    final String blank = text(robot, "problem");
    final boolean disabled = robot.lookup("#createButton").queryButton().isDisabled();
    robot.interact(() -> robot.lookup("#nameField").queryAs(TextField.class).setText("Match d’hiver"));
    robot.interact(() -> robot.lookup("#datePicker").queryAs(DatePicker.class).setValue(null));
    final String noDate = text(robot, "problem");
    robot.interact(() -> robot.lookup("#datePicker").queryAs(DatePicker.class).setValue(TODAY));
    robot.interact(() -> robot.lookup("#modeBox").queryAs(ChoiceBox.class).setValue(TournamentMode.INDIVIDUAL));
    robot.interact(() -> robot.lookup("#gamesSpinner").queryAs(Spinner.class).increment());
    robot.interact(() -> robot.lookup("#maxSpinner").queryAs(Spinner.class).increment());
    press(robot, "addBlockButton");
    final String longer = text(robot, "summary");
    press(robot, "removeBlockButton");
    press(robot, "regulationButton");
    final ComboBox<DealType> first = robot.lookup("#dealList .combo-box").queryAs(ComboBox.class);
    final Object read = robot.lookup("#modeBox").queryAs(ChoiceBox.class).getConverter().fromString("Équipes");
    final List<ComboBox<DealType>> types = robot.lookup("#dealList .combo-box").queryAllAs(ComboBox.class).stream()
        .map(combo -> (ComboBox<DealType>) combo).toList();
    final boolean heartsRed = types.get(0).getButtonCell().getStyleClass().contains(EditionFormController.RED);
    final boolean clubsRed = types.get(1).getButtonCell().getStyleClass().contains(EditionFormController.RED);
    final boolean diamondsRed = types.get(2).getButtonCell().getStyleClass().contains(EditionFormController.RED);
    robot.interact(() -> types.get(1).show());
    WaitForAsyncUtils.waitForFxEvents();
    robot.interact(() -> types.get(1).hide());
    robot.interact(() -> first.setValue(DealType.SCHIEBER));
    press(robot, "createButton");
    await(() -> robot.lookup("#settings").tryQuery().isPresent());
    assertAll(
        () -> assertEquals("Donnez un nom à l’édition.", blank),
        () -> assertNull(read),
        () -> assertTrue(heartsRed),
        () -> assertFalse(clubsRed),
        () -> assertTrue(diamondsRed),
        () -> assertTrue(disabled),
        () -> assertEquals("Choisissez la date de l’édition.", noDate),
        () -> assertEquals("20 donnes, 5 blocs de 4, 3\u202f140 points par partie", longer),
        () -> assertEquals("Match d’hiver – 20 nov. 2026", text(robot, "title")),
        () -> assertEquals("Individuel, 5 parties, 16 donnes par partie (2\u202f512 points)", text(robot, "settings")),
        () -> assertEquals("Match d’hiver – Tournoi de Jass", stage.getTitle()),
        () -> assertFalse(item(robot, "exportItem").isDisable()),
        () -> assertEquals(FIVE, new EditionStore(folder).list().editions().stream()
            .filter(header -> header.name().equals("Match d’hiver")).findFirst().orElseThrow().games()));
  }

  /**
   * F16: the selected edition is duplicated with its settings; the mode of a duplicate cannot be changed; a form can
   * be cancelled.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F16: an edition is duplicated with its settings")
  void duplicateTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    press(robot, "newButton");
    await(() -> robot.lookup("#cancelButton").tryQuery().isPresent());
    press(robot, "cancelButton");
    await(() -> names(robot).size() == 2);
    select(robot, "Match 2026");
    press(robot, "duplicateButton");
    await(() -> robot.lookup("#nameField").tryQuery().isPresent());
    final String title = text(robot, "title");
    final boolean modeDisabled = robot.lookup("#modeBox").query().isDisabled();
    final LocalDate date = robot.lookup("#datePicker").queryAs(DatePicker.class).getValue();
    robot.interact(() -> robot.lookup("#nameField").queryAs(TextField.class).setText("Match 2027"));
    press(robot, "createButton");
    await(() -> robot.lookup("#settings").tryQuery().isPresent());
    assertAll(
        () -> assertEquals("Duplication d’une édition", title),
        () -> assertTrue(modeDisabled),
        () -> assertEquals(TODAY.plusYears(1), date),
        () -> assertTrue(text(robot, "settings").startsWith("Individuel")));
  }

  /**
   * F2: an edition is opened with its button or by a double click on its row; a single click, another button or an
   * empty row opens nothing.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F2: an edition is opened by its button or a double click")
  @SuppressWarnings("unchecked")
  void openTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    select(robot, "Match 2025");
    press(robot, "openButton");
    await(() -> robot.lookup("#settings").tryQuery().isPresent());
    final String opened = text(robot, "title");
    menu(robot, "listItem");
    await(() -> names(robot).size() == 2);
    final List<TableRow<EditionHeader>> rows = robot.lookup(".table-row-cell").queryAllAs(TableRow.class)
        .stream().map(row -> (TableRow<EditionHeader>) row).toList();
    final TableRow<EditionHeader> filled = rows.stream().filter(row -> !row.isEmpty()).findFirst().orElseThrow();
    final TableRow<EditionHeader> empty = rows.stream().filter(TableRow::isEmpty).findFirst().orElseThrow();
    robot.interact(() -> {
      filled.fireEvent(click(MouseButton.PRIMARY, 1));
      filled.fireEvent(click(MouseButton.SECONDARY, 2));
      empty.fireEvent(click(MouseButton.PRIMARY, 2));
    });
    WaitForAsyncUtils.waitForFxEvents();
    final boolean stillList = robot.lookup("#table").tryQuery().isPresent();
    robot.interact(() -> filled.fireEvent(click(MouseButton.PRIMARY, 2)));
    await(() -> robot.lookup("#settings").tryQuery().isPresent());
    assertAll(
        () -> assertEquals("Match 2025 – 20 nov. 2025", opened),
        () -> assertTrue(stillList),
        () -> assertEquals("Match 2026 – 20 nov. 2026", text(robot, "title")));
  }

  /**
   * Creates a mouse click.
   *
   * @param button the button.
   * @param count the number of clicks.
   *
   * @return the event.
   */
  private static MouseEvent click(final MouseButton button, final int count) {
    return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, button, count, false, false, false, false,
                          button == MouseButton.PRIMARY, false, button == MouseButton.SECONDARY, false, false, true,
                          null);
  }

  /**
   * F3: an edition is moved to the trash after confirmation, restored, deleted again from its menu, and the trash is
   * emptied after confirmation.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F3: deletion to the trash, restoration and emptying after confirmation")
  void trashTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    select(robot, "Match 2025");
    natives.answer(false);
    press(robot, "deleteButton");
    WaitForAsyncUtils.waitForFxEvents();
    final List<String> kept = names(robot);
    natives.answer(true);
    press(robot, "deleteButton");
    await(() -> names(robot).size() == 1);
    nav(robot, "trashNav");
    await(() -> robot.lookup("#restoreButton").tryQuery().isPresent() && names(robot).size() == 1);
    final List<String> trashed = names(robot);
    select(robot, "Match 2025");
    press(robot, "restoreButton");
    await(() -> robot.lookup("#restoreButton").tryQuery().isPresent() && names(robot).isEmpty());
    final boolean emptyDisabled = robot.lookup("#emptyButton").queryButton().isDisabled();
    final String restored = text(robot, "notice");
    nav(robot, "editionsNav");
    await(() -> names(robot).size() == 2);
    select(robot, "Match 2025");
    press(robot, "openButton");
    await(() -> robot.lookup("#settings").tryQuery().isPresent());
    natives.answer(true);
    menu(robot, "deleteItem");
    await(() -> names(robot).size() == 1);
    menu(robot, "trashItem");
    await(() -> robot.lookup("#emptyButton").tryQuery().isPresent() && names(robot).size() == 1);
    natives.answer(false);
    press(robot, "emptyButton");
    WaitForAsyncUtils.waitForFxEvents();
    final List<String> notEmptied = names(robot);
    natives.answer(true);
    press(robot, "emptyButton");
    await(() -> robot.lookup("#emptyButton").tryQuery().isPresent() && names(robot).isEmpty());
    assertAll(
        () -> assertEquals(2, kept.size()),
        () -> assertEquals("« Match 2025 » a été restaurée.", restored),
        () -> assertEquals("La corbeille a été vidée.", text(robot, "notice")),
        () -> assertEquals(List.of("Match 2025"), trashed),
        () -> assertTrue(emptyDisabled),
        () -> assertEquals(List.of("Match 2025"), notEmptied),
        () -> assertEquals(List.of(), new EditionStore(folder).listTrash().editions()),
        () -> assertFalse(Files.exists(folder.resolve("editions").resolve(older + ".jass"))));
  }

  /**
   * F3: the open edition is exported and imported back; the whole data folder is backed up; a cancelled dialog does
   * nothing.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F3: export, import and backup through the dialogs of the system")
  void filesTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    select(robot, "Match 2025");
    press(robot, "openButton");
    await(() -> robot.lookup("#settings").tryQuery().isPresent());
    final File exported = other.resolve("export.jass").toFile();
    natives.choose(exported);
    menu(robot, "exportItem");
    await(() -> text(robot, "notice").equals("L’édition a été exportée."));
    menu(robot, "exportItem");
    natives.choose(exported);
    menu(robot, "importItem");
    await(() -> names(robot).isEmpty() && robot.lookup("#settings").tryQuery().isPresent()
                && new EditionStore(folder).list().editions().size() == IMPORTED);
    final File backup = other.resolve("backup.zip").toFile();
    natives.choose(backup);
    menu(robot, "backupItem");
    await(() -> text(robot, "notice").equals("Tout a été sauvegardé."));
    menu(robot, "importItem");
    menu(robot, "listItem");
    await(() -> names(robot).size() == IMPORTED);
    press(robot, "importButton");
    WaitForAsyncUtils.waitForFxEvents();
    select(robot, "Match 2025");
    press(robot, "openButton");
    await(() -> robot.lookup("#settings").tryQuery().isPresent());
    assertAll(
        () -> assertTrue(exported.isFile()),
        () -> assertTrue(backup.isFile()),
        () -> assertEquals("Match 2025 – 20 nov. 2025", text(robot, "title")));
  }

  /**
   * Q11: the settings say which images the logo may be, show it, choose and remove it; an image that is not PNG or
   * JPEG is refused with a message.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Q11: the settings show the accepted images and the logo, which is chosen and removed")
  void logoTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    menu(robot, "optionsItem");
    final Pane settings = (Pane) natives.questions().getLast().getDialogPane().getContent();
    final Label none = (Label) settings.lookup("#noLogo");
    await(none::isVisible);
    final String hint = ((Label) settings.lookupAll(".caption").iterator().next()).getText();
    final File png = other.resolve("logo.png").toFile();
    ImageIO.write(new BufferedImage(LOGO, LOGO, BufferedImage.TYPE_INT_RGB), "png", png);
    natives.choose(png);
    robot.interact(((Button) settings.lookup("#logoButton"))::fire);
    await(() -> text(robot, "notice").equals("Le logo figurera sur les documents.") && !none.isVisible());
    final boolean shown = ((ImageView) settings.lookup("#logoView")).getImage() != null;
    final File text = Files.writeString(other.resolve("logo.txt"), "not an image").toFile();
    natives.choose(text);
    robot.interact(((Button) settings.lookup("#logoButton"))::fire);
    await(() -> natives.texts().contains("Le logo doit être une image PNG ou JPEG."));
    robot.interact(((Button) settings.lookup("#logoButton"))::fire);
    final Button remove = (Button) settings.lookup("#removeLogoButton");
    natives.answer(false);
    robot.interact(remove::fire);
    natives.answer(true);
    robot.interact(remove::fire);
    await(() -> text(robot, "notice").equals("Les documents seront imprimés sans logo.") && none.isVisible());
    assertAll(
        () -> assertTrue(hint.startsWith("Image PNG ou JPEG de 2 Mo au plus"), hint),
        () -> assertTrue(shown),
        () -> assertTrue(remove.isDisabled()),
        () -> assertFalse(Files.exists(folder.resolve("logo"))));
  }

  /**
   * A damaged directory is reported at start-up, and the editions are still listed; a data folder that cannot be
   * created is reported, and nothing is listed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A damaged directory or an unusable data folder is reported at start-up")
  void startErrorsTest(final FxRobot robot) throws Exception {
    final Path damaged = other.resolve("damaged");
    Files.createDirectories(damaged);
    Files.writeString(damaged.resolve("directory.jassdir"), "damaged");
    final Path blocked = Files.writeString(other.resolve("file"), "not a folder");
    final Stage[] stages = new Stage[2];
    robot.interact(() -> {
      stages[0] = new Stage();
      new JassApplication(context(damaged)).start(stages[0]);
      stages[1] = new Stage();
      new JassApplication(context(blocked)).start(stages[1]);
    });
    await(() -> natives.texts().size() == 2);
    WaitForAsyncUtils.waitForFxEvents();
    final Pane view = (Pane) stages[1].getScene().lookup("#viewArea");
    assertAll(
        () -> assertEquals("Le fichier est endommagé ou n’est pas un fichier de l’application.",
                           natives.texts().get(0)),
        () -> assertTrue(natives.texts().get(1).startsWith("Le fichier ne peut pas")),
        () -> assertTrue(view.getChildren().isEmpty()));
    robot.interact(() -> {
      stages[0].close();
      stages[1].close();
    });
  }

  /**
   * The menu Quit closes the window; the pending operations are completed when the application stops.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Quit closes the window and the application completes its operations when it stops")
  void quitTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    menu(robot, "quitItem");
    application.stop();
    new JassApplication(context(folder)).stop();
    assertFalse(stage.isShowing());
  }

  /**
   * F3: an unreadable file in the trash can be emptied even when no edition is listed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F3: a trash holding only an unreadable file can be emptied")
  void unreadableTrashTest(final FxRobot robot) throws Exception {
    Files.writeString(folder.resolve("trash").resolve(UUID.randomUUID() + ".jass"), "damaged");
    await(() -> names(robot).size() == 2);
    nav(robot, "trashNav");
    await(() -> robot.lookup("#emptyButton").tryQuery().isPresent());
    assertFalse(robot.lookup("#emptyButton").queryButton().isDisabled());
  }

  /**
   * Issue #3: the window about the application shows its name, version, author, licence and source code; the links
   * open their page in the browser only when clicked; the donation, at the left, opens the page of the donations;
   * Close, the default button, opens nothing.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Issue #3: the window about the application shows the version and opens the links and the donation")
  void aboutTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    natives.answer(true);
    menu(robot, "aboutItem");
    final Alert alert = natives.questions().getLast();
    final Pane about = (Pane) alert.getDialogPane().getContent();
    final Button donate = (Button) alert.getDialogPane().lookupButton(alert.getButtonTypes().getFirst());
    final Button close = (Button) alert.getDialogPane().lookupButton(alert.getButtonTypes().getLast());
    final List<String> donation = List.copyOf(natives.opened());
    robot.interact(((Hyperlink) about.lookup("#authorLink"))::fire);
    robot.interact(((Hyperlink) about.lookup("#sourceLink"))::fire);
    final List<String> links = List.copyOf(natives.opened());
    natives.answer(false);
    menu(robot, "aboutItem");
    assertAll(
        () -> assertEquals("Tournoi de Jass", alert.getHeaderText()),
        () -> assertEquals("Version 1.0.0", ((Label) about.lookup("#versionLabel")).getText()),
        () -> assertEquals("Fait avec ❤️ par Varani", ((Hyperlink) about.lookup("#authorLink")).getText()),
        () -> assertEquals("Licence Apache-2.0", ((Label) about.lookup("#licenceLabel")).getText()),
        () -> assertEquals("Faire un don", donate.getText()),
        () -> assertFalse(donate.isDefaultButton()),
        () -> assertEquals(ButtonBar.ButtonData.LEFT, ButtonBar.getButtonData(donate)),
        () -> assertEquals("Fermer", close.getText()),
        () -> assertTrue(close.isDefaultButton()),
        () -> assertTrue(close.isCancelButton()),
        () -> assertEquals(List.of("https://www.paypal.me/porchy13"), donation),
        () -> assertEquals(List.of("https://www.paypal.me/porchy13", "https://www.varani.ch",
                                   "https://github.com/porchy13/jass-tournament/"), links),
        () -> assertEquals(links, natives.opened()));
  }

  /**
   * The window about the application links to the open-source libraries it ships: one line per library, its name, its
   * role and its licence; the name opens the site of the library in the browser only when clicked.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The window about the application lists the open-source libraries and opens their site")
  void librariesTest(final FxRobot robot) throws Exception {
    await(() -> names(robot).size() == 2);
    menu(robot, "aboutItem");
    final Pane about = (Pane) natives.questions().getLast().getDialogPane().getContent();
    final Hyperlink link = (Hyperlink) about.lookup("#librariesLink");
    robot.interact(link::fire);
    final Alert alert = natives.questions().getLast();
    final Pane libraries = (Pane) alert.getDialogPane().getContent();
    final Pane list = (Pane) ((ScrollPane) libraries.getChildren().getLast()).getContent();
    final Pane line = (Pane) list.lookup("#PDFBOX");
    final Hyperlink name = (Hyperlink) line.getChildren().getFirst();
    final List<String> before = List.copyOf(natives.opened());
    robot.interact(name::fire);
    assertAll(
        () -> assertEquals("Bibliothèques open source", link.getText()),
        () -> assertEquals("Bibliothèques open source", alert.getHeaderText()),
        () -> assertEquals(Library.values().length, list.lookupAll(".library").size()),
        () -> assertEquals("Apache PDFBox", name.getText()),
        () -> assertEquals("Documents PDF · licence Apache-2.0", ((Label) line.getChildren().getLast()).getText()),
        () -> assertEquals(List.of(), before),
        () -> assertEquals(List.of("https://pdfbox.apache.org"), natives.opened()));
  }

}
