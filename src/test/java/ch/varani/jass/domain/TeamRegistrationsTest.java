package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link TeamRegistrations}: numbering, modifications, duplicate persons and minimum size.
 */
final class TeamRegistrationsTest {

  /** A first fictitious team. */
  private static final Team TEAM_1 = new Team(new Player("Anne", "Fontaine"), new Player("Bruno", "Rochat"));
  /** A second fictitious team. */
  private static final Team TEAM_2 = new Team(new Player("Chloé", "Girard"), new Player("David", "Perret"));
  /** A third fictitious team. */
  private static final Team TEAM_3 = new Team(new Player("Elodie", "Jaquet"), new Player("Fabien", "Monnier"));
  /** The number of games of the regulation. */
  private static final int GAMES = Regulation.GAMES;

  /**
   * Empty registrations have the team mode and no team.
   */
  @Test
  @DisplayName("Empty registrations have the team mode and no team")
  void emptyTest() {
    final TeamRegistrations registrations = TeamRegistrations.empty();
    assertAll(
        () -> assertEquals(TournamentMode.TEAM, registrations.mode()),
        () -> assertEquals(0, registrations.size()),
        () -> assertTrue(registrations.allPlayers().isEmpty()));
  }

  /**
   * The teams are numbered from 1 in their order of registration.
   */
  @Test
  @DisplayName("The teams are numbered from 1 in their order of registration")
  void addTest() {
    final TeamRegistrations registrations = TeamRegistrations.empty().add(TEAM_1).add(TEAM_2);
    assertAll(
        () -> assertEquals(2, registrations.size()),
        () -> assertEquals(TEAM_1, registrations.get(1)),
        () -> assertEquals(TEAM_2, registrations.get(2)),
        () -> assertEquals(List.of(TEAM_1.players().get(0), TEAM_1.players().get(1),
                                   TEAM_2.players().get(0), TEAM_2.players().get(1)),
                           registrations.allPlayers()));
  }

  /**
   * Replacing a team keeps its number.
   */
  @Test
  @DisplayName("Replacing a team keeps its number")
  void replaceTest() {
    final TeamRegistrations registrations = TeamRegistrations.empty().add(TEAM_1).add(TEAM_2).replace(1, TEAM_3);
    assertEquals(List.of(TEAM_3, TEAM_2), registrations.teams());
  }

  /**
   * Removing a team renumbers the following ones.
   */
  @Test
  @DisplayName("Removing a team renumbers the following ones")
  void removeTest() {
    final TeamRegistrations registrations = new TeamRegistrations(List.of(TEAM_1, TEAM_2, TEAM_3)).remove(2);
    assertEquals(TEAM_3, registrations.get(2));
  }

  /**
   * An unknown number is rejected, below 1 and above the number of teams.
   */
  @Test
  @DisplayName("An unknown team number is rejected")
  void unknownNumberTest() {
    final TeamRegistrations registrations = TeamRegistrations.empty().add(TEAM_1);
    assertAll(
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, () -> registrations.get(0)).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, () -> registrations.remove(2)).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, () -> registrations.replace(2, TEAM_2)).getError()));
  }

  /**
   * A person cannot be registered in two teams.
   */
  @Test
  @DisplayName("A person cannot be registered in two teams")
  void duplicateTest() {
    final Team sameAnne = new Team(new Player("ANNE", "Fontaine"), new Player("Gaël", "Vuille"));
    final TeamRegistrations registrations = TeamRegistrations.empty().add(TEAM_1);
    assertEquals(ValidationError.DUPLICATE_PLAYER,
                 assertThrows(DomainException.class, () -> registrations.add(sameAnne)).getError());
  }

  /**
   * Rule 1: each team meets a different opponent in each of its games, so at least games + 1 teams are needed.
   */
  @Test
  @DisplayName("Rule 1: at least games + 1 teams are needed")
  void minimumSizeTest() {
    assertEquals(GAMES + 1, TeamRegistrations.empty().minimumSize(GAMES));
  }

  /**
   * The waiting list keeps its order: a team moves up when the first one is registered or removed, the last registered
   * team moves to its head, and its first team replaces a registered team, which leaves.
   */
  @Test
  @DisplayName("The waiting list is first in, first out, and its first team replaces a registered one")
  void waitingListTest() {
    final TeamRegistrations registrations = TeamRegistrations.empty().add(TEAM_1).addWaiting(TEAM_2)
                                                             .addWaiting(TEAM_3);
    assertAll(
        () -> assertEquals(1, registrations.size()),
        () -> assertEquals(2, registrations.waitingSize()),
        () -> assertEquals(TEAM_2, registrations.waitingAt(1)),
        () -> assertEquals(TEAM_2.players(), registrations.participant(1, true)),
        () -> assertEquals(TEAM_1.players(), registrations.participant(1, false)),
        () -> assertEquals(List.of(TEAM_2.players().get(0), TEAM_2.players().get(1), TEAM_3.players().get(0),
                                   TEAM_3.players().get(1)), registrations.waitingPlayers()),
        () -> assertEquals(List.of(TEAM_1, TEAM_2), registrations.promote().teams()),
        () -> assertEquals(List.of(TEAM_3), registrations.promote().waiting()),
        () -> assertEquals(List.of(TEAM_1, TEAM_2, TEAM_3), registrations.demote().waiting()),
        () -> assertEquals(List.of(TEAM_2), registrations.replaceByFirstWaiting(1).teams()),
        () -> assertEquals(List.of(TEAM_3), registrations.replaceByFirstWaiting(1).waiting()),
        () -> assertEquals(List.of(TEAM_3), registrations.removeWaiting(1).waiting()),
        () -> assertEquals(List.of(TEAM_2), new TeamRegistrations(List.of(TEAM_1), List.of(TEAM_2)).waiting()));
  }

  /**
   * A team is made of players to register it, on the waiting list or not, and to correct it.
   */
  @Test
  @DisplayName("A team is registered or corrected from its players, on the waiting list or not")
  void playersTest() {
    final TeamRegistrations registrations = TeamRegistrations.empty().enrol(TEAM_1.players(), false)
                                                             .enrol(TEAM_2.players(), true);
    assertAll(
        () -> assertEquals(List.of(TEAM_1), registrations.teams()),
        () -> assertEquals(List.of(TEAM_2), registrations.waiting()),
        () -> assertEquals(List.of(TEAM_3), registrations.correct(1, TEAM_3.players(), false).teams()),
        () -> assertEquals(List.of(TEAM_3), registrations.correct(1, TEAM_3.players(), true).waiting()),
        () -> assertEquals(List.of(TEAM_3), registrations.replaceWaiting(1, TEAM_3).waiting()),
        () -> assertEquals(ValidationError.WRONG_TEAM_SIZE,
                           assertThrows(DomainException.class,
                                        () -> registrations.enrol(List.of(new Player("Gaël", "Vuille")), false))
                               .getError()));
  }

  /**
   * A person cannot be both registered and on the waiting list, and the operations of an empty list are rejected.
   */
  @Test
  @DisplayName("A person cannot be both registered and waiting; an empty waiting list has no first team")
  void waitingErrorsTest() {
    final TeamRegistrations registrations = TeamRegistrations.empty().add(TEAM_1);
    assertAll(
        () -> assertEquals(ValidationError.DUPLICATE_PLAYER,
                           assertThrows(DomainException.class, () -> registrations.addWaiting(TEAM_1)).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, registrations::promote).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, () -> registrations.replaceByFirstWaiting(1))
                               .getError()),
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, () -> registrations.removeWaiting(1)).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, TeamRegistrations.empty()::demote).getError()));
  }

}
