package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link ScoreSheet}: empty sheet, change of a deal and of the absence.
 */
final class ScoreSheetTest {

  /** The number of a round. */
  private static final int ROUND = 2;
  /** The number of a table. */
  private static final int TABLE = 3;
  /** Points typed in column A. */
  private static final int POINTS = 100;

  /**
   * An empty sheet has one deal not played per deal of the plan, and both sides present.
   */
  @Test
  @DisplayName("An empty sheet has every deal not played and both sides present")
  void emptyTest() {
    final ScoreSheet sheet = ScoreSheet.empty(ROUND, TABLE, DealPlan.REGULATION);
    assertAll(
        () -> assertEquals(Regulation.DEALS_PER_GAME, sheet.deals().size()),
        () -> assertTrue(sheet.deals().stream().noneMatch(DealScore::isPlayed)),
        () -> assertEquals(Absence.NONE, sheet.absence()),
        () -> assertTrue(sheet.isFor(ROUND, TABLE)),
        () -> assertFalse(sheet.isFor(ROUND, TABLE + 1)),
        () -> assertFalse(sheet.isFor(ROUND + 1, TABLE)));
  }

  /**
   * A deal and the absence can be changed; the other values are kept.
   */
  @Test
  @DisplayName("A deal and the absence can be changed")
  void withTest() {
    final ScoreSheet sheet = ScoreSheet.empty(ROUND, TABLE, DealPlan.REGULATION);
    final ScoreSheet changed = sheet.withDeal(2, DealScore.of(POINTS)).withAbsence(Absence.RECEIVER);
    assertAll(
        () -> assertEquals(DealScore.of(POINTS), changed.deals().get(1)),
        () -> assertEquals(DealScore.NOT_PLAYED, changed.deals().get(0)),
        () -> assertEquals(Absence.RECEIVER, changed.absence()),
        () -> assertEquals(DealScore.NOT_PLAYED, sheet.deals().get(1)),
        () -> assertThrows(IndexOutOfBoundsException.class, () -> sheet.withDeal(0, DealScore.NOT_PLAYED)),
        () -> assertThrows(NullPointerException.class, () -> sheet.withDeal(1, null)),
        () -> assertThrows(NullPointerException.class, () -> new ScoreSheet(1, 1, List.of(), null)));
  }

}
