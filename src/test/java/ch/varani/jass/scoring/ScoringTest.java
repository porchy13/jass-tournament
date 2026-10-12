package ch.varani.jass.scoring;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TieBreak;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Scoring}: results of the games, forfeits (rules 17 and 19), ranking (rule 14) and drawing of lots
 * (rule 15). The participants are fictitious. In team mode, four teams play two rounds: round 1, team 1 against
 * team 2 (table 1) and team 3 against team 4 (table 2); round 2, team 1 against team 3 (table 1) and team 2 against
 * team 4 (table 2); the first team named is the giver.
 */
final class ScoringTest {

  /** The number of the third participant. */
  private static final int THREE = 3;
  /** The number of the fourth participant. */
  private static final int FOUR = 4;
  /** The points of column A of every deal of a sheet. */
  private static final int A = 100;
  /** The points of column B derived from {@link #A}. */
  private static final int B = Regulation.POINTS_PER_DEAL - A;
  /** The points of the giver over a game where every deal gives it {@link #A}. */
  private static final int GIVER = A * Regulation.DEALS_PER_GAME;
  /** The points of the receiver over a game where every deal gives the giver {@link #A}. */
  private static final int RECEIVER = B * Regulation.DEALS_PER_GAME;
  /** A seed of the drawing of lots. */
  private static final long SEED = 42L;
  /** The date of the fictitious editions. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);

  /**
   * Creates the team edition of the tests, scheduled, without results.
   *
   * @return the edition.
   */
  private static Edition teamEdition() {
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 1; i <= FOUR; i++) {
      teams = teams.add(new Team(new Player("Team" + i, "A"), new Player("Team" + i, "B")));
    }
    return Edition.create("Match", DATE, TournamentMode.TEAM)
                  .withGames(2)
                  .withRegistrations(teams)
                  .withSchedule(new TeamSchedule(FOUR, 2, List.of(
                      new TeamRound(1, List.of(new TeamGame(1, 1, 2), new TeamGame(2, THREE, FOUR))),
                      new TeamRound(2, List.of(new TeamGame(1, 1, THREE), new TeamGame(2, 2, FOUR))))));
  }

  /**
   * Creates an individual edition of four players and one game: players 1 and 2 (giver) against players 3 and 4.
   *
   * @return the edition.
   */
  private static Edition individualEdition() {
    return Edition.create("Individual", DATE, TournamentMode.INDIVIDUAL)
                  .withGames(1)
                  .withRegistrations(new IndividualRegistrations(List.of(
                      new Player("P1", ""), new Player("P2", ""), new Player("P3", ""), new Player("P4", ""))))
                  .withSchedule(new IndividualSchedule(FOUR, 1, List.of(new IndividualRound(1, List.of(
                      new IndividualGame(1, new Partners(1, 2), new Partners(THREE, FOUR)))))));
  }

  /**
   * Creates a sheet where every deal gives the same points to the giver, column B derived.
   *
   * @param round the round.
   * @param table the table.
   * @param points the points of column A of every deal.
   *
   * @return the sheet.
   */
  private static ScoreSheet sheet(final int round, final int table, final int points) {
    ScoreSheet sheet = ScoreSheet.empty(round, table, DealPlan.REGULATION);
    for (int deal = 1; deal <= Regulation.DEALS_PER_GAME; deal++) {
      sheet = sheet.withDeal(deal, DealScore.of(points));
    }
    return sheet;
  }

  /**
   * Returns the results of the four games of the team edition, where every deal gives the giver the given points.
   *
   * @param lastGame the points of column A of every deal of the last game (round 2, table 2).
   *
   * @return the results.
   */
  private static Results allGames(final int lastGame) {
    return Results.EMPTY.withSheet(sheet(1, 1, A)).withSheet(sheet(1, 2, B)).withSheet(sheet(2, 1, A))
                        .withSheet(sheet(2, 2, lastGame));
  }

  /**
   * Creates the scoring of an edition with results.
   *
   * @param edition the edition.
   * @param results the results.
   *
   * @return the scoring.
   */
  private static Scoring scoring(final Edition edition, final Results results) {
    return new Scoring(edition.withResults(results));
  }

  /**
   * A game gets its result from its sheet; a game without sheet, or not in the schedule, has no result.
   */
  @Test
  @DisplayName("A game gets its result from its sheet, a game without sheet has none")
  void resultsTest() {
    final Scoring scoring = scoring(teamEdition(), Results.EMPTY.withSheet(sheet(1, 1, A)));
    final GameResult result = scoring.result(1, 1).orElseThrow();
    assertAll(
        () -> assertEquals(GIVER, result.giverPoints()),
        () -> assertEquals(RECEIVER, result.receiverPoints()),
        () -> assertFalse(result.forfeit()),
        () -> assertEquals(Optional.empty(), scoring.result(1, 2)),
        () -> assertEquals(Optional.empty(), scoring.result(THREE, 1)),
        () -> assertEquals(Optional.empty(), scoring.result(1, THREE)),
        () -> assertEquals(List.of(result), scoring.results()),
        () -> assertFalse(scoring.isComplete()),
        () -> assertFalse(scoring.ranking().complete()),
        () -> assertEquals(GIVER, scoring.totals(sheet(1, 1, A)).giverPoints()));
  }

  /**
   * Rule 14: the ranking sorts the teams by their total over their games.
   */
  @Test
  @DisplayName("Rule 14: the teams are ranked by total points")
  void rankingTest() {
    final int better = A + 1;
    final Scoring scoring = scoring(teamEdition(), allGames(better));
    final int second = RECEIVER + better * Regulation.DEALS_PER_GAME;
    final int third = GIVER + (Regulation.POINTS_PER_DEAL - better) * Regulation.DEALS_PER_GAME;
    final Ranking ranking = scoring.ranking();
    assertAll(
        () -> assertTrue(scoring.isComplete()),
        () -> assertTrue(ranking.complete()),
        () -> assertFalse(ranking.hasTies()),
        () -> assertEquals(List.of(new RankingEntry(1, 1, 2 * GIVER, 2), new RankingEntry(2, 2, second, 2),
                                   new RankingEntry(THREE, FOUR, third, 2),
                                   new RankingEntry(FOUR, THREE, 2 * RECEIVER, 2)),
                           ranking.entries()));
  }

  /**
   * Rule 14: the points of a team in each round are the points of its side, and add up to its total; a round
   * without result gives nothing.
   */
  @Test
  @DisplayName("Rule 14: the points of a team in each round add up to its total")
  void pointsTest() {
    final Scoring scoring = scoring(teamEdition(), allGames(A + 1));
    final RankingEntry best = scoring.ranking().entries().getFirst();
    final int total = scoring.points(best.participant(), 1).orElseThrow()
                      + scoring.points(best.participant(), 2).orElseThrow();
    assertAll(
        () -> assertEquals(best.points(), total),
        () -> assertTrue(scoring.points(best.participant(), THREE).isEmpty()),
        () -> assertTrue(scoring(teamEdition(), Results.EMPTY.withSheet(sheet(1, 1, A))).points(THREE, 1)
                             .isEmpty()));
  }

  /**
   * Rule 15: tied teams share their rank until the drawing of lots, and are listed by number.
   */
  @Test
  @DisplayName("Rule 15: tied teams share their rank until the drawing of lots")
  void tiesTest() {
    final Ranking ranking = scoring(teamEdition(), allGames(A)).ranking();
    assertAll(
        () -> assertTrue(ranking.hasTies()),
        () -> assertEquals(List.of(new RankingEntry(1, 1, 2 * GIVER, 2), new RankingEntry(2, 2, GIVER + RECEIVER, 2),
                                   new RankingEntry(2, FOUR, GIVER + RECEIVER, 2),
                                   new RankingEntry(FOUR, THREE, 2 * RECEIVER, 2)),
                           ranking.entries()));
  }

  /**
   * Rule 15: the drawing of lots orders the tied teams; the same seed gives the same order; once stored, the ranking
   * follows it.
   */
  @Test
  @DisplayName("Rule 15: ties are broken by drawing of lots, reproducible with the recorded seed")
  void drawLotsTest() {
    final Edition edition = teamEdition().withResults(allGames(A));
    final TieBreak tieBreak = new Scoring(edition).drawLots(SEED);
    final Ranking ranking = new Scoring(edition.withResults(edition.results().withTieBreak(tieBreak))).ranking();
    final TieBreak random = new Scoring(edition).drawLots();
    assertAll(
        () -> assertEquals(SEED, tieBreak.seed()),
        () -> assertEquals(tieBreak, new Scoring(edition).drawLots(SEED)),
        () -> assertEquals(List.of(2, FOUR), tieBreak.order().stream().sorted().toList()),
        () -> assertFalse(ranking.hasTies()),
        () -> assertEquals(tieBreak.order().getFirst(), ranking.entries().get(1).participant()),
        () -> assertEquals(2, ranking.entries().get(1).rank()),
        () -> assertEquals(THREE, ranking.entries().get(2).rank()),
        () -> assertEquals(List.of(2, FOUR), random.order().stream().sorted().toList()));
  }

  /**
   * Rule 15: a draw only breaks a tie whose participants were all drawn; a draw without tie is empty.
   */
  @Test
  @DisplayName("Rule 15: a draw breaks only the ties it covers; without tie, nothing is drawn")
  void partialDrawTest() {
    final Results results = allGames(A).withTieBreak(new TieBreak(SEED, List.of(FOUR)));
    final Ranking ranking = scoring(teamEdition(), results).ranking();
    assertAll(
        () -> assertTrue(ranking.hasTies()),
        () -> assertEquals(List.of(), scoring(teamEdition(), allGames(A + 1)).drawLots(SEED).order()));
  }

  /**
   * Rule 15: the drawing of lots is done only once every game has a result.
   */
  @Test
  @DisplayName("Rule 15: lots are drawn only at the end of the games")
  void drawIncompleteTest() {
    final Scoring scoring = scoring(teamEdition(), Results.EMPTY.withSheet(sheet(1, 1, A)));
    assertEquals(ValidationError.RESULTS_INCOMPLETE,
                 assertThrows(DomainException.class, () -> scoring.drawLots(SEED)).getError());
  }

  /**
   * Rule 19: an absent team gets 0 points and its opponents half of a game, 1'256 points; if both are absent, both
   * get 0.
   */
  @Test
  @DisplayName("Rule 19: the opponents of an absent team receive 1'256 points")
  void absenceTest() {
    final Edition edition = teamEdition();
    final int forfeit = Regulation.FORFEIT_POINTS;
    final GameResult giver = scoring(edition, Results.EMPTY.withSheet(sheet(1, 1, A).withAbsence(Absence.GIVER)))
        .result(1, 1).orElseThrow();
    final GameResult receiver = scoring(edition, Results.EMPTY.withSheet(
        ScoreSheet.empty(1, 1, DealPlan.REGULATION).withAbsence(Absence.RECEIVER))).result(1, 1).orElseThrow();
    final GameResult both = scoring(edition, Results.EMPTY.withSheet(
        ScoreSheet.empty(1, 1, DealPlan.REGULATION).withAbsence(Absence.BOTH))).result(1, 1).orElseThrow();
    assertAll(
        () -> assertEquals(new GameResult(giver.pairing(), 0, forfeit, true), giver),
        () -> assertEquals(new GameResult(receiver.pairing(), forfeit, 0, true), receiver),
        () -> assertEquals(new GameResult(both.pairing(), 0, 0, true), both));
  }

  /**
   * Rule 17: an excluded team loses all its games, played or not, its opponents receive 1'256 points, and it is
   * removed from the ranking.
   */
  @Test
  @DisplayName("Rule 17: an excluded team loses its games, its opponents receive 1'256 points")
  void exclusionTest() {
    final Scoring scoring = scoring(teamEdition(), Results.EMPTY.withSheet(sheet(2, 1, A)).withExclusion(THREE, true));
    final Scoring both = scoring(teamEdition(), Results.EMPTY.withExclusion(THREE, true).withExclusion(FOUR, true));
    final int forfeit = Regulation.FORFEIT_POINTS;
    assertAll(
        () -> assertEquals(forfeit, scoring.result(1, 2).orElseThrow().receiverPoints()),
        () -> assertEquals(0, scoring.result(1, 2).orElseThrow().giverPoints()),
        () -> assertEquals(forfeit, scoring.result(2, 1).orElseThrow().giverPoints()),
        () -> assertEquals(0, scoring.result(2, 1).orElseThrow().receiverPoints()),
        () -> assertEquals(List.of(1, FOUR, 2), scoring.ranking().entries().stream()
                                                       .map(RankingEntry::participant).toList()),
        () -> assertEquals(0, both.result(1, 2).orElseThrow().giverPoints()),
        () -> assertEquals(0, both.result(1, 2).orElseThrow().receiverPoints()));
  }

  /**
   * Individual mode: each partner gets the points of its side; an absent player makes its side absent; an excluded
   * player makes its side forfeit and is removed from the ranking.
   */
  @Test
  @DisplayName("Individual mode: each partner gets the points of its side, absences and exclusions forfeit the side")
  void individualTest() {
    final Edition edition = individualEdition();
    final Ranking played = scoring(edition, Results.EMPTY.withSheet(sheet(1, 1, A))).ranking();
    final Scoring absent = scoring(edition, Results.EMPTY.withSheet(sheet(1, 1, A).withAbsence(Absence.RECEIVER)));
    final Ranking excluded = scoring(edition, Results.EMPTY.withExclusion(1, true)).ranking();
    final int forfeit = Regulation.FORFEIT_POINTS;
    assertAll(
        () -> assertEquals(List.of(new RankingEntry(1, 1, GIVER, 1), new RankingEntry(1, 2, GIVER, 1),
                                   new RankingEntry(THREE, THREE, RECEIVER, 1),
                                   new RankingEntry(THREE, FOUR, RECEIVER, 1)), played.entries()),
        () -> assertEquals(forfeit, absent.result(1, 1).orElseThrow().pointsOf(2)),
        () -> assertEquals(0, absent.result(1, 1).orElseThrow().pointsOf(FOUR)),
        () -> assertEquals(List.of(new RankingEntry(1, THREE, forfeit, 1), new RankingEntry(1, FOUR, forfeit, 1),
                                   new RankingEntry(THREE, 2, 0, 1)), excluded.entries()));
  }

  /**
   * Before the schedule, there is no result and every participant has 0 points.
   */
  @Test
  @DisplayName("Before the schedule, every participant has 0 points")
  void noScheduleTest() {
    final Scoring scoring = new Scoring(teamEdition().withoutSchedule());
    assertAll(
        () -> assertEquals(Optional.empty(), scoring.result(1, 1)),
        () -> assertFalse(scoring.isComplete()),
        () -> assertEquals(FOUR, scoring.ranking().entries().size()),
        () -> assertTrue(scoring.ranking().entries().stream().allMatch(entry -> entry.points() == 0
                                                                                && entry.rank() == 1)));
  }

}
