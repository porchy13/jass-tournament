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

  /**
   * The waiting list keeps its order: a player moves up when the first one is registered or removed, the last
   * registered player moves to its head, and its first player replaces a registered player, who leaves.
   */
  @Test
  @DisplayName("The waiting list is first in, first out, and its first player replaces a registered one")
  void waitingListTest() {
    final IndividualRegistrations registrations = IndividualRegistrations.empty().add(ANNE).addWaiting(BRUNO)
                                                                         .addWaiting(CHLOE);
    assertAll(
        () -> assertEquals(1, registrations.size()),
        () -> assertEquals(2, registrations.waitingSize()),
        () -> assertEquals(BRUNO, registrations.waitingAt(1)),
        () -> assertEquals(List.of(BRUNO), registrations.participant(1, true)),
        () -> assertEquals(List.of(ANNE), registrations.participant(1, false)),
        () -> assertEquals(List.of(BRUNO, CHLOE), registrations.waitingPlayers()),
        () -> assertEquals(List.of(ANNE, BRUNO), registrations.promote().players()),
        () -> assertEquals(List.of(ANNE, BRUNO, CHLOE), registrations.demote().waiting()),
        () -> assertEquals(List.of(BRUNO), registrations.replaceByFirstWaiting(1).players()),
        () -> assertEquals(List.of(CHLOE), registrations.removeWaiting(1).waiting()),
        () -> assertEquals(List.of(new Player("Gaël", "Vuille"), CHLOE),
                           registrations.replaceWaiting(1, new Player("Gaël", "Vuille")).waiting()),
        () -> assertEquals(ValidationError.DUPLICATE_PLAYER,
                           assertThrows(DomainException.class, () -> registrations.add(BRUNO)).getError()));
  }

  /**
   * A player is registered or corrected from a list of one player, on the waiting list or not.
   */
  @Test
  @DisplayName("A player is registered or corrected from a list of one player, on the waiting list or not")
  void playersTest() {
    final IndividualRegistrations registrations = IndividualRegistrations.empty().enrol(List.of(ANNE), false)
                                                                         .enrol(List.of(BRUNO), true);
    assertAll(
        () -> assertEquals(List.of(ANNE), registrations.players()),
        () -> assertEquals(List.of(BRUNO), registrations.waiting()),
        () -> assertEquals(List.of(CHLOE), registrations.correct(1, List.of(CHLOE), false).players()),
        () -> assertEquals(List.of(CHLOE), registrations.correct(1, List.of(CHLOE), true).waiting()),
        () -> assertEquals(ValidationError.WRONG_TEAM_SIZE,
                           assertThrows(DomainException.class,
                                        () -> registrations.enrol(List.of(CHLOE, new Player("Gaël", "Vuille")),
                                                                  false)).getError()));
  }

}
