package ch.varani.jass.solver;

import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.solver.SolverResult.Reason;
import ch.varani.jass.solver.SolverResult.Unsolved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link TeamScheduleSolver}: the computed schedules satisfy the rules of the regulation, the invalid inputs
 * are rejected, and the time-out and the cancellation are reported.
 *
 * <p>The tests use small instances (even and odd numbers of teams) and realistic instances of 20 and 21 teams, each
 * solved once, in a fraction of a second, with the fixed seed and number of workers of {@link SolverOptions#DEFAULT}.
 * </p>
 */
final class TeamScheduleSolverTest {

  /** The number of games of a tournament (rule 1). */
  private static final int GAMES = Regulation.GAMES;
  /** An even number of teams, solved quickly. */
  private static final int EVEN_TEAMS = 6;
  /** An odd number of teams, solved quickly, which requires a catch-up round. */
  private static final int ODD_TEAMS = 7;
  /** A realistic even number of teams. */
  private static final int REALISTIC_EVEN_TEAMS = 20;
  /** A realistic odd number of teams. */
  private static final int REALISTIC_ODD_TEAMS = 21;
  /** The smallest number of teams able to play the games of the regulation, odd since the games are even. */
  private static final int SMALLEST_TEAMS = GAMES + 1;
  /** An odd number of games, which makes the giver/receiver balance impossible. */
  private static final int ODD_GAMES = 3;

  /** The schedules already computed, by number of teams, so that each tournament is solved once. */
  private static final Map<Integer, TeamSchedule> SCHEDULES = new ConcurrentHashMap<>();

  /**
   * Returns the schedule of a tournament solved with the default options, solving it on the first call.
   *
   * @param teams the number of teams.
   *
   * @return the schedule.
   */
  private static TeamSchedule schedule(final int teams) {
    return SCHEDULES.computeIfAbsent(teams, TeamScheduleSolverTest::solve);
  }

  /**
   * Solves a tournament with the default options and returns its schedule.
   *
   * @param teams the number of teams.
   *
   * @return the schedule.
   */
  private static TeamSchedule solve(final int teams) {
    return SolverAssertions.solved(new TeamScheduleSolver(teams, GAMES, SolverOptions.DEFAULT).solve());
  }

  /**
   * Provides the numbers of teams of the tested tournaments.
   *
   * @return the numbers of teams, even and odd.
   */
  static Stream<Arguments> tournaments() {
    return Stream.of(Arguments.of(SMALLEST_TEAMS), Arguments.of(EVEN_TEAMS), Arguments.of(ODD_TEAMS),
                     Arguments.of(REALISTIC_EVEN_TEAMS), Arguments.of(REALISTIC_ODD_TEAMS));
  }

  /**
   * Provides the odd numbers of teams of the tested tournaments.
   *
   * @return the odd numbers of teams.
   */
  static Stream<Arguments> oddTournaments() {
    return Stream.of(Arguments.of(SMALLEST_TEAMS), Arguments.of(ODD_TEAMS), Arguments.of(REALISTIC_ODD_TEAMS));
  }

  /**
   * Rule 1: the number of rounds is the number of games, plus a catch-up round when the number of teams is odd.
   *
   * @param teams the number of teams.
   */
  @ParameterizedTest(name = "{0} teams")
  @MethodSource("tournaments")
  @DisplayName("Rule 1: one round per game, plus a catch-up round when the number of teams is odd")
  void roundsTest(final int teams) {
    final TeamSchedule schedule = schedule(teams);
    assertAll(
        () -> assertEquals(teams, schedule.teams()),
        () -> assertEquals(GAMES, schedule.games()),
        () -> assertEquals(GAMES + teams % 2, schedule.rounds().size()),
        () -> assertEquals(IntStream.rangeClosed(1, schedule.rounds().size()).boxed().toList(),
                           schedule.rounds().stream().map(TeamRound::number).toList()));
  }

  /**
   * Rule 1: each team plays exactly the number of games, and at most once per round.
   *
   * @param teams the number of teams.
   */
  @ParameterizedTest(name = "{0} teams")
  @MethodSource("tournaments")
  @DisplayName("Rule 1: each team plays every game, at most once per round")
  void gamesTest(final int teams) {
    final TeamSchedule schedule = schedule(teams);
    final Map<Integer, Integer> played = new HashMap<>();
    for (final TeamRound round : schedule.rounds()) {
      final Set<Integer> playing = new HashSet<>();
      for (final TeamGame game : round.games()) {
        assertTrue(playing.add(game.giver()), () -> "Team " + game.giver() + " plays twice in round " + round);
        assertTrue(playing.add(game.receiver()), () -> "Team " + game.receiver() + " plays twice in round " + round);
        played.merge(game.giver(), 1, Integer::sum);
        played.merge(game.receiver(), 1, Integer::sum);
      }
    }
    for (int team = 1; team <= teams; team++) {
      assertEquals(GAMES, played.getOrDefault(team, 0), "Team " + team + " does not play every game");
    }
  }

  /**
   * Rule 1: with an odd number of teams, exactly one team sits out each of the first rounds, never the same twice, and
   * the catch-up round is played by these teams only.
   *
   * @param teams the odd number of teams.
   */
  @ParameterizedTest(name = "{0} teams")
  @MethodSource("oddTournaments")
  @DisplayName("Rule 1: the catch-up round is played by the teams that sat out the first rounds")
  void catchUpRoundTest(final int teams) {
    final TeamSchedule schedule = schedule(teams);
    final Set<Integer> satOut = new HashSet<>();
    for (final TeamRound round : schedule.rounds().subList(0, GAMES)) {
      final Set<Integer> absent = new HashSet<>(IntStream.rangeClosed(1, teams).boxed().toList());
      absent.removeAll(playing(round));
      assertEquals(1, absent.size(), () -> "One team must sit out round " + round.number());
      assertTrue(satOut.addAll(absent), () -> "A team sits out twice in round " + round.number());
    }
    assertEquals(satOut, playing(schedule.rounds().get(GAMES)));
  }

  /**
   * A team never plays against itself and meets another team at most once.
   *
   * @param teams the number of teams.
   */
  @ParameterizedTest(name = "{0} teams")
  @MethodSource("tournaments")
  @DisplayName("A team never plays itself and meets another team at most once")
  void opponentsTest(final int teams) {
    final Set<Set<Integer>> pairs = new HashSet<>();
    for (final TeamRound round : schedule(teams).rounds()) {
      for (final TeamGame game : round.games()) {
        assertNotEquals(game.giver(), game.receiver());
        assertTrue(pairs.add(Set.of(game.giver(), game.receiver())), () -> game + " is played twice");
      }
    }
  }

  /**
   * Rule 6: the giver sits at the smaller place of its table; the tables of a round are numbered from 1, so that the
   * places of a round are 1 to twice the number of games.
   *
   * @param teams the number of teams.
   */
  @ParameterizedTest(name = "{0} teams")
  @MethodSource("tournaments")
  @DisplayName("Rule 6: the giver sits at the smaller place, tables numbered from 1")
  void placesTest(final int teams) {
    for (final TeamRound round : schedule(teams).rounds()) {
      final List<TeamGame> games = round.games();
      assertEquals(IntStream.rangeClosed(1, games.size()).boxed().toList(),
                   games.stream().map(TeamGame::table).toList());
      for (final TeamGame game : games) {
        assertTrue(game.giverPlace() < game.receiverPlace(), game::toString);
      }
    }
  }

  /**
   * Rule 6: each team deals first in half of its games and receives in the other half.
   *
   * @param teams the number of teams.
   */
  @ParameterizedTest(name = "{0} teams")
  @MethodSource("tournaments")
  @DisplayName("Rule 6: each team gives in half of its games and receives in the other half")
  void giversTest(final int teams) {
    final Map<Integer, Integer> given = new HashMap<>();
    final Map<Integer, Integer> received = new HashMap<>();
    for (final TeamRound round : schedule(teams).rounds()) {
      for (final TeamGame game : round.games()) {
        given.merge(game.giver(), 1, Integer::sum);
        received.merge(game.receiver(), 1, Integer::sum);
      }
    }
    for (int team = 1; team <= teams; team++) {
      assertEquals(GAMES / 2, given.getOrDefault(team, 0), "Team " + team + " does not give half of its games");
      assertEquals(GAMES / 2, received.getOrDefault(team, 0), "Team " + team + " does not receive half of its games");
    }
  }

  /**
   * The same input and options give the same schedule.
   */
  @Test
  @DisplayName("The solving is reproducible")
  void reproducibleTest() {
    assertEquals(schedule(REALISTIC_ODD_TEAMS), solve(REALISTIC_ODD_TEAMS));
  }

  /**
   * An odd number of games cannot be split equally between giving and receiving.
   */
  @Test
  @DisplayName("An odd number of games has no schedule")
  void oddGamesTest() {
    assertEquals(new Unsolved<>(Reason.ODD_GAMES),
                 new TeamScheduleSolver(EVEN_TEAMS, ODD_GAMES, SolverOptions.DEFAULT).solve());
  }

  /**
   * With fewer opponents than games, a team would meet an opponent twice.
   */
  @Test
  @DisplayName("Not more teams than games has no schedule")
  void notEnoughTeamsTest() {
    assertAll(
        () -> assertEquals(new Unsolved<>(Reason.NOT_ENOUGH_TEAMS),
                           new TeamScheduleSolver(GAMES, GAMES, SolverOptions.DEFAULT).solve()),
        () -> assertEquals(new Unsolved<>(Reason.NOT_ENOUGH_TEAMS),
                           new TeamScheduleSolver(0, GAMES, SolverOptions.DEFAULT).solve()));
  }

  /**
   * Less than one game and missing options are rejected.
   */
  @Test
  @DisplayName("Less than one game and missing options are rejected")
  void invalidArgumentsTest() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new TeamScheduleSolver(EVEN_TEAMS, 0,
                                                                                         SolverOptions.DEFAULT)),
        () -> assertThrows(NullPointerException.class, () -> new TeamScheduleSolver(EVEN_TEAMS, GAMES, null)));
  }

  /**
   * A time limit too short to find a schedule is reported.
   */
  @Test
  @DisplayName("Reaching the time limit is reported")
  void timeLimitTest() {
    final SolverOptions options = new SolverOptions(SolverOptions.DEFAULT_RANDOM_SEED, SolverOptions.DEFAULT_WORKERS,
                                                    Duration.ofNanos(1));
    assertEquals(new Unsolved<>(Reason.TIME_LIMIT),
                 new TeamScheduleSolver(REALISTIC_EVEN_TEAMS, GAMES, options).solve());
  }

  /**
   * A cancelled solving is reported as such.
   */
  @Test
  @DisplayName("A cancelled solving is reported")
  void cancelTest() {
    final TeamScheduleSolver solver = new TeamScheduleSolver(EVEN_TEAMS, GAMES, SolverOptions.DEFAULT);
    solver.cancel();
    assertEquals(new Unsolved<>(Reason.CANCELLED), solver.solve());
  }

  /**
   * Returns the teams playing in a round.
   *
   * @param round the round.
   *
   * @return the numbers of the givers and receivers of the round.
   */
  private static Set<Integer> playing(final TeamRound round) {
    final Set<Integer> playing = new HashSet<>();
    for (final TeamGame game : round.games()) {
      playing.add(game.giver());
      playing.add(game.receiver());
    }
    return playing;
  }

}
