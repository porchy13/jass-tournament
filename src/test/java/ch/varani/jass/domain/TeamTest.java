package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link Team}: two different players, copied list.
 */
final class TeamTest {

  /** A first fictitious player. */
  private static final Player ANNE = new Player("Anne", "Fontaine");
  /** A second fictitious player. */
  private static final Player BRUNO = new Player("Bruno", "Rochat");

  /**
   * A team keeps its two players in order, in a copy of the given list.
   */
  @Test
  @DisplayName("A team keeps a copy of its two players")
  void playersTest() {
    final List<Player> players = new ArrayList<>(List.of(ANNE, BRUNO));
    final Team team = new Team(players);
    players.clear();
    assertEquals(List.of(ANNE, BRUNO), team.players());
  }

  /**
   * A team must have exactly two players.
   */
  @Test
  @DisplayName("A team must have exactly two players")
  void sizeTest() {
    assertEquals(ValidationError.WRONG_TEAM_SIZE,
                 assertThrows(DomainException.class, () -> new Team(List.of(ANNE))).getError());
  }

  /**
   * The two players of a team must be different persons.
   */
  @Test
  @DisplayName("The two players of a team must be different persons")
  void duplicateTest() {
    final Player sameName = new Player("anne", "fontaine");
    assertEquals(ValidationError.DUPLICATE_PLAYER,
                 assertThrows(DomainException.class, () -> new Team(ANNE, sameName)).getError());
  }

}
