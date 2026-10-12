package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.EditionStore;
import ch.varani.jass.persistence.LogoStore;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.disabled;
import static ch.varani.jass.app.UiSupport.menu;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the external display (phase 11b, F18): the window opened, moved to full screen and closed; the round
 * with its countdown started by the user, its places by pages; the time of a game saved; the final ranking revealed
 * from the last line; the display following the open edition and closed with the main window; in French, with a
 * clock moved by the test and fictitious teams ({@link ScoreFixtures}).
 */
@ExtendWith(ApplicationExtension.class)
final class DisplayScreenTest {

  /** The edition scheduled without result. */
  private static final String ROUND = "Match affiché";
  /** The edition where every game has a result, without tie. */
  private static final String FINAL = "Match final";
  /** The edition without schedule. */
  private static final String OPEN = "Match ouvert";
  /** The individual edition. */
  private static final String INDIVIDUAL = "Match individuel";
  /** The start of the clock of the tests. */
  private static final Instant START = Instant.parse("2026-11-20T14:00:00Z");
  /** The points of column A of the games of the final edition, in the order of the schedule. */
  private static final int[] DISTINCT = {100, 90, 80, 70};
  /** The time elapsed in the tests of the countdown, in seconds. */
  private static final int ELAPSED = 30;
  /** The height of the display window that leaves room for one line only, in pixels. */
  private static final double SHORT = 320;
  /** The cells of a line of the places: participant, names, place. */
  private static final int PLACE_CELLS = 3;
  /** The cells of a line of the ranking of two rounds: rank, participant, names, two rounds, total. */
  private static final int RANKING_CELLS = 6;
  /** The players of the individual edition: one table, and one player sitting out. */
  private static final int PLAYERS = 5;
  /** The side of the logo of the tests, in pixels. */
  private static final int LOGO = 8;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** Another temporary folder, for the logo. */
  @TempDir
  private Path other;
  /** The clock of the application, moved by the tests. */
  private final MutableClock clock = new MutableClock(START);

  /**
   * Fills the data folder with a logo and the editions of the scenarios, then starts the application in French.
   *
   * @param stage the stage provided by TestFX.
   *
   * @throws Exception never.
   */
  @Start
  void start(final Stage stage) throws Exception {
    final Path image = other.resolve("logo.png");
    ImageIO.write(new BufferedImage(LOGO, LOGO, BufferedImage.TYPE_INT_RGB), "png", image.toFile());
    new LogoStore(folder).save(image);
    final EditionStore store = new EditionStore(folder);
    store.create(ScoreFixtures.scheduled(ROUND));
    store.create(ScoreFixtures.played(ScoreFixtures.scheduled(FINAL), DISTINCT));
    store.create(ScoreFixtures.registered(OPEN));
    final List<Player> players = new ArrayList<>();
    for (int i = 1; i <= PLAYERS; i++) {
      players.add(new Player("Joueur" + i, "Test"));
    }
    store.create(Edition.create(INDIVIDUAL, UiSupport.TODAY, TournamentMode.INDIVIDUAL).withGames(1)
        .withRegistrations(new IndividualRegistrations(players))
        .withSchedule(new IndividualSchedule(PLAYERS, 1, List.of(new IndividualRound(1, List.of(
            new IndividualGame(1, new Partners(1, 2), new Partners(ScoreFixtures.THIRD, ScoreFixtures.TEAMS))))))));
    new JassApplication(new AppContext(folder, Languages.FRENCH, new FakeNatives().natives(), clock,
                                       Solving::of, false, FakeUpdater.NONE)).start(stage);
  }

  /**
   * Returns the window of the display.
   *
   * @return the window, or {@code null} if it is not shown.
   */
  private static Stage display() {
    return (Stage) Window.getWindows().stream()
                         .filter(window -> window instanceof final Stage stage && stage.getTitle() != null
                                           && stage.getTitle().startsWith("Affichage"))
                         .findFirst().orElse(null);
  }

  /**
   * Returns the texts of the lines of the display, cell by cell.
   *
   * @param robot the TestFX robot.
   *
   * @return the texts.
   */
  private static List<String> cells(final FxRobot robot) {
    return robot.lookup("#grid").queryAs(GridPane.class).getChildren().stream()
                .map(node -> ((Label) node).getText()).toList();
  }

  /**
   * Opens the control of the display of an edition.
   *
   * @param robot the TestFX robot.
   * @param name the name of the edition.
   *
   * @throws Exception if the edition is not opened in time.
   */
  private static void open(final FxRobot robot, final String name) throws Exception {
    ScoreFixtures.open(robot, name, "displayNav");
    await(() -> shown(robot, "openButton"));
  }

  /**
   * Tells whether a node is shown.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the node.
   *
   * @return {@code true} if it is visible.
   */
  private static boolean visible(final FxRobot robot, final String id) {
    final Node node = robot.lookup("#" + id).query();
    return node.isVisible();
  }

