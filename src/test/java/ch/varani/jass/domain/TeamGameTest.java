package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link TeamGame}: places of the teams at the table.
 */
final class TeamGameTest {

  /** The number of a table. */
  private static final int TABLE = 3;
  /** The place of the giver at {@link #TABLE}. */
  private static final int GIVER_PLACE = 5;
  /** The place of the receiver at {@link #TABLE}. */
  private static final int RECEIVER_PLACE = 6;

  /**
   * Rule 6: at table k, the giver sits at place 2k − 1 and the receiver at place 2k.
   */
  @Test
  @DisplayName("Rule 6: at table k, the giver sits at place 2k - 1, the smaller one")
  void placesTest() {
    final TeamGame game = new TeamGame(TABLE, 2, 1);
    assertAll(
        () -> assertEquals(GIVER_PLACE, game.giverPlace()),
        () -> assertEquals(RECEIVER_PLACE, game.receiverPlace()));
  }

}
