package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.scoring.Ranking;
import ch.varani.jass.scoring.RankingEntry;
import ch.varani.jass.scoring.Scoring;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the content of the external display without the toolkit: rounds, places, round in progress, countdown,
 * pages and final ranking revealed.
 */
final class DisplayModelTest {

  /** The start signal of the countdown in the tests. */
  private static final Instant START = Instant.parse("2026-11-20T14:00:00Z");
  /** The points of column A of every deal of the games with a result. */
  private static final int GIVER = 80;
  /** The time of a game in the tests, in minutes. */
  private static final int MINUTES = 75;
  /** The players of the individual edition: one table, plus one sitting out. */
  private static final int PLAYERS = 5;
  /** The number of lines of the pages in the tests. */
  private static final int PAGE = 3;
  /** The number of lines of the paged list in the tests. */
  private static final int LINES = 7;
  /** Half a minute, in seconds. */
  private static final int HALF_MINUTE = 30;

  /**
   * The places follow rule 6 (giver at the odd place) and the participants sitting out have none.
   */
  @Test
  @DisplayName("Rule 6: the giver sits at place 2k − 1 and the receiver at place 2k of table k")
  void placesTest() {
    final Edition edition = ScoreFixtures.scheduled("Places");
    assertAll(
        () -> assertEquals(List.of(1, 2), DisplayModel.rounds(edition)),
        () -> assertEquals(List.of(new DisplayModel.PlaceLine(1, 1, 1),
                                   new DisplayModel.PlaceLine(2, 2, ScoreFixtures.THIRD),
                                   new DisplayModel.PlaceLine(ScoreFixtures.THIRD, 1, 2),
                                   new DisplayModel.PlaceLine(ScoreFixtures.TEAMS, 2, ScoreFixtures.TEAMS)),
                           DisplayModel.places(edition, 2)),
        () -> assertEquals(List.of(), DisplayModel.rounds(ScoreFixtures.registered("Open"))));
  }

  /**
   * In individual mode, each player has the place of its side; a player without game sits out.
   */
  @Test
  @DisplayName("F12: each player has the place of its side, the surplus players sit out")
  void individualPlacesTest() {
    final List<Player> players = new ArrayList<>();
    for (int i = 1; i <= PLAYERS; i++) {
      players.add(new Player("Joueur" + i, "Test"));
    }
    final Edition edition = Edition.create("Individuel", UiSupport.TODAY, TournamentMode.INDIVIDUAL).withGames(1)
        .withRegistrations(new IndividualRegistrations(players))
        .withSchedule(new IndividualSchedule(PLAYERS, 1, List.of(new IndividualRound(1, List.of(
            new IndividualGame(1, new Partners(1, 2), new Partners(ScoreFixtures.THIRD, ScoreFixtures.TEAMS)))))));
    assertEquals(List.of(new DisplayModel.PlaceLine(1, 1, 1), new DisplayModel.PlaceLine(2, 1, 1),
                         new DisplayModel.PlaceLine(ScoreFixtures.THIRD, 1, 2),
                         new DisplayModel.PlaceLine(ScoreFixtures.TEAMS, 1, 2),
                         new DisplayModel.PlaceLine(PLAYERS, 0, 0)),
                 DisplayModel.places(edition, 1));
  }

  /**
   * The round in progress is the first one with a game without result, the last one when every game has one.
   */
  @Test
  @DisplayName("F18: the round in progress is the first round with a game without result")
  void currentRoundTest() {
    final Edition edition = ScoreFixtures.scheduled("Rounds");
    final Edition first = edition.withResults(Results.EMPTY.withSheet(ScoreFixtures.sheet(1, 1, GIVER)));
    final Edition second = first.withResults(first.results().withSheet(ScoreFixtures.sheet(1, 2, GIVER)));
    assertAll(
        () -> assertEquals(1, DisplayModel.currentRound(ScoreFixtures.registered("Open"))),
        () -> assertEquals(1, DisplayModel.currentRound(edition)),
        () -> assertEquals(1, DisplayModel.currentRound(first)),
        () -> assertEquals(2, DisplayModel.currentRound(second)),
        () -> assertEquals(2, DisplayModel.currentRound(
            ScoreFixtures.played(edition, GIVER, GIVER, GIVER, GIVER))));
  }

