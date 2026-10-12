package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link IndividualGame}: places of the teams at the table and missing teams.
 */
final class IndividualGameTest {

  /** The number of a table. */
  private static final int TABLE = 3;
  /** The place of the giver at {@link #TABLE}. */
  private static final int GIVER_PLACE = 5;
  /** The place of the receiver at {@link #TABLE}. */
  private static final int RECEIVER_PLACE = 6;
  /** A team of two partners. */
  private static final Partners GIVER = new Partners(1, 2);
  /** Another team of two partners. */
  private static final Partners RECEIVER = new Partners(3, 4);

  /**
   * Rule 6: at table k, the giver team sits at place 2k − 1 and the receiver team at place 2k.
   */
  @Test
  @DisplayName("Rule 6: at table k, the giver team sits at place 2k - 1, the smaller one")
  void placesTest() {
    final IndividualGame game = new IndividualGame(TABLE, GIVER, RECEIVER);
    assertAll(
        () -> assertEquals(GIVER_PLACE, game.giverPlace()),
        () -> assertEquals(RECEIVER_PLACE, game.receiverPlace()));
  }

  /**
   * A game needs both teams.
   */
  @Test
  @DisplayName("A missing team is rejected")
  void missingTeamTest() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> new IndividualGame(TABLE, null, RECEIVER)),
        () -> assertThrows(NullPointerException.class, () -> new IndividualGame(TABLE, GIVER, null)));
  }

}
