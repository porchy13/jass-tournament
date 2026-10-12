package ch.varani.jass.app;

import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.EmailAddress;
import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.DirectoryStore;
import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.EditionStore;
import ch.varani.jass.persistence.SettingsStore;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.menu;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the main window (DESIGN.md §2, §6, §9, §10): side navigation, contextual new element, search, side
 * bar, F6, detail panel floating in a narrow window, settings, menus of macOS, restored window and its saved state;
 * in French, on temporary data folders with fictitious data.
 */
@ExtendWith(ApplicationExtension.class)
final class WindowTest {

  /** The number of teams of the edition, and of the counts of the side navigation. */
  private static final int TEAMS = 3;
  /** The saved left and top of the window. */
  private static final double POSITION = 10;
  /** The saved width of the window. */
  private static final double WIDTH = 1200;
  /** The saved height of the window. */
  private static final double HEIGHT = 760;
  /** The saved position of the divider of the side navigation. */
  private static final double SIDEBAR = 0.2;
  /** A width of the window below the width of the floating detail panel. */
  private static final double NARROW = 1000;
  /** A smaller narrow width. */
  private static final double NARROWER = 990;
  /** A width of the window where the detail panel is docked, within the screen of the tests. */
  private static final double WIDE = 1150;
  /** A position of the divider of the detail panel. */
  private static final double DETAIL = 0.6;
  /** A position far outside every screen. */
  private static final double FAR = 100_000;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** Another temporary data folder. */
  @TempDir
  private Path other;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();
  /** The window. */
  private Stage stage;
  /** The application. */
  private JassApplication application;

