package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link TieBreak}: positions of the participants and rejection of a participant drawn twice.
 */
final class TieBreakTest {

  /** A participant. */
  private static final int SEVEN = 7;
  /** A participant. */
  private static final int THREE = 3;

  /**
   * Rule 15: the position of a participant is its place in the order of the draw, -1 if not drawn.
   */
  @Test
  @DisplayName("Rule 15: the draw gives the order of the tied participants")
  void positionTest() {
    final TieBreak tieBreak = new TieBreak(1L, List.of(SEVEN, THREE));
    assertAll(
        () -> assertEquals(0, tieBreak.positionOf(SEVEN)),
        () -> assertEquals(1, tieBreak.positionOf(THREE)),
        () -> assertEquals(-1, tieBreak.positionOf(1)),
        () -> assertEquals(ValidationError.INVALID_TIE_BREAK,
                           assertThrows(DomainException.class, () -> new TieBreak(1L, List.of(SEVEN, SEVEN)))
                               .getError()));
  }

}