  /**
   * Q21: the countdown shows the whole time before the start signal, then the time remaining, never below zero.
   */
  @Test
  @DisplayName("Q21: the countdown starts at the signal and stops at zero")
  void countdownTest() {
    assertAll(
        () -> assertEquals(Duration.ofMinutes(MINUTES), DisplayModel.remaining(null, MINUTES, START)),
        () -> assertEquals("75:00", DisplayModel.clock(DisplayModel.remaining(null, MINUTES, START))),
        () -> assertEquals("74:30", DisplayModel.clock(DisplayModel.remaining(START, MINUTES,
                                                                              START.plusSeconds(HALF_MINUTE)))),
        () -> assertEquals("00:01", DisplayModel.clock(DisplayModel.remaining(
            START, MINUTES, START.plus(Duration.ofMinutes(MINUTES)).minusSeconds(1)))),
        () -> assertEquals(Duration.ZERO, DisplayModel.remaining(START, MINUTES,
                                                                 START.plus(Duration.ofHours(2)))),
        () -> assertEquals("00:00", DisplayModel.clock(Duration.ZERO)));
  }

  /**
   * Q22: the lines that do not fit are cut into pages that alternate every ten seconds.
   */
  @Test
  @DisplayName("Q22: the pages alternate every ten seconds")
  void pagesTest() {
    final List<Integer> lines = IntStream.rangeClosed(1, LINES).boxed().toList();
    final Instant epoch = Instant.EPOCH;
    final long step = DisplayModel.PAGE_SECONDS;
    assertAll(
        () -> assertEquals(new DisplayModel.Page<>(lines, 1, 1), DisplayModel.page(lines, LINES, epoch)),
        () -> assertEquals(new DisplayModel.Page<>(lines.subList(0, PAGE), 1, PAGE),
                           DisplayModel.page(lines, PAGE, epoch.plusSeconds(step - 1))),
        () -> assertEquals(new DisplayModel.Page<>(lines.subList(PAGE, 2 * PAGE), 2, PAGE),
                           DisplayModel.page(lines, PAGE, epoch.plusSeconds(step))),
        () -> assertEquals(new DisplayModel.Page<>(lines.subList(2 * PAGE, LINES), PAGE, PAGE),
                           DisplayModel.page(lines, PAGE, epoch.plusSeconds(2 * step))),
        () -> assertEquals(new DisplayModel.Page<>(lines.subList(0, PAGE), 1, PAGE),
                           DisplayModel.page(lines, PAGE, epoch.plusSeconds(PAGE * step))));
  }

  /**
   * F18: the final ranking is revealed from the last line, shown the best first, once it is final.
   */
  @Test
  @DisplayName("F18: the final ranking is revealed from the last line to the first")
  void revealedTest() {
    final Edition played = ScoreFixtures.played(ScoreFixtures.scheduled("Final"), 100, 90, GIVER, 70);
    final Ranking ranking = new Scoring(played).ranking();
    final List<RankingEntry> entries = ranking.entries();
    final Edition tied = ScoreFixtures.played(ScoreFixtures.scheduled("Tied"), 79, 79, 79, 79);
    final Edition drawn = tied.withResults(tied.results().withTieBreak(new Scoring(tied).drawLots(1)));
    assertAll(
        () -> assertTrue(DisplayModel.isFinal(ranking)),
        () -> assertFalse(DisplayModel.isFinal(new Scoring(tied).ranking())),
        () -> assertTrue(DisplayModel.isFinal(new Scoring(drawn).ranking())),
        () -> assertFalse(DisplayModel.isFinal(new Scoring(ScoreFixtures.scheduled("Empty")).ranking())),
        () -> assertEquals(List.of(), DisplayModel.revealed(ranking, 0)),
        () -> assertEquals(List.of(entries.getLast()), DisplayModel.revealed(ranking, 1)),
        () -> assertEquals(entries.subList(2, ScoreFixtures.TEAMS), DisplayModel.revealed(ranking, 2)),
        () -> assertEquals(entries, DisplayModel.revealed(ranking, ScoreFixtures.TEAMS + 1)));
  }

}
