package ch.varani.jass.scoring;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TournamentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test protocol of phase 6: the real points of the 20 teams of the 2025 edition over its 4 games (sheet "Résultats" of
 * the former spreadsheet, team numbers only, fictitious names, {@code AGENTS.md} §5); the ranking computed by the
 * application must match the ranking of the sheet.
 *
 * <p>The spreadsheet does not keep the schedule: the pairings below are reconstructed from the points, the points of
 * both teams of a game summing to 2'512, except two games summing to 2'502 (round 1, teams 12 and 5; round 4,
 * teams 11 and 10), which test a total below 2'512 (no correction, Q2). The ranking depends only on the points of each
 * team. The first team of each pair is the giver.</p>
 */
final class Edition2025Test {

  /** The date of the 2025 edition. */
  private static final LocalDate DATE = LocalDate.of(2025, Month.NOVEMBER, 21);
  /** The number of teams of the 2025 edition. */
  private static final int TEAMS = 20;

  /** The points of each team (index = team number − 1) in each game (index = round − 1). */
  private static final int[][] POINTS = {
      {1264, 1196, 1371, 1168}, {1357, 1389, 1187, 1292}, {1196, 1161, 1533, 1356}, {1349, 1351, 1253, 1071},
      {1408, 1180, 1130, 1344}, {1384, 1401, 1474, 1546}, {1169, 1332, 1287, 1220}, {1206, 1316, 1259, 966},
      {1306, 1199, 1325, 1101}, {1248, 1472, 979, 1025}, {1436, 1493, 1454, 1477}, {1094, 1313, 1225, 1543},
      {1151, 1322, 1058, 1156}, {1343, 1123, 1493, 1441}, {1155, 1240, 1141, 1246}, {1361, 1019, 1038, 969},
      {1076, 1111, 1211, 1266}, {1128, 1190, 1301, 1411}, {1316, 1040, 1019, 1088}, {1163, 1272, 1382, 1424},
  };

  /** The pairings of each round (giver, receiver), reconstructed from the points. */
  private static final int[][][] PAIRINGS = {
      {{1, 10}, {2, 15}, {3, 19}, {4, 20}, {5, 12}, {6, 18}, {7, 14}, {8, 9}, {11, 17}, {13, 16}},
      {{1, 8}, {2, 14}, {3, 4}, {5, 7}, {6, 17}, {9, 12}, {10, 19}, {11, 16}, {13, 18}, {15, 20}},
      {{1, 15}, {2, 9}, {3, 10}, {4, 8}, {5, 20}, {6, 16}, {7, 12}, {11, 13}, {14, 19}, {17, 18}},
      {{1, 5}, {2, 7}, {3, 13}, {4, 14}, {6, 8}, {9, 18}, {10, 11}, {12, 16}, {15, 17}, {19, 20}},
  };

  /** The ranking of the sheet "Résultats": team numbers, the first one first. */
  private static final List<Integer> RANKING = List.of(11, 6, 14, 3, 20, 2, 12, 5, 18, 4, 7, 1, 9, 15, 8, 10, 13, 17,
                                                       19, 16);
  /** The totals of the teams of {@link #RANKING}, in the same order. */
  private static final List<Integer> TOTALS = List.of(5860, 5805, 5400, 5246, 5241, 5225, 5175, 5062, 5030, 5024,
                                                      5008, 4999, 4931, 4782, 4747, 4724, 4687, 4664, 4463, 4387);

  /**
   * Rule 14: the ranking of the 2025 edition computed from the score sheets matches the ranking of the sheet
   * "Résultats": team 11 first with 5'860 points, team 16 last with 4'387 points.
   */
  @Test
  @DisplayName("Rule 14: the 2025 ranking is found again, team 11 first with 5'860 points, team 16 last")
  void ranking2025Test() {
    final Ranking ranking = new Scoring(edition()).ranking();
    assertAll(
        () -> assertTrue(ranking.complete()),
        () -> assertFalse(ranking.hasTies()),
        () -> assertEquals(RANKING, ranking.entries().stream().map(RankingEntry::participant).toList()),
        () -> assertEquals(TOTALS, ranking.entries().stream().map(RankingEntry::points).toList()),
        () -> assertTrue(ranking.entries().stream().allMatch(entry -> entry.games() == Regulation.GAMES)));
  }

  /**
   * Builds the 2025 edition: fictitious teams, the reconstructed schedule and one score sheet per game.
   *
   * @return the edition with its results.
   */
  private static Edition edition() {
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 1; i <= TEAMS; i++) {
      teams = teams.add(new Team(new Player("Team" + i, "A"), new Player("Team" + i, "B")));
    }
    final List<TeamRound> rounds = new ArrayList<>();
    Results results = Results.EMPTY;
    for (int round = 0; round < PAIRINGS.length; round++) {
      final List<TeamGame> games = new ArrayList<>();
      for (int table = 0; table < PAIRINGS[round].length; table++) {
        final int giver = PAIRINGS[round][table][0];
        final int receiver = PAIRINGS[round][table][1];
        games.add(new TeamGame(table + 1, giver, receiver));
        results = results.withSheet(sheet(round + 1, table + 1, POINTS[giver - 1][round],
                                          POINTS[receiver - 1][round]));
      }
      rounds.add(new TeamRound(round + 1, games));
    }
    return Edition.create("Match aux cartes 2025", DATE, TournamentMode.TEAM)
                  .withRegistrations(teams)
                  .withSchedule(new TeamSchedule(TEAMS, Regulation.GAMES, rounds))
                  .withResults(results);
  }

  /**
   * Builds a score sheet giving each side its points: column A filled deal after deal with up to 157 points, column B
   * derived; when both totals sum to less than 2'512, one deal gets a manual column B, as the players wrote it.
   *
   * @param round the round.
   * @param table the table.
   * @param giver the points of the giver.
   * @param receiver the points of the receiver.
   *
   * @return the score sheet.
   */
  private static ScoreSheet sheet(final int round, final int table, final int giver, final int receiver) {
    final List<DealScore> deals = new ArrayList<>();
    int remaining = giver;
    for (int deal = 0; deal < Regulation.DEALS_PER_GAME; deal++) {
      final int points = Math.min(Regulation.POINTS_PER_DEAL, remaining);
      remaining -= points;
      deals.add(DealScore.of(points));
    }
    final int missing = Regulation.POINTS_PER_GAME - giver - receiver;
    if (missing > 0) {
      final DealScore last = deals.getLast();
      deals.set(deals.size() - 1, new DealScore(last.giver(), last.receiverPoints() - missing));
    }
    return new ScoreSheet(round, table, deals, Absence.NONE);
  }

}
