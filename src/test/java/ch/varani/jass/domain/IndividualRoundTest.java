package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link IndividualRound}: immutability.
 */
final class IndividualRoundTest {

  /**
   * A round copies its list of games and rejects a missing list.
   */
  @Test
  @DisplayName("A round copies its games and rejects a missing list")
  void immutabilityTest() {
    final IndividualGame game = new IndividualGame(1, new Partners(1, 2), new Partners(3, 4));
    final List<IndividualGame> games = new ArrayList<>(List.of(game));
    final IndividualRound round = new IndividualRound(1, games);
    games.clear();
    assertAll(
        () -> assertEquals(List.of(game), round.games()),
        () -> assertThrows(UnsupportedOperationException.class, () -> round.games().clear()),
        () -> assertThrows(NullPointerException.class, () -> new IndividualRound(1, null)));
  }

}
