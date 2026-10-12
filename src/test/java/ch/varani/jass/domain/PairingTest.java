package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link Pairing} and of the pairings of the schedules of both modes.
 */
final class PairingTest {

  /** The number of a participant. */
  private static final int THREE = 3;
  /** The number of a participant. */
  private static final int FOUR = 4;

  /**
   * A pairing copies its lists.
   */
  @Test
  @DisplayName("A pairing copies its lists")
  void copyTest() {
    final List<Integer> giver = new ArrayList<>(List.of(1));
    final Pairing pairing = new Pairing(1, 1, giver, List.of(2));
    giver.clear();
    assertAll(
        () -> assertEquals(List.of(1), pairing.giver()),
        () -> assertThrows(NullPointerException.class, () -> new Pairing(1, 1, null, List.of())));
  }

  /**
   * The pairings of a team schedule are sorted by round then table, one team per side.
   */
  @Test
  @DisplayName("The pairings of a team schedule have one team per side, sorted by round and table")
  void teamTest() {
    final TeamSchedule schedule = new TeamSchedule(FOUR, 2, List.of(
        new TeamRound(2, List.of(new TeamGame(1, 1, THREE))),
        new TeamRound(1, List.of(new TeamGame(2, THREE, FOUR), new TeamGame(1, 1, 2)))));
    assertEquals(List.of(new Pairing(1, 1, List.of(1), List.of(2)), new Pairing(1, 2, List.of(THREE), List.of(FOUR)),
                         new Pairing(2, 1, List.of(1), List.of(THREE))),
                 schedule.pairings());
  }

  /**
   * The pairings of an individual schedule have the two partners of each side.
   */
  @Test
  @DisplayName("The pairings of an individual schedule have two partners per side")
  void individualTest() {
    final IndividualSchedule schedule = new IndividualSchedule(FOUR, 1, List.of(
        new IndividualRound(1, List.of(new IndividualGame(1, new Partners(1, FOUR), new Partners(2, THREE))))));
    assertEquals(List.of(new Pairing(1, 1, List.of(1, FOUR), List.of(2, THREE))), schedule.pairings());
  }

}