  /**
   * Q21: the countdown shows the time of a game until the start signal, then the time remaining, and says when the
   * time is over; the signal can be cancelled; another round waits for its own signal; the time of a game is saved;
   * the places alternate by pages when they do not fit; the window goes to full screen and is closed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Q21: the countdown of a round starts at the signal given by the user")
  void roundTest(final FxRobot robot) throws Exception {
    open(robot, ROUND);
    press(robot, "openButton");
    await(() -> display() != null && display().isShowing());
    final String heading = text(robot, "heading");
    final String waiting = text(robot, "status");
    final List<String> places = cells(robot);
    final boolean logo = robot.lookup("#logo").queryAs(ImageView.class).getImage() != null;
    press(robot, "startButton");
    final String started = text(robot, "roundStatus");
    clock.advance(Duration.ofSeconds(ELAPSED));
    await(() -> "59:30".equals(text(robot, "timer")));
    final String running = text(robot, "status");
    clock.advance(Duration.ofMinutes(Edition.DEFAULT_GAME_MINUTES));
    await(() -> "00:00".equals(text(robot, "timer")));
    final String over = text(robot, "status");
    final boolean overStyle = robot.lookup("#timer").query().getStyleClass().contains(DisplayWindow.OVER);
    press(robot, "resetButton");
    final String reset = text(robot, "timer");
    @SuppressWarnings("unchecked")
    final Spinner<Integer> minutes = robot.lookup("#minutes").queryAs(Spinner.class);
    robot.interact(() -> minutes.increment(1));
    await(() -> ScoreFixtures.stored(folder, ROUND).gameMinutes() == Edition.DEFAULT_GAME_MINUTES + 1);
    final String longer = text(robot, "timer");
    @SuppressWarnings("unchecked")
    final ComboBox<Integer> rounds = robot.lookup("#roundChoice").queryAs(ComboBox.class);
    press(robot, "startButton");
    robot.interact(() -> rounds.getSelectionModel().select(1));
    WaitForAsyncUtils.waitForFxEvents();
    final String second = text(robot, "heading");
    final boolean cancelled = disabled(robot, "resetButton");
    assertAll(
        () -> assertEquals("Partie 1", heading),
        () -> assertEquals("En attente du départ", waiting),
        () -> assertEquals(List.of("Équipe 1", "Emma1 Favre & Félix1 Girard", "Place 1 · table 1"),
                           places.subList(0, PLACE_CELLS)),
        () -> assertEquals("Place 4 · table 2", places.getLast()),
        () -> assertTrue(logo),
        () -> assertEquals("Départ donné à 14:00:00.", started),
        () -> assertEquals("Temps restant", running),
        () -> assertEquals("Temps écoulé", over),
        () -> assertTrue(overStyle),
        () -> assertEquals("60:00", reset),
        () -> assertEquals("61:00", longer),
        () -> assertEquals("Partie 2", second),
        () -> assertTrue(cancelled),
        () -> assertEquals("Affichage – " + ROUND, display().getTitle()));
  }

  /**
   * Q22: the places that do not fit alternate by pages; the window goes to the screen chosen, in full screen or not,
   * and is closed and opened again with the same state.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Q22: the places alternate by pages, the window goes to full screen and back")
  void windowTest(final FxRobot robot) throws Exception {
    open(robot, ROUND);
    final CheckBox full = robot.lookup("#fullScreen").queryAs(CheckBox.class);
    robot.interact(full::fire);
    press(robot, "openButton");
    await(() -> display() != null && display().isShowing());
    final boolean fullScreen = display().isFullScreen();
    robot.interact(full::fire);
    final boolean window = !display().isFullScreen();
    robot.interact(() -> display().setHeight(SHORT));
    await(() -> text(robot, "pageLabel").startsWith("Page"));
    final String first = text(robot, "pageLabel");
    clock.advance(Duration.ofSeconds(DisplayModel.PAGE_SECONDS));
    await(() -> !first.equals(text(robot, "pageLabel")));
    final String next = text(robot, "pageLabel");
    final Stage shownDisplay = display();
    press(robot, "closeButton");
    final boolean closed = !shownDisplay.isShowing();
    final boolean openShown = visible(robot, "openButton");
    menu(robot, "listItem");
    open(robot, ROUND);
    @SuppressWarnings("unchecked")
    final ComboBox<Object> screens = robot.lookup("#screenChoice").queryAs(ComboBox.class);
    robot.interact(() -> screens.getSelectionModel().select(0));
    robot.interact(() -> screens.fireEvent(new javafx.event.ActionEvent()));
    menu(robot, "listItem");
    open(robot, ROUND);
    press(robot, "openButton");
    await(() -> display().isShowing());
    assertAll(
        () -> assertTrue(fullScreen),
        () -> assertTrue(window),
        () -> assertTrue(first.startsWith("Page "), first),
        () -> assertTrue(next.startsWith("Page "), next),
        () -> assertTrue(closed),
        () -> assertTrue(openShown),
        () -> assertNotNull(screens.getValue()),
        () -> assertFalse(display().isFullScreen()),
        () -> assertTrue(visible(robot, "closeButton")));
  }

  /**
   * F18: once every game has a result and nobody is tied, the final ranking is revealed from the last line to the
   * first, with the points of every round; the lines can be hidden again and the display can go back to the round.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F18: the final ranking is revealed from the last line to the first")
  void rankingTest(final FxRobot robot) throws Exception {
    open(robot, FINAL);
    press(robot, "openButton");
    await(() -> display() != null && display().isShowing());
    final String none = text(robot, "rankingStatus");
    final String firstReveal = robot.lookup("#revealButton").queryAs(Button.class).getText();
    press(robot, "revealButton");
    final String heading = text(robot, "heading");
    final List<String> last = cells(robot);
    final boolean clockHidden = !visible(robot, "clockBox");
    final boolean rankingDisabled = disabled(robot, "rankingButton");
    for (int i = 1; i < ScoreFixtures.TEAMS; i++) {
      press(robot, "revealButton");
    }
    final List<String> all = cells(robot);
    final String done = robot.lookup("#revealButton").queryAs(Button.class).getText();
    final boolean revealDisabled = disabled(robot, "revealButton");
    press(robot, "hideButton");
    final int hidden = cells(robot).size();
    press(robot, "roundButton");
    final String round = text(robot, "heading");
    press(robot, "rankingButton");
    assertAll(
        () -> assertEquals("Rangs révélés : 0 sur 4.", none),
        () -> assertEquals("Révéler le rang 4", firstReveal),
        () -> assertEquals("Classement final", heading),
        () -> assertEquals(List.of("Rang", "", "", "Partie 1", "Partie 2", "Total", "4", "Équipe 2",
                                   "Emma2 Favre & Félix2 Girard", "912", "1 120", "2 032"),
                           last.stream().map(cell -> cell.replace(' ', ' ')).toList()),
        () -> assertTrue(clockHidden),
        () -> assertTrue(rankingDisabled),
        () -> assertEquals("1", all.get(RANKING_CELLS)),
        () -> assertEquals("Équipe 1", all.get(RANKING_CELLS + 1)),
        () -> assertEquals("Tout est révélé", done),
        () -> assertTrue(revealDisabled),
        () -> assertEquals(RANKING_CELLS, hidden),
        () -> assertEquals("Partie 2", round),
        () -> assertEquals("Classement final", text(robot, "heading")));
  }

  /**
   * F18: the final ranking cannot be revealed before every game has a result; the display follows the edition open
   * in the main window: another edition starts it again; the window is opened from the menu, and closed with the main
   * window.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F18: the display follows the open edition and closes with the main window")
  void followTest(final FxRobot robot) throws Exception {
    final boolean noEdition = robot.lookup(".menu-bar").queryAs(javafx.scene.control.MenuBar.class).getMenus()
                                   .get(1).getItems().stream()
                                   .filter(item -> "displayItem".equals(item.getId())).findFirst().orElseThrow()
                                   .isDisable();
    open(robot, ROUND);
    final String unavailable = text(robot, "rankingStatus");
    final boolean revealDisabled = disabled(robot, "revealButton");
    press(robot, "startButton");
    menu(robot, "listItem");
    ScoreFixtures.open(robot, INDIVIDUAL, "registrationNav");
    menu(robot, "displayItem");
    await(() -> display() != null && display().isShowing());
    final boolean navigated = robot.lookup("#displayNav").queryAs(ToggleButton.class).isSelected();
    final String waiting = text(robot, "status");
    final List<String> players = cells(robot);
    menu(robot, "listItem");
    ScoreFixtures.open(robot, OPEN, "displayNav");
    await(() -> visible(robot, "empty"));
    final String unscheduled = text(robot, "heading");
    final boolean clockHidden = !visible(robot, "clockBox");
    press(robot, "registrationButton");
    await(() -> shown(robot, "addButton"));
    final Stage shownDisplay = display();
    menu(robot, "quitItem");
    assertAll(
        () -> assertTrue(noEdition),
        () -> assertEquals("Le classement final s’affiche quand toutes les parties ont un résultat et que les "
                           + "égalités sont départagées.", unavailable),
        () -> assertTrue(revealDisabled),
        () -> assertTrue(navigated),
        () -> assertEquals("En attente du départ", waiting),
        () -> assertEquals(List.of("Joueur 1", "Joueur1 Test", "Place 1 · table 1"), players.subList(0, PLACE_CELLS)),
        () -> assertEquals("Au repos", players.getLast()),
        () -> assertEquals("Les rencontres ne sont pas encore calculées.", unscheduled),
        () -> assertTrue(clockHidden),
        () -> assertFalse(shownDisplay.isShowing()));
  }

}
