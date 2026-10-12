package ch.varani.jass.app;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.TieBreak;
import ch.varani.jass.persistence.EditionStore;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
 * Scenarios of the entry of the scores (phase 11, F9): derived and typed columns, rejected values, inconsistent deals,
 * rule 16 deduction, absences (rule 19), keyboard, clearing of a sheet, next game to enter, saving; in French, on a
 * temporary data folder with fictitious teams ({@link ScoreFixtures}).
 */
@ExtendWith(ApplicationExtension.class)
final class ScoresScreenTest {

  /** The edition whose sheets are entered: one sheet entered, round 1 table 2, over the points of a game. */
  private static final String POINTS = "Match points";
  /** The edition where every game has a result. */
  private static final String COMPLETE = "Match complet";
  /** The edition where every game has a result and the lots are drawn. */
  private static final String DRAWN = "Match tiré";
  /** The edition without schedule. */
  private static final String OPEN = "Match ouvert";
  /** The points typed in column A of the sheet entered: 16 × 100 + 16 × 60 = 2'560, i.e. 48 too many. */
  private static final int GIVER = 100;
  /** The points typed in column B of the sheet entered. */
  private static final int RECEIVER = 60;
  /** The points of column A in the complete edition. */
  private static final int PLAYED = 80;
  /** The last deal of the plan of the regulation. */
  private static final int LAST = 16;
  /** The index of the column of the result in the list of the games. */
  private static final int RESULT = 4;
  /** The deals of a block. */
  private static final int BLOCK = 4;
  /** The rule 16 deduction of the sheet entered: half of 48, for each side. */
  private static final int DEDUCTION = 24;
  /** The points of a forfeit: half of a game (rule 19). */
  private static final int FORFEIT = 1256;
  /** The points typed then left by going to another view. */
  private static final int LEFT = 90;

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
    final Edition points = ScoreFixtures.scheduled(POINTS);
    store.create(points.withResults(Results.EMPTY.withSheet(
        ScoreFixtures.sheet(1, 2, new DealScore(GIVER, RECEIVER)))));
    store.create(ScoreFixtures.played(ScoreFixtures.scheduled(COMPLETE), PLAYED, PLAYED, PLAYED, PLAYED));
    store.create(ScoreFixtures.registered(OPEN));
    final Edition drawn = ScoreFixtures.played(ScoreFixtures.scheduled(DRAWN), PLAYED, PLAYED, PLAYED, PLAYED);
    store.create(drawn.withResults(drawn.results().withTieBreak(new TieBreak(1, List.of(2, ScoreFixtures.THIRD)))));
    new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(), UiSupport.CLOCK,
                                       Solving::of, false, FakeUpdater.NONE)).start(stage);
  }

  /**
   * Returns the field of a column of a deal.
   *
   * @param robot the TestFX robot.
   * @param id its identifier: {@code a} or {@code b} and the number of the deal.
   *
   * @return the field.
   */
  private static TextField field(final FxRobot robot, final String id) {
    return robot.lookup("#" + id).queryAs(TextField.class);
  }

  /**
   * Types a text into a field of the sheet, then presses Enter.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the field.
   * @param value the text.
   */
  private static void enter(final FxRobot robot, final String id, final String value) {
    final TextField field = field(robot, id);
    robot.interact(() -> {
      field.requestFocus();
      field.setText(value);
      field.fireEvent(new ActionEvent());
    });
    WaitForAsyncUtils.waitForFxEvents();
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
   * Returns the identifier of the node that has the focus.
   *
   * @param robot the TestFX robot.
   *
   * @return the identifier.
   */
  private static String focused(final FxRobot robot) {
    return robot.lookup("#table").query().getScene().getFocusOwner().getId();
  }

  /**
   * Returns the list of the games.
   *
   * @param robot the TestFX robot.
   *
   * @return the table.
   */
  @SuppressWarnings("unchecked")
  private static TableView<Pairing> games(final FxRobot robot) {
    return robot.lookup("#table").queryAs(TableView.class);
  }

  /**
   * Selects a game in the list.
   *
   * @param robot the TestFX robot.
   * @param index the index of the game, in the order of the schedule.
   */
  private static void select(final FxRobot robot, final int index) {
    robot.interact(() -> games(robot).getSelectionModel().select(index));
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Tells whether a node has a style class.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the node.
   * @param styleClass the style class.
   *
   * @return {@code true} if it has it.
   */
  private static boolean styled(final FxRobot robot, final String id, final String styleClass) {
    return robot.lookup("#" + id).query().getStyleClass().contains(styleClass);
  }

  /**
   * F9, rule 4: the first game without result is shown; column A typed derives column B and Enter moves to the next
   * deal; column B typed is kept and the deal highlighted as inconsistent; a text that is not a number is rejected;
   * the arrows move in a column; the values are saved at once and the list shows the result.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F9: column B is derived from column A, typed values are kept, inconsistent deals highlighted")
  void entryTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, POINTS, "scoresNav");
    await(() -> shown(robot, "a1"));
    final boolean open = robot.lookup("#lockNotice").query().isVisible();
    final String title = text(robot, "sheetTitle");
    final String count = text(robot, "count");
    enter(robot, "a1", "100");
    final String derived = field(robot, "b1").getText();
    final boolean derivedStyle = styled(robot, "b1", ScoresController.DERIVED);
    final String afterEnter = focused(robot);
    enter(robot, "b1", "60");
    final boolean manual = styled(robot, "b1", ScoresController.DERIVED);
    final boolean inconsistent = styled(robot, "a1", ScoresController.INCONSISTENT);
    final String check = text(robot, "check");
    enter(robot, "a2", "abc");
    final String problem = text(robot, "problem");
    final boolean invalid = styled(robot, "a2", ScoresController.INVALID);
    key(robot, field(robot, "a2"), KeyCode.DOWN);
    final String down = focused(robot);
    key(robot, field(robot, "a3"), KeyCode.UP);
    final String up = focused(robot);
    robot.interact(field(robot, "a1")::requestFocus);
    key(robot, field(robot, "a1"), KeyCode.UP);
    key(robot, field(robot, "a1"), KeyCode.SHIFT);
    final String top = focused(robot);
    enter(robot, "b" + LAST, "");
    final String bottom = focused(robot);
    await(() -> ScoreFixtures.stored(folder, POINTS).results().sheet(1, 1).isPresent());
    final DealScore saved = ScoreFixtures.stored(folder, POINTS).results().sheet(1, 1).orElseThrow().deals().getFirst();
    assertAll(
        () -> assertFalse(open),
        () -> assertEquals("Partie 1 · table 1", title),
        () -> assertEquals("Parties avec un résultat : 1 sur 4", count),
        () -> assertEquals("57", derived),
        () -> assertTrue(derivedStyle),
        () -> assertEquals("a2", afterEnter),
        () -> assertFalse(manual),
        () -> assertTrue(inconsistent),
        () -> assertEquals("Donnes 1 : les deux colonnes ne font pas 157 points. Vérifiez la feuille.", check),
        () -> assertEquals(messages.get("error.validation.INVALID_POINTS"), problem),
        () -> assertTrue(invalid),
        () -> assertEquals("a3", down),
        () -> assertEquals("a2", up),
        () -> assertEquals("a1", top),
        () -> assertEquals("b" + LAST, bottom),
        () -> assertEquals(new DealScore(GIVER, RECEIVER), saved),
        () -> assertEquals("Points retenus : 100 – 60", text(robot, "result")),
        () -> assertEquals("100 – 60", String.valueOf(games(robot).getColumns().get(RESULT).getCellData(0))),
        () -> assertEquals("Parties avec un résultat : 2 sur 4", text(robot, "count")));
  }

  /**
   * Rule 16: a sheet over the points of a game shows its subtotals, totals and the deduction of each side.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 16: the excess over 2'512 points is deducted from both sides, half each, rounded up")
  void deductionTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, POINTS, "scoresNav");
    await(() -> shown(robot, "a1"));
    final boolean hidden = robot.lookup("#deductionA").query().isVisible();
    select(robot, 1);
    assertAll(
        () -> assertFalse(hidden),
        () -> assertEquals(messages.number(BLOCK * GIVER), text(robot, "subtotalA1")),
        () -> assertEquals(messages.number(BLOCK * RECEIVER), text(robot, "subtotalB4")),
        () -> assertEquals(messages.number(LAST * GIVER), text(robot, "totalA")),
        () -> assertEquals(messages.number(LAST * RECEIVER), text(robot, "totalB")),
        () -> assertTrue(robot.lookup("#deductionA").query().isVisible()),
        () -> assertEquals("−24", text(robot, "deductionA")),
        () -> assertEquals("−24", text(robot, "deductionB")),
        () -> assertEquals("Points retenus : " + messages.number(LAST * GIVER - DEDUCTION) + " – 936",
                           text(robot, "result")));
  }

  /**
   * F9: a game whose sheet has deals that do not sum to 157 points is marked in the list (warning colour and icon
   * with its description), and the deals stay listed under the sheet; once the sheet is cleared, the mark is gone.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F9: the games whose sheet has inconsistent deals are marked in the list")
  void toCheckTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, POINTS, "scoresNav");
    await(() -> shown(robot, "a1"));
    final int marked = robot.lookup(".table-row-cell.warned").queryAll().size();
    final String tooltip = robot.lookup(".table-row-cell.warned .label").queryLabeled().getTooltip().getText();
    select(robot, 1);
    final String check = text(robot, "check");
    natives.answer(true);
    press(robot, "clearButton");
    await(() -> robot.lookup(".table-row-cell.warned").queryAll().isEmpty());
    assertAll(
        () -> assertEquals(1, marked),
        () -> assertEquals("Feuille à vérifier", tooltip),
        () -> assertTrue(check.startsWith("Donnes 1, 2, 3"), check));
  }

  /**
   * Rule 19: an absent side forfeits, its opponents receive half of the points of a game; the deals cannot be typed;
   * once both sides are present again and nothing is entered, the game has no result.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 19: an absent side gets 0 points and its opponents 1'256")
  void absenceTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, POINTS, "scoresNav");
    await(() -> shown(robot, "a1"));
    select(robot, 2);
    final CheckBox giver = robot.lookup("#giverAbsent").queryAs(CheckBox.class);
    final CheckBox receiver = robot.lookup("#receiverAbsent").queryAs(CheckBox.class);
    robot.interact(giver::fire);
    final String forfeit = text(robot, "result");
    final boolean locked = disabled(robot, "a1");
    final boolean clearable = !disabled(robot, "clearButton");
    robot.interact(receiver::fire);
    final String both = text(robot, "result");
    await(() -> ScoreFixtures.stored(folder, POINTS).results().sheet(2, 1)
                             .filter(sheet -> sheet.absence() == Absence.BOTH).isPresent());
    robot.interact(giver::fire);
    robot.interact(receiver::fire);
    await(() -> ScoreFixtures.stored(folder, POINTS).results().sheet(2, 1).isEmpty());
    assertAll(
        () -> assertEquals("Points retenus : 0 – " + messages.number(FORFEIT) + " (forfait)", forfeit),
        () -> assertTrue(locked),
        () -> assertTrue(clearable),
        () -> assertEquals("Points retenus : 0 – 0 (forfait)", both),
        () -> assertEquals("", text(robot, "result")),
        () -> assertTrue(disabled(robot, "clearButton")),
        () -> assertFalse(disabled(robot, "a1")));
  }

  /**
   * F9: Enter in the list moves to the first deal; Delete clears the sheet after confirmation, nothing without a
   * sheet or when the user cancels; clearing a sheet by its button.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F9: a sheet is cleared after confirmation, from the list or by its button")
  void clearTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, POINTS, "scoresNav");
    await(() -> shown(robot, "a1"));
    key(robot, games(robot), KeyCode.ENTER);
    final String first = focused(robot);
    key(robot, games(robot), KeyCode.DELETE);
    final int noSheet = natives.questions().size();
    select(robot, 1);
    natives.answer(false);
    key(robot, games(robot), KeyCode.DELETE);
    final boolean kept = text(robot, "result").startsWith("Points retenus");
    natives.answer(true);
    press(robot, "clearButton");
    await(() -> ScoreFixtures.stored(folder, POINTS).results().sheets().isEmpty());
    assertAll(
        () -> assertEquals("a1", first),
        () -> assertEquals(0, noSheet),
        () -> assertTrue(kept),
        () -> assertEquals(2, natives.questions().size()),
        () -> assertEquals("", text(robot, "result")),
        () -> assertEquals("La feuille de la partie 1, table 2 a été effacée.", text(robot, "notice")));
  }

  /**
   * F9: the next game without result is selected, going round the list; when every game has a result, the user is
   * told so and the first game is shown.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F9: the tool goes to the next game without result")
  void nextTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, POINTS, "scoresNav");
    await(() -> shown(robot, "a1"));
    press(robot, "nextButton");
    final String second = text(robot, "sheetTitle");
    final String focus = focused(robot);
    press(robot, "nextButton");
    final String third = text(robot, "sheetTitle");
    press(robot, "nextButton");
    final String back = text(robot, "sheetTitle");
    menu(robot, "listItem");
    ScoreFixtures.open(robot, COMPLETE, "scoresNav");
    await(() -> shown(robot, "a1"));
    final String complete = text(robot, "sheetTitle");
    press(robot, "nextButton");
    assertAll(
        () -> assertEquals("Partie 2 · table 1", second),
        () -> assertEquals("a1", focus),
        () -> assertEquals("Partie 2 · table 2", third),
        () -> assertEquals("Partie 1 · table 1", back),
        () -> assertEquals("Partie 1 · table 1", complete),
        () -> assertEquals("Toutes les parties ont un résultat.", text(robot, "notice")));
  }

  /**
   * F9: a value typed then left by going to another view is saved, without showing the edition again; without
   * selection, the panel invites to choose a game.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F9: a value typed is saved when the view is left")
  void leaveTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, POINTS, "scoresNav");
    await(() -> shown(robot, "a1"));
    robot.interact(() -> games(robot).getSelectionModel().clearSelection());
    final boolean choose = robot.lookup("#choose").query().isVisible();
    select(robot, 0);
    final TextField field = field(robot, "a1");
    robot.interact(() -> {
      field.requestFocus();
      field.setText("90");
    });
    menu(robot, "listItem");
    await(() -> ScoreFixtures.stored(folder, POINTS).results().sheet(1, 1).isPresent());
    await(() -> shown(robot, "openButton"));
    assertAll(
        () -> assertTrue(choose),
        () -> assertEquals(DealScore.of(LEFT), ScoreFixtures.stored(folder, POINTS).results().sheet(1, 1)
                                                         .orElseThrow().deals().getFirst()),
        () -> assertFalse(robot.lookup("#scoresNav").queryAs(ToggleButton.class).isVisible()));
  }

  /**
   * Rule 15: once the lots are drawn, the sheets can no longer be changed, and the view says how to correct them.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Rule 15: the drawing of lots closes the score sheets")
  void lockedTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, DRAWN, "scoresNav");
    await(() -> shown(robot, "a1"));
    key(robot, games(robot), KeyCode.DELETE);
    assertAll(
        () -> assertTrue(robot.lookup("#lockNotice").query().isVisible()),
        () -> assertTrue(disabled(robot, "a1")),
        () -> assertTrue(disabled(robot, "b" + LAST)),
        () -> assertTrue(disabled(robot, "giverAbsent")),
        () -> assertTrue(disabled(robot, "receiverAbsent")),
        () -> assertTrue(disabled(robot, "clearButton")),
        () -> assertTrue(natives.questions().isEmpty()),
        () -> assertTrue(ScoreFixtures.stored(folder, DRAWN).results().sheet(1, 1).isPresent()));
  }

  /**
   * F9: before the schedule exists, the scores cannot be entered and the list leads to the registration.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F9: without schedule, the scores view leads to the registration")
  void emptyTest(final FxRobot robot) throws Exception {
    ScoreFixtures.open(robot, OPEN, "scoresNav");
    await(() -> shown(robot, "registrationButton"));
    final boolean next = disabled(robot, "nextButton");
    final String count = text(robot, "count");
    press(robot, "registrationButton");
    await(() -> shown(robot, "addButton"));
    assertAll(
        () -> assertTrue(next),
        () -> assertEquals("Parties avec un résultat : 0 sur 0", count),
        () -> assertTrue(robot.lookup("#registrationNav").queryAs(ToggleButton.class).isSelected()));
  }

}
