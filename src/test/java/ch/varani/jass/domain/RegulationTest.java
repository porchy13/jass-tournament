package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link Regulation}: the constants are consistent with each other, as the regulation states.
 */
final class RegulationTest {

  /**
   * Rule 5: the total of a game is the number of deals times the points of a deal.
   */
  @Test
  @DisplayName("Rule 5: a game of 16 deals of 157 points totals 2'512 points")
  void pointsPerGameTest() {
    assertEquals(Regulation.DEALS_PER_GAME * Regulation.POINTS_PER_DEAL, Regulation.POINTS_PER_GAME);
  }

  /**
   * Rules 8 to 11: a block of four deals totals 628 points, and a game of the regulation has four blocks; each player
   * at a table starts one deal of each block.
   */
  @Test
  @DisplayName("Rules 8 to 11: a block of 4 deals totals 628 points, a game has 4 blocks, one deal per player")
  void pointsPerBlockTest() {
    assertEquals(Regulation.DEALS_PER_BLOCK * Regulation.POINTS_PER_DEAL, Regulation.POINTS_PER_BLOCK);
    assertEquals(Regulation.DEALS_PER_GAME, Regulation.DEALS_PER_BLOCK * Regulation.DEALS_PER_BLOCK);
    assertEquals(Regulation.PLAYERS_PER_TABLE, Regulation.DEALS_PER_BLOCK);
  }

  /**
   * Rules 17 and 19: a forfeit gives half of the points of a game.
   */
  @Test
  @DisplayName("Rules 17 and 19: a forfeit gives half of a game, 1'256 points")
  void forfeitPointsTest() {
    assertEquals(Regulation.POINTS_PER_GAME / 2, Regulation.FORFEIT_POINTS);
  }

  /**
   * A table of the individual mode gathers two teams.
   */
  @Test
  @DisplayName("A table gathers two teams of two players")
  void playersPerTableTest() {
    assertEquals(2 * Regulation.PLAYERS_PER_TEAM, Regulation.PLAYERS_PER_TABLE);
  }

}
