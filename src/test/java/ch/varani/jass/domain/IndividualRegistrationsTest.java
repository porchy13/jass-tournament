package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link IndividualRegistrations}: numbering, modifications, duplicate persons and minimum size.
 */
final class IndividualRegistrationsTest {

  /** A first fictitious player. */
  private static final Player ANNE = new Player("Anne", "Fontaine");
  /** A second fictitious player. */
  private static final Player BRUNO = new Player("Bruno", "Rochat");
  /** A third fictitious player. */
  private static final Player CHLOE = new Player("Chloé", "Girard");
  /** A number of games lower than a table minus one. */
  private static final int FEW_GAMES = 2;
  /** A number of games requiring more players than a table. */
  private static final int MANY_GAMES = 6;

  /**
   * Registrations have the individual mode, and the players are numbered from 1.
   */
  @Test
  @DisplayName("The players are numbered from 1 in their order of registration")
  void addTest() {
    final IndividualRegistrations registrations = IndividualRegistrations.empty().add(ANNE).add(BRUNO);
    assertAll(
        () -> assertEquals(TournamentMode.INDIVIDUAL, registrations.mode()),
        () -> assertEquals(2, registrations.size()),
        () -> assertEquals(BRUNO, registrations.get(2)),
        () -> assertEquals(List.of(ANNE, BRUNO), registrations.allPlayers()));
  }

  /**
   * Replacing a player keeps its number; removing one renumbers the following ones.
   */
  @Test
  @DisplayName("Replacing keeps the number, removing renumbers the following players")
  void replaceAndRemoveTest() {
    final IndividualRegistrations registrations = new IndividualRegistrations(List.of(ANNE, BRUNO));
    assertAll(
        () -> assertEquals(List.of(CHLOE, BRUNO), registrations.replace(1, CHLOE).players()),
        () -> assertEquals(List.of(BRUNO), registrations.remove(1).players()));
  }

  /**
   * A person cannot be registered twice, and an unknown number is rejected.
   */
  @Test
  @DisplayName("A person cannot be registered twice, an unknown number is rejected")
  void errorsTest() {
    final IndividualRegistrations registrations = IndividualRegistrations.empty().add(ANNE);
    final Player sameName = new Player("anne", "FONTAINE");
    assertAll(
        () -> assertEquals(ValidationError.DUPLICATE_PLAYER,
                           assertThrows(DomainException.class, () -> registrations.add(sameName)).getError()),
        () -> assertEquals(ValidationError.UNKNOWN_REGISTRATION,
                           assertThrows(DomainException.class, () -> registrations.get(2)).getError()));
  }

  /**
   * At least a full table is needed, and each player needs a different partner in each game.
   */
  @Test
  @DisplayName("At least a full table and games + 1 players are needed")
  void minimumSizeTest() {
    final IndividualRegistrations registrations = IndividualRegistrations.empty();
    assertAll(
        () -> assertEquals(Regulation.PLAYERS_PER_TABLE, registrations.minimumSize(FEW_GAMES)),
        () -> assertEquals(MANY_GAMES + 1, registrations.minimumSize(MANY_GAMES)));
  }

}