  /**
   * Fills the data folder with an edition of three teams, a directory of two persons and saved settings, then starts
   * the application.
   *
   * @param primaryStage the stage provided by TestFX.
   *
   * @throws Exception never.
   */
  @Start
  void start(final Stage primaryStage) throws Exception {
    stage = primaryStage;
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 1; i <= TEAMS; i++) {
      teams = teams.add(new Team(new Player("Claire" + i, "Dubois"), new Player("David" + i, "Petit")));
    }
    new EditionStore(folder).create(Edition.create("Match 2026", UiSupport.TODAY, TournamentMode.TEAM).withGames(2)
                                           .withRegistrations(teams));
    new DirectoryStore(folder).save(Directory.empty()
        .add(Person.create("Anne", "Fontaine").withEmail(new EmailAddress("anne@example.ch")))
        .add(Person.create("Bruno", "Rochat")));
    new SettingsStore(folder).save(Settings.DEFAULT.withWindow(POSITION, POSITION, WIDTH, HEIGHT, false)
                                                   .withDividers(SIDEBAR, Double.NaN).values());
    application = new JassApplication(context(folder, false));
    application.start(stage);
  }

  /**
   * Returns the environment of the tests.
   *
   * @param data the data folder.
   * @param mac {@code true} for macOS.
   *
   * @return the environment.
   */
  private AppContext context(final Path data, final boolean mac) {
    return new AppContext(data, Languages.FRENCH, natives.natives(), UiSupport.CLOCK, Solving::of, mac,
                          FakeUpdater.NONE);
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
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Presses a key in the window.
   *
   * @param robot the TestFX robot.
   * @param target the node receiving the key.
   * @param code the key.
   * @param shift whether Shift is held.
   */
  private static void key(final FxRobot robot, final Node target, final KeyCode code, final boolean shift) {
    robot.interact(() -> Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false,
                                                              false, false)));
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Returns the identifier of the control having the focus.
   *
   * @return the identifier, or the style classes of a control without identifier.
   */
  private String focused() {
    final Node owner = stage.getScene().getFocusOwner();
    return owner == null ? "" : String.valueOf(owner.getId()) + owner.getStyleClass();
  }

  /**
   * The main window has the icon of the application in every size, for the title bar and the task bar.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("The windows have the icon of the application in every size")
  void iconTest(final FxRobot robot) {
    final List<Image> icons = stage.getIcons();
    assertAll(
        () -> assertEquals(MainController.ICON_SIZES.size(), icons.size()),
        () -> assertTrue(icons.stream().noneMatch(Image::isError)),
        () -> assertEquals(MainController.ICON_SIZES.getLast().doubleValue(), icons.getLast().getWidth()));
  }

  /**
   * F2: the side navigation shows the places with their counts and keeps one selected; the parts of an open edition
   * are reached from it; the new element follows the view (Shortcut+N), the search takes the focus (Shortcut+F); the
   * window is placed where it was saved.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F2: side navigation, contextual new element and search")
  @SuppressWarnings("unchecked")
  void navigationTest(final FxRobot robot) throws Exception {
    await(() -> shown(robot, "openButton") && !robot.lookup(".nav-count").queryAllAs(Label.class).iterator().next()
                                                     .getText().isEmpty());
    final List<String> counts = robot.lookup(".nav-count").queryAllAs(Label.class).stream().map(Label::getText)
                                     .toList();
    final double x = stage.getX();
    nav(robot, "editionsNav");
    final boolean kept = robot.lookup("#editionsNav").queryAs(ToggleButton.class).isSelected();
    nav(robot, "trashNav");
    await(() -> shown(robot, "restoreButton"));
    nav(robot, "directoryNav");
    await(() -> shown(robot, "search"));
    menu(robot, "searchItem");
    final String search = focused();
    menu(robot, "newItem");
    final String person = text(robot, "personTitle");
    final String newText = robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().getFirst().getItems()
                                .getFirst().getText();
    nav(robot, "editionsNav");
    await(() -> shown(robot, "openButton"));
    final TableView<EditionHeader> table = robot.lookup("#table").queryAs(TableView.class);
    robot.interact(() -> table.getSelectionModel().select(0));
    press(robot, "openButton");
    await(() -> shown(robot, "addButton"));
    nav(robot, "scheduleNav");
    await(() -> shown(robot, "registrationButton"));
    final boolean empty = robot.lookup("#empty").query().isVisible();
    final boolean documents = robot.lookup("#documents").query().isDisabled();
    press(robot, "registrationButton");
    await(() -> shown(robot, "addButton"));
    menu(robot, "newItem");
    final String registration = focused();
    assertAll(
        () -> assertEquals(List.of("1", "2", "0"), counts.subList(0, TEAMS)),
        () -> assertEquals(POSITION, x),
        () -> assertTrue(kept),
        () -> assertTrue(search.startsWith("search"), search),
        () -> assertEquals("Nouvelle personne", person),
        () -> assertEquals("_Nouvelle personne", newText),
        () -> assertTrue(empty),
        () -> assertTrue(documents),
        () -> assertTrue(registration.startsWith("first1"), registration),
        () -> assertTrue(robot.lookup("#registrationNav").queryAs(ToggleButton.class).isSelected()));
  }

  /**
   * F6 and Shift+F6 move the focus from a panel to the next and back; another key does nothing; the side navigation
   * is hidden and shown again, and its divider is saved with the window when the application stops.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F6 moves between the panels; the side bar is hidden, shown, and saved with the window")
  void keyboardTest(final FxRobot robot) throws Exception {
    await(() -> shown(robot, "openButton"));
    nav(robot, "directoryNav");
    await(() -> shown(robot, "search"));
    final Node root = stage.getScene().getRoot();
    key(robot, root, KeyCode.A, false);
    final String before = focused();
    key(robot, root, KeyCode.F6, false);
    final String next = focused();
    key(robot, root, KeyCode.F6, false);
    key(robot, root, KeyCode.F6, true);
    final String back = focused();
    final CheckMenuItem sidebar = (CheckMenuItem) robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().get(1)
                                                       .getItems().stream()
                                                       .filter(item -> "sidebarItem".equals(item.getId()))
                                                       .findFirst().orElseThrow();
    final SplitPane split = robot.lookup("#split").queryAs(SplitPane.class);
    robot.interact(() -> sidebar.setSelected(false));
    final int hidden = split.getItems().size();
    robot.interact(() -> sidebar.setSelected(true));
    final int shownAgain = split.getItems().size();
    robot.interact(() -> sidebar.setSelected(false));
    robot.interact(() -> {
      try {
        application.stop();
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    });
    final Map<String, String> saved = new SettingsStore(folder).load();
    assertAll(
        () -> assertNotNull(before),
        () -> assertNotEquals(before, next),
        () -> assertEquals(next, back),
        () -> assertEquals(1, hidden),
        () -> assertEquals(2, shownAgain),
        () -> assertEquals(String.valueOf(POSITION), saved.get(Settings.KEY_WINDOW_X)),
        () -> assertNotEquals("NaN", saved.get(Settings.KEY_SIDEBAR)));
  }

  /**
   * DESIGN.md §6: in a narrow window the detail panel floats, opens on a selection, closes with its button or
   * Escape; in a wide window it is docked again, with its divider remembered.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The detail panel floats in a narrow window and docks in a wide one")
  @SuppressWarnings("unchecked")
  void detailTest(final FxRobot robot) throws Exception {
    await(() -> shown(robot, "openButton"));
    nav(robot, "directoryNav");
    await(() -> shown(robot, "search"));
    final Node detail = robot.lookup("#detail").query();
    final TableView<Person> table = robot.lookup("#table").queryAs(TableView.class);
    robot.interact(() -> stage.setWidth(NARROW));
    await(() -> !detail.isVisible());
    robot.interact(() -> stage.setWidth(NARROWER));
    WaitForAsyncUtils.waitForFxEvents();
    robot.interact(() -> table.getSelectionModel().clearAndSelect(0));
    final boolean opened = detail.isVisible();
    press(robot, "newButton");
    key(robot, detail, KeyCode.A, false);
    final boolean stillOpen = detail.isVisible();
    key(robot, detail, KeyCode.ESCAPE, false);
    final boolean escaped = !detail.isVisible();
    robot.interact(() -> table.getSelectionModel().clearAndSelect(1));
    press(robot, "closeDetail");
    final boolean closed = !detail.isVisible();
    robot.interact(() -> stage.setWidth(WIDE));
    await(detail::isVisible);
    key(robot, detail, KeyCode.ESCAPE, false);
    final boolean docked = detail.isVisible();
    final SplitPane split = robot.lookup("#body .split-pane").queryAs(SplitPane.class);
    robot.interact(() -> split.setDividerPositions(DETAIL));
    robot.interact(() -> stage.setWidth(NARROW));
    robot.interact(() -> stage.setWidth(WIDE));
    await(detail::isVisible);
    assertAll(
        () -> assertTrue(opened),
        () -> assertTrue(stillOpen),
        () -> assertTrue(escaped),
        () -> assertTrue(closed),
        () -> assertTrue(docked),
        () -> assertEquals(2, split.getItems().size()));
  }

  /**
   * DESIGN.md §2, §9: the settings change the theme, the accent and the animations at once, and are saved; the
   * notices leave the status bar after a while.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The settings change the theme, the accent and the animations, and are saved")
  @SuppressWarnings("unchecked")
  void settingsTest(final FxRobot robot) throws Exception {
    await(() -> shown(robot, "openButton"));
    menu(robot, "optionsItem");
    final Pane panel = (Pane) natives.questions().getLast().getDialogPane().getContent();
    final String header = natives.questions().getLast().getHeaderText();
    final ChoiceBox<ThemeMode> themes = (ChoiceBox<ThemeMode>) panel.lookup("#themeBox");
    final String light = themes.getConverter().toString(ThemeMode.LIGHT);
    robot.interact(() -> themes.setValue(ThemeMode.DARK));
    final String sheet = stage.getScene().getStylesheets().getFirst();
    robot.interact(() -> ((CheckBox) panel.lookup("#accentBox")).setSelected(true));
    final String style = stage.getScene().getRoot().getStyle();
    robot.interact(() -> ((CheckBox) panel.lookup("#motionBox")).setSelected(true));
    await(() -> "true".equals(new SettingsStore(folder).load().get(Settings.KEY_REDUCED_MOTION)));
    nav(robot, "directoryNav");
    await(() -> shown(robot, "emailsButton"));
    press(robot, "emailsButton");
    final String notice = text(robot, "notice");
    await(() -> text(robot, "notice").isEmpty());
    assertAll(
        () -> assertEquals("Options", header),
        () -> assertEquals("Clair", light),
        () -> assertTrue(sheet.endsWith(Theme.DARK_TOKENS)),
        () -> assertTrue(style.startsWith("-c-accent:"), style),
        () -> assertEquals("DARK", new SettingsStore(folder).load().get(Settings.KEY_THEME)),
        () -> assertEquals("1 adresses e-mail copiées.", notice));
  }

  /**
   * DESIGN.md §2: on macOS, the settings are in the File menu, there is no Tools menu and the application menu quits;
   * a window saved outside the screens is centred, a maximized one is maximized again; an empty data folder says how to
   * start; a data folder that cannot be opened leaves the directory unavailable.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Menus of macOS, restored window, empty and unusable data folders")
  void systemTest(final FxRobot robot) throws Exception {
    new SettingsStore(other).save(Settings.DEFAULT.withWindow(FAR, FAR, WIDTH, HEIGHT, true).values());
    final Path blocked = Files.writeString(folder.resolve("blocked"), "not a folder");
    final Stage[] stages = new Stage[2];
    final JassApplication unusable = new JassApplication(context(blocked, false));
    robot.interact(() -> {
      stages[0] = new Stage();
      new JassApplication(context(other, true)).start(stages[0]);
      stages[1] = new Stage();
      unusable.start(stages[1]);
    });
    final FxRobot mac = new FxRobot();
    final Node macRoot = stages[0].getScene().getRoot();
    await(() -> mac.from(macRoot).lookup("#table").tryQuery().isPresent());
    final List<Menu> menus = mac.from(macRoot).lookup(".menu-bar").queryAs(MenuBar.class).getMenus();
    final List<Boolean> visible = new ArrayList<>();
    for (final String id : List.of("settingsItem", "quitItem")) {
      visible.add(menus.getFirst().getItems().stream().filter(item -> id.equals(item.getId())).findFirst()
                       .map(MenuItem::isVisible).orElseThrow());
    }
    visible.add(menus.stream().filter(menu -> "toolsMenu".equals(menu.getId())).findFirst().orElseThrow().isVisible());
    final boolean maximized = stages[0].isMaximized();
    final Button create = (Button) mac.from(macRoot).lookup(".empty-state .button").query();
    robot.interact(create::fire);
    await(() -> mac.from(macRoot).lookup("#nameField").tryQuery().isPresent());
    robot.interact(() -> menus.get(1).getItems().stream().filter(item -> "directoryItem".equals(item.getId()))
                              .findFirst().orElseThrow().fire());
    await(() -> mac.from(macRoot).lookup(".empty-state .button").tryQuery().isPresent());
    final Button importButton = (Button) mac.from(macRoot).lookup(".empty-state .button").query();
    robot.interact(importButton::fire);
    await(() -> mac.from(macRoot).lookup("#fileButton").tryQuery().isPresent());
    robot.interact(() -> menus.getFirst().getItems().stream().filter(item -> "settingsItem".equals(item.getId()))
                              .findFirst().orElseThrow().fire());
    final String header = natives.questions().getLast().getHeaderText();
    final Node blockedRoot = stages[1].getScene().getRoot();
    robot.interact(() -> mac.from(blockedRoot).lookup(".menu-bar").queryAs(MenuBar.class).getMenus().get(1)
                            .getItems().stream().filter(item -> "directoryItem".equals(item.getId()))
                            .findFirst().orElseThrow().fire());
    final boolean unavailable = mac.from(blockedRoot).lookup("#unavailable").query().isVisible();
    final Scene macScene = stages[0].getScene();
    robot.interact(() -> {
      stages[0].close();
      stages[1].close();
    });
    unusable.stop();
    assertAll(
        () -> assertEquals(List.of(true, false, false), visible),
        () -> assertTrue(maximized),
        () -> assertEquals("Réglages", header),
        () -> assertTrue(unavailable),
        () -> assertNotNull(macScene));
  }

}
