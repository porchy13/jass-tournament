package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.solver.SolverOptions;
import ch.varani.jass.solver.SolverResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests of {@link Solving}: the solver of the mode of the edition computes the schedule, and can be cancelled.
 */
final class SolvingTest {

  /** The date of the fictitious editions. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** A number of participants enough for the games of the tests. */
  private static final int FIVE = 5;
  /** The number of players of a full individual edition of two games. */
  private static final int EIGHT = 8;

  /**
   * Rule 1: a team edition is solved by the team solver, an individual edition by the individual solver.
   */
  @Test
  @DisplayName("Rule 1: each mode is solved by its solver")
  void solveTest() {
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 1; i <= FIVE; i++) {
      teams = teams.add(new Team(new Player("A" + i, ""), new Player("B" + i, "")));
    }
    final List<Player> players = new ArrayList<>();
    for (int i = 1; i <= EIGHT; i++) {
      players.add(new Player("P" + i, ""));
    }
    final Edition team = Edition.create("Match", DATE, TournamentMode.TEAM).withRegistrations(teams);
    final Edition individual = Edition.create("Match", DATE, TournamentMode.INDIVIDUAL).withGames(2)
                                      .withRegistrations(new IndividualRegistrations(players));
    final SolverResult<?> teamResult = Solving.of(team).solve();
    final SolverResult<?> individualResult = Solving.of(individual).solve();
    assertAll(
        () -> assertEquals(FIVE, ((SolverResult.Solved<?>) teamResult).schedule().participants()),
        () -> assertEquals(EIGHT, ((SolverResult.Solved<?>) individualResult).schedule().participants()));
  }

  /**
   * A cancelled computation reports its cancellation.
   */
  @Test
  @DisplayName("A cancelled computation is reported")
  void cancelTest() {
    final Solving solving = Solving.of(Edition.create("Match", DATE, TournamentMode.TEAM));
    solving.cancel();
    assertInstanceOf(SolverResult.Unsolved.class, solving.solve());
  }

  /**
   * The time limit shown as the progress of the computation is the one of the solver; a computation that does not
   * tell its own has the default one.
   */
  @Test
  @DisplayName("The time limit of the computation is the one of the solver")
  void timeLimitTest() {
    final Solving solver = Solving.of(Edition.create("Match", DATE, TournamentMode.INDIVIDUAL));
    final Solving other = new Solving() {

      /**
       * Returns no schedule.
       *
       * @return a cancelled result.
       */
      @Override
      public SolverResult<?> solve() {
        return new SolverResult.Unsolved<>(SolverResult.Reason.CANCELLED);
      }

      /**
       * Does nothing.
       */
      @Override
      public void cancel() {
        // Nothing to stop.
      }

    };
    assertAll(
        () -> assertEquals(SolverOptions.PARALLEL.timeLimit(), solver.timeLimit()),
        () -> assertEquals(SolverOptions.DEFAULT_TIME_LIMIT, other.timeLimit()),
        () -> assertInstanceOf(SolverResult.Unsolved.class, other.solve()));
  }

}
