package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link Partners}: the order of the two players does not matter.
 */
final class PartnersTest {

  /** The number of a player. */
  private static final int SMALLER = 3;
  /** The number of another player, greater than {@link #SMALLER}. */
  private static final int GREATER = 7;

  /**
   * The numbers of the players are sorted, so that two teams of the same players are equal.
   */
  @Test
  @DisplayName("The players are sorted, so that the order does not matter")
  void orderTest() {
    final Partners sorted = new Partners(SMALLER, GREATER);
    final Partners reversed = new Partners(GREATER, SMALLER);
    assertAll(
        () -> assertEquals(SMALLER, reversed.first()),
        () -> assertEquals(GREATER, reversed.second()),
        () -> assertEquals(sorted, reversed));
  }

}
