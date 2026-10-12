package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link DealScore}: derived and manual columns, deals not played, range of the values and consistency.
 */
final class DealScoreTest {

  /** Points typed in column A. */
  private static final int A = 113;
  /** Points of column B derived from {@link #A}. */
  private static final int B = 44;
  /** Points written by mistake in column B. */
  private static final int WRONG_B = 50;

  /**
   * Rule 4: column B is derived from column A as 157 minus A, until it is typed.
   */
  @Test
  @DisplayName("Rule 4: column B is derived as 157 minus column A")
  void derivedTest() {
    final DealScore score = DealScore.of(A);
    assertAll(
        () -> assertEquals(A, score.giverPoints()),
        () -> assertEquals(B, score.receiverPoints()),
        () -> assertTrue(score.isReceiverDerived()),
        () -> assertFalse(score.isGiverDerived()),
        () -> assertTrue(score.isConsistent()),
        () -> assertTrue(score.isPlayed()));
  }

  /**
   * A column typed alone in B derives column A.
   */
  @Test
  @DisplayName("Column A is derived from column B when only B is typed")
  void giverDerivedTest() {
    final DealScore score = new DealScore(null, B);
    assertAll(
        () -> assertEquals(A, score.giverPoints()),
        () -> assertTrue(score.isPlayed()),
        () -> assertTrue(score.isGiverDerived()),
        () -> assertFalse(score.isReceiverDerived()));
  }

  /**
   * A manual column B is kept as written, even when the sum differs from 157: the deal is then inconsistent.
   */
  @Test
  @DisplayName("A manual column B is kept as written; a wrong sum is inconsistent, not rejected")
  void manualTest() {
    final DealScore score = new DealScore(A, WRONG_B);
    assertAll(
        () -> assertEquals(WRONG_B, score.receiverPoints()),
        () -> assertFalse(score.isReceiverDerived()),
        () -> assertFalse(score.isConsistent()),
        () -> assertTrue(new DealScore(A, B).isConsistent()));
  }

  /**
   * Rule 2: a deal not played counts 0 for both sides and is consistent.
   */
  @Test
  @DisplayName("Rule 2: a deal not played counts 0 for both sides")
  void notPlayedTest() {
    final DealScore score = DealScore.NOT_PLAYED;
    assertAll(
        () -> assertFalse(score.isPlayed()),
        () -> assertEquals(0, score.giverPoints()),
        () -> assertEquals(0, score.receiverPoints()),
        () -> assertFalse(score.isGiverDerived()),
        () -> assertFalse(score.isReceiverDerived()),
        () -> assertTrue(score.isConsistent()),
        () -> assertTrue(new DealScore(0, 0).isPlayed()));
  }

  /**
   * Rule 4: a typed value is between 0 and 157.
   */
  @Test
  @DisplayName("Rule 4: a typed value is between 0 and 157")
  void rangeTest() {
    final int over = Regulation.POINTS_PER_DEAL + 1;
    assertAll(
        () -> assertEquals(Regulation.POINTS_PER_DEAL, DealScore.of(Regulation.POINTS_PER_DEAL).giverPoints()),
        () -> assertEquals(ValidationError.INVALID_POINTS,
                           assertThrows(DomainException.class, () -> DealScore.of(-1)).getError()),
        () -> assertEquals(ValidationError.INVALID_POINTS,
                           assertThrows(DomainException.class, () -> DealScore.of(over)).getError()),
        () -> assertEquals(ValidationError.INVALID_POINTS,
                           assertThrows(DomainException.class, () -> new DealScore(0, -1)).getError()),
        () -> assertEquals(ValidationError.INVALID_POINTS,
                           assertThrows(DomainException.class, () -> new DealScore(null, over)).getError()));
  }

}
