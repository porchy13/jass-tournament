package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link TeamRound}: immutability.
 */
final class TeamRoundTest {

  /**
   * A round copies its list of games and rejects a missing list.
   */
  @Test
  @DisplayName("A round copies its games and rejects a missing list")
  void immutabilityTest() {
    final List<TeamGame> games = new ArrayList<>(List.of(new TeamGame(1, 1, 2)));
    final TeamRound round = new TeamRound(1, games);
    games.clear();
    assertAll(
        () -> assertEquals(List.of(new TeamGame(1, 1, 2)), round.games()),
        () -> assertThrows(UnsupportedOperationException.class, () -> round.games().clear()),
        () -> assertThrows(NullPointerException.class, () -> new TeamRound(1, null)));
  }

}
