package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.TieBreak;
import ch.varani.jass.persistence.EditionStore;
import ch.varani.jass.scoring.RankingEntry;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.List;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.disabled;
import static ch.varani.jass.app.UiSupport.menu;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.text;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the ranking (phase 11, F11): provisional ranking with the points of every game, exclusion for cheating
 * and its cancellation (rule 17), drawing of lots between tied participants (rule 15), final ranking; in French, on a
 * temporary data folder with fictitious teams ({@link ScoreFixtures}).
 */
@ExtendWith(ApplicationExtension.class)
final class RankingScreenTest {

  /** The edition with one result. */
  private static final String PROVISIONAL = "Classement provisoire";
  /** The edition where teams 2 and 3 are tied with 2'512 points. */
  private static final String TIED = "Classement égalité";
  /** The edition with a final ranking without tie. */
  private static final String FINAL = "Classement définitif";
  /** The edition with a final ranking without tie and a draw without tied participant. */
  private static final String DRAWN = "Classement tiré";
  /** The edition without schedule. */
  private static final String OPEN = "Classement ouvert";
  /** The points of column A of every deal of the only sheet of the provisional edition. */
  private static final int GIVER = 100;
  /** The total of column A of the only sheet of the provisional edition: 16 deals of 100 points. */
  private static final int SHEET = 1600;
  /** The points of column A of every deal of the tied edition: 1'264 against 1'248 per game. */
  private static final int TIE = 79;
  /** The points of column A of the games of the final edition, in the order of the schedule. */
  private static final int[] DISTINCT = {100, 90, 80, 70};
  /** The index of the column of the first game in the ranking. */
  private static final int FIRST_GAME = 3;
  /** The index of the column of the total in the ranking. */
  private static final int TOTAL = 5;
  /** The index of the column of the number of games in the ranking. */
  private static final int GAMES = 6;
  /** The seed of the draw stored in the edition without tie. */
  private static final long SEED = 7;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
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
    store.create(ScoreFixtures.scheduled(PROVISIONAL)
                              .withResults(Results.EMPTY.withSheet(ScoreFixtures.sheet(1, 1, GIVER))));
    store.create(ScoreFixtures.played(ScoreFixtures.scheduled(TIED), TIE, TIE, TIE, TIE));
    store.create(ScoreFixtures.played(ScoreFixtures.scheduled(FINAL), DISTINCT));
    final Edition drawn = ScoreFixtures.played(ScoreFixtures.scheduled(DRAWN), DISTINCT);
    store.create(drawn.withResults(drawn.results().withTieBreak(new TieBreak(SEED, List.of()))));
    store.create(ScoreFixtures.registered(OPEN));
    new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(), UiSupport.CLOCK,
                                       Solving::of, false, FakeUpdater.NONE)).start(stage);
  }

  /**
   * Returns the lines of the ranking.
   *
   * @param robot the TestFX robot.
   *
   * @return the table.
   */
  @SuppressWarnings("unchecked")
  private static TableView<RankingEntry> ranking(final FxRobot robot) {
    return robot.lookup("#table").queryAs(TableView.class);
  }

  /**
   * Returns the text of a cell of the ranking.
   *
   * @param robot the TestFX robot.
   * @param row the index of the line.
   * @param column the index of the column.
   *
   * @return the text.
   */
  private static String cell(final FxRobot robot, final int row, final int column) {
    return String.valueOf(ranking(robot).getColumns().get(column).getCellData(row));
  }

  /**
   * Opens the ranking of an edition.
   *
   * @param robot the TestFX robot.
   * @param name the name of the edition.
   *
   * @throws Exception if the edition is not opened in time.
   */
  private static void open(final FxRobot robot, final String name) throws Exception {
    ScoreFixtures.open(robot, name, "rankingNav");
    await(() -> shown(robot, "drawButton"));
  }

  /**
   * Rules 14 and 17: the provisional ranking shows the points of every game, the participant named by its number
   * then its players, the numbers and their heads aligned to the right; a participant is excluded after confirmation,
   * leaves the ranking, and can be readmitted.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 17: an excluded team leaves the ranking until its exclusion is cancelled")
  void exclusionTest(final FxRobot robot) throws Exception {
    open(robot, PROVISIONAL);
    final String state = text(robot, "state");
    final boolean draw = disabled(robot, "drawButton");
    final boolean exclude = disabled(robot, "excludeButton");
    final boolean noExcluded = robot.lookup("#excludedBox").query().isVisible();
    final List<String> first = List.of(cell(robot, 0, 0), cell(robot, 0, 1), cell(robot, 0, FIRST_GAME),
                                       cell(robot, 0, FIRST_GAME + 1), cell(robot, 0, TOTAL),
                                       cell(robot, 0, GAMES));
    final String names = cell(robot, 0, 2);
    final List<Boolean> numeric = ranking(robot).getColumns().stream()
        .map(column -> column.getStyleClass().contains(Views.NUMBER)).toList();
    final List<Pos> heads = robot.lookup(".column-header.number .label").queryAll().stream()
        .map(label -> ((Label) label).getAlignment()).distinct().toList();
    robot.interact(() -> ranking(robot).getSelectionModel().select(0));
    natives.answer(false);
    press(robot, "excludeButton");
    final int cancelled = ScoreFixtures.stored(folder, PROVISIONAL).results().excluded().size();
    natives.answer(true);
    press(robot, "excludeButton");
    await(() -> !ScoreFixtures.stored(folder, PROVISIONAL).results().excluded().isEmpty());
    await(() -> robot.lookup("#excludedBox").query().isVisible());
    final String excluded = text(robot, "notice");
    final int lines = ranking(robot).getItems().size();
    @SuppressWarnings("unchecked")
    final TableView<Integer> excludedTable = robot.lookup("#excludedTable").queryAs(TableView.class);
    final String excludedNames = String.valueOf(excludedTable.getColumns().get(1).getCellData(0));
    robot.interact(() -> excludedTable.getSelectionModel().select(0));
    press(robot, "readmitButton");
    await(() -> ScoreFixtures.stored(folder, PROVISIONAL).results().excluded().isEmpty());
    WaitForAsyncUtils.waitForFxEvents();
    assertAll(
        () -> assertEquals("Classement provisoire, parties avec un résultat : 1 sur 4.", state),
        () -> assertTrue(draw),
        () -> assertTrue(exclude),
        () -> assertFalse(noExcluded),
        () -> assertEquals(List.of("1", "1", messages.number(SHEET), "",
                                   messages.number(SHEET), "1"), first),
        () -> assertEquals("Emma1 Favre & Félix1 Girard", names),
        () -> assertEquals("Emma1 Favre & Félix1 Girard", excludedNames),
        () -> assertEquals(List.of(true, true, false, true, true, true, true), numeric),
        () -> assertEquals(List.of(Pos.CENTER_RIGHT), heads),
        () -> assertEquals(0, cancelled),
        () -> assertEquals("Équipe 1 : Emma1 Favre & Félix1 Girard a été exclu du classement.", excluded),
        () -> assertEquals(ScoreFixtures.TEAMS - 1, lines),
        () -> assertEquals("Équipe 1 : Emma1 Favre & Félix1 Girard a été réintégré au classement.",
                           text(robot, "notice")),
        () -> assertEquals(ScoreFixtures.TEAMS, ranking(robot).getItems().size()),
        () -> assertFalse(robot.lookup("#excludedBox").query().isVisible()));
  }

  /**
   * Rule 15: once every game has a result, the tied teams are ranked by drawing lots, after confirmation; the
   * outcome is stored, the ranking becomes final and the exclusions are closed; cancelling the draw, after
   * confirmation, ties the teams again.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 15: the ties are broken by drawing lots once every game has a result")
  void drawTest(final FxRobot robot) throws Exception {
    open(robot, TIED);
    final String state = text(robot, "state");
    final boolean warning = robot.lookup("#state").query().getStyleClass().contains("warning");
    final List<Integer> tied = ranking(robot).getItems().stream().map(RankingEntry::rank).toList();
    final boolean enabled = !disabled(robot, "drawButton");
    natives.answer(false);
    press(robot, "drawButton");
    final boolean notDrawn = ScoreFixtures.stored(folder, TIED).results().tieBreak() == null;
    natives.answer(true);
    press(robot, "drawButton");
    await(() -> ScoreFixtures.stored(folder, TIED).results().tieBreak() != null);
    await(() -> shown(robot, "cancelDrawButton") && robot.lookup("#cancelDrawButton").query().isVisible());
    final List<Integer> drawnRanks = ranking(robot).getItems().stream().map(RankingEntry::rank).toList();
    final String drawnState = text(robot, "state");
    final String drawnNotice = text(robot, "notice");
    final List<Integer> order = ScoreFixtures.stored(folder, TIED).results().tieBreak().order().stream().sorted()
                                             .toList();
    robot.interact(() -> ranking(robot).getSelectionModel().select(0));
    final boolean locked = disabled(robot, "excludeButton");
    natives.answer(false);
    press(robot, "cancelDrawButton");
    final boolean kept = ScoreFixtures.stored(folder, TIED).results().tieBreak() != null;
    natives.answer(true);
    press(robot, "cancelDrawButton");
    await(() -> ScoreFixtures.stored(folder, TIED).results().tieBreak() == null);
    await(() -> robot.lookup("#drawButton").query().isVisible());
    assertAll(
        () -> assertEquals("Toutes les parties ont un résultat, mais des participants sont à égalité : tirez au "
                           + "sort (règle 15).", state),
        () -> assertTrue(warning),
        () -> assertEquals(List.of(1, 2, 2, ScoreFixtures.TEAMS), tied),
        () -> assertTrue(enabled),
        () -> assertTrue(notDrawn),
        () -> assertEquals(List.of(2, ScoreFixtures.THIRD), order),
        () -> assertEquals(List.of(1, 2, ScoreFixtures.THIRD, ScoreFixtures.TEAMS), drawnRanks),
        () -> assertEquals("Classement définitif ; les égalités ont été départagées par tirage au sort (règle 15).",
                           drawnState),
        () -> assertEquals("Le tirage au sort a été effectué.", drawnNotice),
        () -> assertTrue(locked),
        () -> assertTrue(kept),
        () -> assertEquals(tied, ranking(robot).getItems().stream().map(RankingEntry::rank).toList()),
        () -> assertFalse(robot.lookup("#cancelDrawButton").query().isVisible()),
        () -> assertEquals("Le tirage au sort a été annulé : les feuilles sont de nouveau modifiables.",
                           text(robot, "notice")));
  }

  /**
   * Rule 14: once every game has a result and no participant is tied, the ranking is final, with or without a draw
   * stored.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 14: the ranking is final once every game has a result and nobody is tied")
  void finalTest(final FxRobot robot) throws Exception {
    open(robot, FINAL);
    final String state = text(robot, "state");
    final boolean draw = disabled(robot, "drawButton");
    final List<Integer> order = ranking(robot).getItems().stream().map(RankingEntry::participant).toList();
    menu(robot, "listItem");
    open(robot, DRAWN);
    assertAll(
        () -> assertEquals("Classement définitif.", state),
        () -> assertTrue(draw),
        () -> assertEquals(List.of(1, ScoreFixtures.THIRD, ScoreFixtures.TEAMS, 2), order),
        () -> assertEquals("Classement définitif.", text(robot, "state")),
        () -> assertTrue(robot.lookup("#cancelDrawButton").query().isVisible()),
        () -> assertFalse(robot.lookup("#drawButton").query().isVisible()));
  }

  /**
   * Rule 14: before the schedule exists, the ranking leads to the registration.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 14: without schedule, the ranking leads to the registration")
  void emptyTest(final FxRobot robot) throws Exception {
    open(robot, OPEN);
    final String state = text(robot, "state");
    press(robot, "registrationButton");
    await(() -> shown(robot, "addButton"));
    assertAll(
        () -> assertEquals("", state),
        () -> assertTrue(robot.lookup("#registrationNav").queryAs(ToggleButton.class).isSelected()));
  }

}
