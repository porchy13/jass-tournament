package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link TeamSchedule}: number of rounds, mode, participants and immutability.
 */
final class TeamScheduleTest {

  /** An even number of teams. */
  private static final int EVEN_TEAMS = 20;
  /** An odd number of teams. */
  private static final int ODD_TEAMS = 21;

  /**
   * Rule 1: one round per game, plus a catch-up round when the number of teams is odd.
   */
  @Test
  @DisplayName("Rule 1: one round per game, plus a catch-up round when the number of teams is odd")
  void roundsForTest() {
    assertAll(
        () -> assertEquals(Regulation.GAMES, TeamSchedule.roundsFor(EVEN_TEAMS, Regulation.GAMES)),
        () -> assertEquals(Regulation.GAMES + 1, TeamSchedule.roundsFor(ODD_TEAMS, Regulation.GAMES)));
  }

  /**
   * A schedule copies its list of rounds and rejects a missing list.
   */
  @Test
  @DisplayName("A schedule copies its rounds and rejects a missing list")
  void immutabilityTest() {
    final TeamRound round = new TeamRound(1, List.of(new TeamGame(1, 1, 2)));
    final List<TeamRound> rounds = new ArrayList<>(List.of(round));
    final TeamSchedule schedule = new TeamSchedule(2, 1, rounds);
    rounds.clear();
    assertAll(
        () -> assertEquals(List.of(round), schedule.rounds()),
        () -> assertThrows(UnsupportedOperationException.class, () -> schedule.rounds().clear()),
        () -> assertThrows(NullPointerException.class, () -> new TeamSchedule(2, 1, null)));
  }

  /**
   * A team schedule is of the team mode and its participants are the teams.
   */
  @Test
  @DisplayName("A team schedule is of the team mode, its participants are the teams")
  void modeTest() {
    final TeamSchedule schedule = new TeamSchedule(EVEN_TEAMS, Regulation.GAMES, List.of());
    assertAll(
        () -> assertEquals(TournamentMode.TEAM, schedule.mode()),
        () -> assertEquals(EVEN_TEAMS, schedule.participants()));
  }

}
