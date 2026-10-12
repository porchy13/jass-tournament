package ch.varani.jass.app;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.EditionStore;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;

/**
 * The editions of the scenarios of the scores and of the ranking (phase 11): four fictitious teams playing two games,
 * with a schedule written by hand, and the helpers to open them and read them back from the data folder.
 *
 * <p>The schedule: round 1, table 1 teams 1 and 2, table 2 teams 3 and 4; round 2, table 1 teams 1 and 3, table 2
 * teams 2 and 4; the giver first.</p>
 */
final class ScoreFixtures {

  /** The number of games of the editions. */
  static final int GAMES = 2;
  /** The number of teams of the editions. */
  static final int TEAMS = 4;
  /** The team 3. */
  static final int THIRD = 3;

  /**
   * Prevents the instantiation of this utility class.
   */
  private ScoreFixtures() {
  }

  /**
   * Creates an edition of four teams with the schedule and without result.
   *
   * @param name the name of the edition.
   *
   * @return the edition.
   */
  static Edition scheduled(final String name) {
    return registered(name).withSchedule(new TeamSchedule(TEAMS, GAMES, List.of(
        new TeamRound(1, List.of(new TeamGame(1, 1, 2), new TeamGame(2, THIRD, TEAMS))),
        new TeamRound(2, List.of(new TeamGame(1, 1, THIRD), new TeamGame(2, 2, TEAMS))))));
  }

  /**
   * Creates an edition of four teams without schedule.
   *
   * @param name the name of the edition.
   *
   * @return the edition.
   */
  static Edition registered(final String name) {
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 1; i <= TEAMS; i++) {
      teams = teams.add(new Team(new Player("Emma" + i, "Favre"), new Player("Félix" + i, "Girard")));
    }
    return Edition.create(name, UiSupport.TODAY, TournamentMode.TEAM).withGames(GAMES).withRegistrations(teams);
  }

  /**
   * Creates the sheet of a game where every deal has the same points, column B derived.
   *
   * @param round the round.
   * @param table the table.
   * @param giver the points of column A of every deal.
   *
   * @return the sheet.
   */
  static ScoreSheet sheet(final int round, final int table, final int giver) {
    return sheet(round, table, DealScore.of(giver));
  }

  /**
   * Creates the sheet of a game where every deal has the same points.
   *
   * @param round the round.
   * @param table the table.
   * @param score the points of every deal.
   *
   * @return the sheet.
   */
  static ScoreSheet sheet(final int round, final int table, final DealScore score) {
    return new ScoreSheet(round, table, Collections.nCopies(DealPlan.REGULATION.size(), score), Absence.NONE);
  }

  /**
   * Gives the results of every game of a scheduled edition: every deal of the game with the same points in column A.
   *
   * @param edition the scheduled edition.
   * @param givers the points of column A in round 1 table 1, round 1 table 2, round 2 table 1, round 2 table 2.
   *
   * @return the edition with its results.
   */
  static Edition played(final Edition edition, final int... givers) {
    Results results = Results.EMPTY;
    int index = 0;
    for (int round = 1; round <= GAMES; round++) {
      for (int table = 1; table <= TEAMS / 2; table++) {
        results = results.withSheet(sheet(round, table, givers[index++]));
      }
    }
    return edition.withResults(results);
  }

  /**
   * Opens an edition from the start screen, then shows one of its parts.
   *
   * @param robot the TestFX robot.
   * @param name the name of the edition.
   * @param part the identifier of the item of the side navigation.
   *
   * @throws Exception if the edition is not opened in time.
   */
  @SuppressWarnings("unchecked")
  static void open(final FxRobot robot, final String name, final String part) throws Exception {
    await(() -> shown(robot, "openButton"));
    final TableView<EditionHeader> table = robot.lookup("#table").queryAs(TableView.class);
    robot.interact(() -> table.getSelectionModel().select(table.getItems().stream()
        .filter(header -> header.name().equals(name)).findFirst().orElseThrow()));
    press(robot, "openButton");
    await(() -> shown(robot, "addButton"));
    final ToggleButton item = robot.lookup("#" + part).queryAs(ToggleButton.class);
    robot.interact(item::fire);
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Reads an edition back from the data folder.
   *
   * @param folder the data folder.
   * @param name the name of the edition.
   *
   * @return the edition as saved.
   *
   * @throws Exception if it cannot be read.
   */
  static Edition stored(final Path folder, final String name) throws Exception {
    final EditionStore store = new EditionStore(folder);
    return store.open(store.list().editions().stream().filter(header -> header.name().equals(name)).findFirst()
                           .orElseThrow().id());
  }

}
