package ch.varani.jass.solver;

import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.solver.SolverResult.Reason;
import ch.varani.jass.solver.SolverResult.Unsolved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link IndividualScheduleSolver}: the computed schedules satisfy the rules of the regulation and the
 * requirements of the individual mode, the repeated meetings are minimised, the invalid inputs are rejected, and the
 * infeasibility, the time-out and the cancellation are reported.
 *
 * <p>The tests use small instances, with every number of surplus players, each solved once with the
 * {@link SolverOptions#PARALLEL} options, in a fraction of a second. With several workers, the schedule itself is not
 * reproducible; the tests check its properties, and the optimal values of the objective, which are. The instances of
 * 41 and 100 players (the performance target) take several seconds and are tagged {@code slow}.</p>
 */
final class IndividualScheduleSolverTest {

  /** The number of games of a tournament (rule 1). */
  private static final int GAMES = Regulation.GAMES;
  /** The players at a table. */
  private static final int TABLE = Regulation.PLAYERS_PER_TABLE;
  /** The smallest number of players able to play the games of the regulation, with one surplus player. */
  private static final int SMALLEST_PLAYERS = GAMES + 1;
  /** A number of players filling two tables, too few to avoid every repeated meeting. */
  private static final int TWO_TABLES = 8;
  /** A number of players with two surplus players. */
  private static final int TWO_SURPLUS = 10;
  /** A number of players with one surplus player. */
  private static final int ONE_SURPLUS = 13;
  /** A number of players with three surplus players. */
  private static final int THREE_SURPLUS = 15;
  /** A realistic number of players, filling every table. */
  private static final int REALISTIC_PLAYERS = 24;
  /** The number of players of the fictitious individual edition of the acceptance (phase 14), plus a surplus one. */
  private static final int ACCEPTANCE_PLAYERS = 41;
  /** The number of players of the performance target. */
  private static final int TARGET_PLAYERS = 100;
  /** An odd number of games, which makes the giver/receiver balance impossible. */
  private static final int ODD_GAMES = 3;
  /** The minimum total of repeated meetings with {@link #SMALLEST_PLAYERS} players. */
  private static final int SMALLEST_REPEATED = 10;

  /** The schedules already computed, by number of players, so that each tournament is solved once. */
  private static final Map<Integer, IndividualSchedule> SCHEDULES = new ConcurrentHashMap<>();

  /**
   * Returns the schedule of a tournament solved with the parallel options, solving it on the first call.
   *
   * @param players the number of players.
   *
   * @return the schedule.
   */
  private static IndividualSchedule schedule(final int players) {
    return SCHEDULES.computeIfAbsent(players, IndividualScheduleSolverTest::solve);
  }

  /**
   * Solves a tournament with the parallel options and returns its schedule.
   *
   * @param players the number of players.
   *
   * @return the schedule.
   */
  private static IndividualSchedule solve(final int players) {
    return SolverAssertions.solved(new IndividualScheduleSolver(players, GAMES, SolverOptions.PARALLEL).solve());
  }

  /**
   * Provides the numbers of players of the tested tournaments.
   *
   * @return the numbers of players, with zero to three surplus players.
   */
  static Stream<Arguments> tournaments() {
    return Stream.of(Arguments.of(SMALLEST_PLAYERS), Arguments.of(TWO_TABLES), Arguments.of(TWO_SURPLUS),
                     Arguments.of(ONE_SURPLUS), Arguments.of(THREE_SURPLUS), Arguments.of(REALISTIC_PLAYERS));
  }

  /**
   * Provides the numbers of players of the tested tournaments with surplus players.
   *
   * @return the numbers of players that are not a multiple of the players of a table.
   */
  static Stream<Arguments> surplusTournaments() {
    return Stream.of(Arguments.of(SMALLEST_PLAYERS), Arguments.of(TWO_SURPLUS), Arguments.of(ONE_SURPLUS),
                     Arguments.of(THREE_SURPLUS));
  }

  /**
   * Rule 1: one round per game, plus a catch-up round when there are surplus players.
   *
   * @param players the number of players.
   */
  @ParameterizedTest(name = "{0} players")
  @MethodSource("tournaments")
  @DisplayName("Rule 1: one round per game, plus a catch-up round when there are surplus players")
  void roundsTest(final int players) {
    final IndividualSchedule schedule = schedule(players);
    assertAll(
        () -> assertEquals(players, schedule.players()),
        () -> assertEquals(GAMES, schedule.games()),
        () -> assertEquals(GAMES + (players % TABLE == 0 ? 0 : 1), schedule.rounds().size()),
        () -> assertEquals(IntStream.rangeClosed(1, schedule.rounds().size()).boxed().toList(),
                           schedule.rounds().stream().map(IndividualRound::number).toList()));
  }

  /**
   * Rule 1: each player plays exactly the number of games, at most once per round, with three other players.
   *
   * @param players the number of players.
   */
  @ParameterizedTest(name = "{0} players")
  @MethodSource("tournaments")
  @DisplayName("Rule 1: each player plays every game, at most once per round")
  void gamesTest(final int players) {
    final Map<Integer, Integer> played = new HashMap<>();
    for (final IndividualRound round : schedule(players).rounds()) {
      final Set<Integer> playing = new HashSet<>();
      for (final IndividualGame game : round.games()) {
        for (final int player : players(game)) {
          assertTrue(playing.add(player), () -> "Player " + player + " plays twice in round " + round.number());
          played.merge(player, 1, Integer::sum);
        }
      }
    }
    for (int player = 1; player <= players; player++) {
      assertEquals(GAMES, played.getOrDefault(player, 0), "Player " + player + " does not play every game");
    }
  }

  /**
   * Rule 1, applied to the players: the surplus players sit out each of the first rounds, never the same twice, and
   * the catch-up round is played by these players only.
   *
   * @param players the number of players, not a multiple of the players of a table.
   */
  @ParameterizedTest(name = "{0} players")
  @MethodSource("surplusTournaments")
  @DisplayName("Rule 1: the catch-up round is played by the players who sat out the first rounds")
  void catchUpRoundTest(final int players) {
    final IndividualSchedule schedule = schedule(players);
    final Set<Integer> satOut = new HashSet<>();
    for (final IndividualRound round : schedule.rounds().subList(0, GAMES)) {
      final Set<Integer> absent = new HashSet<>(IntStream.rangeClosed(1, players).boxed().toList());
      absent.removeAll(playing(round));
      assertEquals(players % TABLE, absent.size(), () -> "Wrong number of players sitting out " + round.number());
      assertTrue(satOut.addAll(absent), () -> "A player sits out twice in round " + round.number());
    }
    assertEquals(satOut, playing(schedule.rounds().get(GAMES)));
  }

  /**
   * Two players are partners at most once in the whole tournament, and a team has two different players.
   *
   * @param players the number of players.
   */
  @ParameterizedTest(name = "{0} players")
  @MethodSource("tournaments")
  @DisplayName("Two players are partners at most once in the tournament")
  void partnersTest(final int players) {
    final Set<Partners> teams = new HashSet<>();
    for (final IndividualRound round : schedule(players).rounds()) {
      for (final IndividualGame game : round.games()) {
        for (final Partners team : List.of(game.giver(), game.receiver())) {
          assertTrue(team.first() < team.second(), team::toString);
          assertTrue(teams.add(team), () -> team + " are partners twice");
        }
      }
    }
  }

  /**
   * Rule 6: the giver sits at the smaller place of its table; the tables of a round are numbered from 1.
   *
   * @param players the number of players.
   */
  @ParameterizedTest(name = "{0} players")
  @MethodSource("tournaments")
  @DisplayName("Rule 6: the giver sits at the smaller place, tables numbered from 1")
  void placesTest(final int players) {
    for (final IndividualRound round : schedule(players).rounds()) {
      final List<IndividualGame> games = round.games();
      assertEquals(IntStream.rangeClosed(1, games.size()).boxed().toList(),
                   games.stream().map(IndividualGame::table).toList());
      for (final IndividualGame game : games) {
        assertTrue(game.giverPlace() < game.receiverPlace(), game::toString);
      }
    }
  }

  /**
   * Rule 6: each player deals first in half of its games and receives in the other half.
   *
   * @param players the number of players.
   */
  @ParameterizedTest(name = "{0} players")
  @MethodSource("tournaments")
  @DisplayName("Rule 6: each player gives in half of its games and receives in the other half")
  void giversTest(final int players) {
    final Map<Integer, Integer> given = new HashMap<>();
    for (final IndividualRound round : schedule(players).rounds()) {
      for (final IndividualGame game : round.games()) {
        given.merge(game.giver().first(), 1, Integer::sum);
        given.merge(game.giver().second(), 1, Integer::sum);
      }
    }
    for (int player = 1; player <= players; player++) {
      assertEquals(GAMES / 2, given.getOrDefault(player, 0), "Player " + player + " does not give half of its games");
    }
  }

  /**
   * With enough players, no two players face each other twice: the maximum number of meetings is 1.
   *
   * @param players the number of players.
   */
  @ParameterizedTest(name = "{0} players")
  @MethodSource("enoughPlayers")
  @DisplayName("With enough players, two players never face each other twice")
  void noRepeatedMeetingTest(final int players) {
    final Map<Set<Integer>, Integer> meetings = meetings(schedule(players));
    assertAll(
        () -> assertEquals(1, maximum(meetings)),
        () -> assertEquals(0, repeated(meetings)));
  }

  /**
   * Provides the numbers of players with which no two players face each other twice.
   *
   * @return the numbers of players of the tested tournaments, from 10 players.
   */
  static Stream<Arguments> enoughPlayers() {
    return Stream.of(Arguments.of(TWO_SURPLUS), Arguments.of(ONE_SURPLUS), Arguments.of(THREE_SURPLUS),
                     Arguments.of(REALISTIC_PLAYERS));
  }

  /**
   * With few players, repeated meetings cannot be avoided; the solver minimises the maximum number of meetings, then
   * the total number of repeated meetings. The optimal values are proven by the solver, so they do not depend on the
   * workers.
   */
  @Test
  @DisplayName("With few players, the maximum and then the total of the repeated meetings are minimised")
  void minimisedMeetingsTest() {
    final Map<Set<Integer>, Integer> smallest = meetings(schedule(SMALLEST_PLAYERS));
    final Map<Set<Integer>, Integer> twoTables = meetings(schedule(TWO_TABLES));
    assertAll(
        () -> assertEquals(2, maximum(smallest)),
        () -> assertEquals(SMALLEST_REPEATED, repeated(smallest)),
        () -> assertEquals(2, maximum(twoTables)),
        () -> assertEquals(TWO_TABLES, repeated(twoTables)));
  }

  /**
   * Performance target of the development plan: 100 players are solved, without any repeated meeting, within the
   * default time limit; and 41 players, the size of the acceptance edition.
   */
  @Test
  @Tag("slow")
  @DisplayName("100 players are solved within the time limit, without repeated meeting")
  void performanceTargetTest() {
    for (final int players : List.of(ACCEPTANCE_PLAYERS, TARGET_PLAYERS)) {
      final Map<Set<Integer>, Integer> meetings = meetings(schedule(players));
      assertAll(
          () -> assertEquals(1, maximum(meetings)),
          () -> assertEquals(0, repeated(meetings)));
    }
  }

  /**
   * An odd number of games cannot be split equally between giving and receiving.
   */
  @Test
  @DisplayName("An odd number of games has no schedule")
  void oddGamesTest() {
    assertEquals(new Unsolved<>(Reason.ODD_GAMES),
                 new IndividualScheduleSolver(REALISTIC_PLAYERS, ODD_GAMES, SolverOptions.DEFAULT).solve());
  }

  /**
   * Fewer players than a table, or not more players than games, cannot give a different partner in each game.
   */
  @Test
  @DisplayName("Fewer players than a table or than games plus one has no schedule")
  void notEnoughPlayersTest() {
    assertAll(
        () -> assertEquals(new Unsolved<>(Reason.NOT_ENOUGH_PLAYERS),
                           new IndividualScheduleSolver(TABLE - 1, 2, SolverOptions.DEFAULT).solve()),
        () -> assertEquals(new Unsolved<>(Reason.NOT_ENOUGH_PLAYERS),
                           new IndividualScheduleSolver(GAMES, GAMES, SolverOptions.DEFAULT).solve()));
  }

  /**
   * The surplus players must fill the tables of the catch-up round, and sit out once only.
   */
  @Test
  @DisplayName("Rule 1: surplus players unable to form a catch-up round have no schedule")
  void noCatchUpRoundTest() {
    assertAll(
        () -> assertEquals(new Unsolved<>(Reason.NO_CATCH_UP_ROUND),
                           new IndividualScheduleSolver(TABLE + 2, GAMES, SolverOptions.DEFAULT).solve()),
        () -> assertEquals(new Unsolved<>(Reason.NO_CATCH_UP_ROUND),
                           new IndividualScheduleSolver(TABLE + 1, 2, SolverOptions.DEFAULT).solve()));
  }

  /**
   * Four players and two games: the two players who did not give in the first game must form the giver team of the
   * second, but they were already partners.
   */
  @Test
  @DisplayName("An infeasible tournament is reported")
  void infeasibleTest() {
    assertEquals(new Unsolved<>(Reason.INFEASIBLE),
                 new IndividualScheduleSolver(TABLE, 2, SolverOptions.DEFAULT).solve());
  }

  /**
   * Less than one game and missing options are rejected.
   */
  @Test
  @DisplayName("Less than one game and missing options are rejected")
  void invalidArgumentsTest() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class,
                           () -> new IndividualScheduleSolver(REALISTIC_PLAYERS, 0, SolverOptions.DEFAULT)),
        () -> assertThrows(NullPointerException.class,
                           () -> new IndividualScheduleSolver(REALISTIC_PLAYERS, GAMES, null)));
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
                 new IndividualScheduleSolver(REALISTIC_PLAYERS, GAMES, options).solve());
  }

  /**
   * A cancelled solving is reported as such.
   */
  @Test
  @DisplayName("A cancelled solving is reported")
  void cancelTest() {
    final IndividualScheduleSolver solver = new IndividualScheduleSolver(TWO_TABLES, GAMES, SolverOptions.DEFAULT);
    solver.cancel();
    assertEquals(new Unsolved<>(Reason.CANCELLED), solver.solve());
  }

  /**
   * Returns the four players of a game.
   *
   * @param game the game.
   *
   * @return the numbers of the givers and of the receivers.
   */
  private static List<Integer> players(final IndividualGame game) {
    return List.of(game.giver().first(), game.giver().second(), game.receiver().first(), game.receiver().second());
  }

  /**
   * Returns the players playing in a round.
   *
   * @param round the round.
   *
   * @return the numbers of the players of every game of the round.
   */
  private static Set<Integer> playing(final IndividualRound round) {
    final Set<Integer> playing = new HashSet<>();
    for (final IndividualGame game : round.games()) {
      playing.addAll(players(game));
    }
    return playing;
  }

  /**
   * Counts the meetings of each pair of players, i.e. the games in which they are opponents.
   *
   * @param schedule the schedule.
   *
   * @return the number of meetings of each pair of players that faced each other at least once.
   */
  private static Map<Set<Integer>, Integer> meetings(final IndividualSchedule schedule) {
    final Map<Set<Integer>, Integer> meetings = new HashMap<>();
    for (final IndividualRound round : schedule.rounds()) {
      for (final IndividualGame game : round.games()) {
        for (final int giver : List.of(game.giver().first(), game.giver().second())) {
          for (final int receiver : List.of(game.receiver().first(), game.receiver().second())) {
            meetings.merge(Set.of(giver, receiver), 1, Integer::sum);
          }
        }
      }
    }
    return meetings;
  }

  /**
   * Returns the maximum number of meetings of two players.
   *
   * @param meetings the meetings of each pair of players.
   *
   * @return the maximum number of meetings.
   */
  private static int maximum(final Map<Set<Integer>, Integer> meetings) {
    return meetings.values().stream().mapToInt(Integer::intValue).max().orElse(0);
  }

  /**
   * Returns the total number of repeated meetings, i.e. the meetings of each pair beyond the first one.
   *
   * @param meetings the meetings of each pair of players.
   *
   * @return the total number of repeated meetings.
   */
  private static int repeated(final Map<Set<Integer>, Integer> meetings) {
    return meetings.values().stream().mapToInt(count -> count - 1).sum();
  }

}
