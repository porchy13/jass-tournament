package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link IndividualSchedule}: number of rounds, mode, participants and immutability.
 */
final class IndividualScheduleTest {

  /** A number of players filling every table. */
  private static final int FULL_TABLES = 24;

  /**
   * Rule 1, applied to the players: one round per game when the players fill every table.
   */
  @Test
  @DisplayName("Rule 1: one round per game when the players fill every table")
  void roundsForFullTablesTest() {
    assertEquals(Regulation.GAMES, IndividualSchedule.roundsFor(FULL_TABLES, Regulation.GAMES));
  }

  /**
   * Rule 1, applied to the players: a catch-up round when there are surplus players.
   *
   * @param surplus the number of surplus players.
   */
  @ParameterizedTest(name = "{0} surplus players")
  @MethodSource("surpluses")
  @DisplayName("Rule 1: a catch-up round when there are surplus players")
  void roundsForSurplusTest(final int surplus) {
    assertEquals(Regulation.GAMES + 1, IndividualSchedule.roundsFor(FULL_TABLES + surplus, Regulation.GAMES));
  }

  /**
   * Provides the possible numbers of surplus players.
   *
   * @return 1 to {@link Regulation#PLAYERS_PER_TABLE} − 1.
   */
  static IntStream surpluses() {
    return IntStream.range(1, Regulation.PLAYERS_PER_TABLE);
  }

  /**
   * An individual schedule is of the individual mode and its participants are the players.
   */
  @Test
  @DisplayName("An individual schedule is of the individual mode, its participants are the players")
  void modeTest() {
    final IndividualSchedule schedule = new IndividualSchedule(FULL_TABLES, Regulation.GAMES, List.of());
    assertAll(
        () -> assertEquals(TournamentMode.INDIVIDUAL, schedule.mode()),
        () -> assertEquals(FULL_TABLES, schedule.participants()));
  }

  /**
   * A schedule copies its list of rounds and rejects a missing list.
   */
  @Test
  @DisplayName("A schedule copies its rounds and rejects a missing list")
  void immutabilityTest() {
    final IndividualRound round = new IndividualRound(1, List.of());
    final List<IndividualRound> rounds = new ArrayList<>(List.of(round));
    final IndividualSchedule schedule = new IndividualSchedule(FULL_TABLES, 1, rounds);
    rounds.clear();
    assertAll(
        () -> assertEquals(List.of(round), schedule.rounds()),
        () -> assertThrows(UnsupportedOperationException.class, () -> schedule.rounds().clear()),
        () -> assertThrows(NullPointerException.class, () -> new IndividualSchedule(FULL_TABLES, 1, null)));
  }

}
