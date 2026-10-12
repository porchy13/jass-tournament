package ch.varani.jass.scoring;

import ch.varani.jass.domain.Pairing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Ranking} and {@link GameResult}: ties of a ranking, points of a participant in a game.
 */
final class RankingTest {

  /** Points of a participant. */
  private static final int POINTS = 1_000;
  /** The number of a participant. */
  private static final int THREE = 3;

  /**
   * A ranking has ties when two lines share a rank.
   */
  @Test
  @DisplayName("A ranking has ties when two lines share a rank")
  void tiesTest() {
    final RankingEntry first = new RankingEntry(1, 1, POINTS, 1);
    assertAll(
        () -> assertTrue(new Ranking(List.of(first, new RankingEntry(1, 2, POINTS, 1)), true).hasTies()),
        () -> assertFalse(new Ranking(List.of(first, new RankingEntry(2, 2, POINTS, 1)), true).hasTies()),
        () -> assertFalse(new Ranking(List.of(), false).hasTies()));
  }

  /**
   * Each participant of a side gets the points of its side; the other participants get nothing.
   */
  @Test
  @DisplayName("Each participant of a side gets the points of its side")
  void gameResultTest() {
    final GameResult result = new GameResult(new Pairing(1, 1, List.of(1, 2), List.of(THREE)), POINTS, 2, false);
    assertAll(
        () -> assertEquals(POINTS, result.pointsOf(2)),
        () -> assertEquals(2, result.pointsOf(THREE)),
        () -> assertEquals(0, result.pointsOf(THREE + 1)),
        () -> assertTrue(result.involves(1)),
        () -> assertTrue(result.involves(THREE)),
        () -> assertFalse(result.involves(THREE + 1)));
  }

}
