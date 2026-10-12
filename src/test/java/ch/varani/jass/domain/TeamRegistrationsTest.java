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

}
