package ch.varani.jass.scoring;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.ScoreSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link SheetTotals}: subtotals, totals and rule 16 correction, on the complete score sheet of the 2025
 * edition (sheet "Contrôle des points" of the former spreadsheet, team numbers only).
 */
final class SheetTotalsTest {

  /** The points of column A of a complete score sheet of the 2025 edition. */
  static final List<Integer> SHEET_2025 = List.of(113, 76, 94, 79, 43, 122, 81, 120, 50, 29, 33, 108, 124, 143, 69,
                                                  127);
  /** The subtotals of column A of {@link #SHEET_2025}. */
  private static final List<Integer> GIVER_SUBTOTALS_2025 = List.of(362, 366, 220, 463);
  /** The subtotals of column B of {@link #SHEET_2025}. */
  private static final List<Integer> RECEIVER_SUBTOTALS_2025 = List.of(266, 262, 408, 165);
  /** The subtotal of the last block of column A of {@link #SHEET_2025}. */
  private static final int LAST_BLOCK_2025 = 463;
  /** The subtotals of column A of {@link #SHEET_2025} when the last block is not played. */
  private static final List<Integer> INTERRUPTED_SUBTOTALS_2025 = List.of(362, 366, 220, 0);
  /** The total of column A of {@link #SHEET_2025}. */
  private static final int GIVER_2025 = 1_411;
  /** The total of column B of {@link #SHEET_2025}. */
  private static final int RECEIVER_2025 = 1_101;
  /** The excess of a sheet totalling 2'515 points. */
  private static final int EXCESS = 3;
  /** The deduction of each side for an excess of {@link #EXCESS}: half of it, rounded up. */
  private static final int DEDUCTION = 2;

  /**
   * Creates a sheet where column A is typed and column B derived.
   *
   * @param giver the points of column A, one per deal.
   *
   * @return the sheet of round 1, table 1.
   */
  static ScoreSheet sheet(final List<Integer> giver) {
    return new ScoreSheet(1, 1, giver.stream().map(DealScore::of).toList(), Absence.NONE);
  }

  /**
   * Rules 5, 8 to 11: the 2025 sheet gives the subtotals of the four blocks and totals 2'512 points, without
   * correction.
   */
  @Test
  @DisplayName("Rules 5, 8 to 11: the 2025 sheet gives 362, 366, 220, 463 and 1'411 against 1'101")
  void sheet2025Test() {
    final SheetTotals totals = SheetTotals.of(sheet(SHEET_2025), DealPlan.REGULATION);
    assertAll(
        () -> assertEquals(GIVER_SUBTOTALS_2025, totals.giverSubtotals()),
        () -> assertEquals(RECEIVER_SUBTOTALS_2025, totals.receiverSubtotals()),
        () -> assertEquals(GIVER_2025, totals.giverTotal()),
        () -> assertEquals(RECEIVER_2025, totals.receiverTotal()),
        () -> assertEquals(0, totals.deduction()),
        () -> assertEquals(GIVER_2025, totals.giverPoints()),
        () -> assertEquals(RECEIVER_2025, totals.receiverPoints()),
        () -> assertEquals(List.of(), totals.inconsistentDeals()));
  }

  /**
   * Rule 16: a total of 2'515 points (excess 3) costs each side half of the excess, rounded up: 2 points (Q3).
   */
  @Test
  @DisplayName("Rule 16: the excess over 2'512 points is split between both teams, rounded up")
  void rule16OddTest() {
    final ScoreSheet sheet = sheet(SHEET_2025).withDeal(1, new DealScore(SHEET_2025.getFirst(),
                                                                         Regulation.POINTS_PER_DEAL
                                                                         - SHEET_2025.getFirst() + EXCESS));
    final SheetTotals totals = SheetTotals.of(sheet, DealPlan.REGULATION);
    assertAll(
        () -> assertEquals(DEDUCTION, totals.deduction()),
        () -> assertEquals(GIVER_2025 - DEDUCTION, totals.giverPoints()),
        () -> assertEquals(RECEIVER_2025 + EXCESS - DEDUCTION, totals.receiverPoints()),
        () -> assertEquals(List.of(1), totals.inconsistentDeals()));
  }

  /**
   * Rule 16: an even excess is split exactly; an excess of 1 costs each side 1 point.
   */
  @Test
  @DisplayName("Rule 16: an even excess is split exactly, an excess of 1 costs 1 point to each team")
  void rule16EvenTest() {
    final ScoreSheet four = sheet(SHEET_2025).withDeal(2, new DealScore(SHEET_2025.get(1) + 2,
                                                                        Regulation.POINTS_PER_DEAL
                                                                        - SHEET_2025.get(1) + 2));
    final ScoreSheet one = sheet(SHEET_2025).withDeal(2, new DealScore(SHEET_2025.get(1) + 1,
                                                                       Regulation.POINTS_PER_DEAL
                                                                       - SHEET_2025.get(1)));
    assertAll(
        () -> assertEquals(2, SheetTotals.of(four, DealPlan.REGULATION).deduction()),
        () -> assertEquals(1, SheetTotals.of(one, DealPlan.REGULATION).deduction()));
  }

  /**
   * Rules 2 and 16: an interrupted game counts the completed deals only, without correction below 2'512 (Q2).
   */
  @Test
  @DisplayName("Rule 2: an interrupted game counts the completed deals only, without correction")
  void interruptedTest() {
    final List<DealScore> deals = new ArrayList<>(sheet(SHEET_2025).deals());
    for (int deal = Regulation.DEALS_PER_GAME - Regulation.DEALS_PER_BLOCK; deal < Regulation.DEALS_PER_GAME; deal++) {
      deals.set(deal, DealScore.NOT_PLAYED);
    }
    final SheetTotals totals = SheetTotals.of(new ScoreSheet(1, 1, deals, Absence.NONE), DealPlan.REGULATION);
    assertAll(
        () -> assertEquals(INTERRUPTED_SUBTOTALS_2025, totals.giverSubtotals()),
        () -> assertEquals(0, totals.deduction()),
        () -> assertEquals(GIVER_2025 - LAST_BLOCK_2025, totals.giverPoints()));
  }

  /**
   * Another plan of deals has its own number of blocks and its own points per game for rule 16.
   */
  @Test
  @DisplayName("Another plan of deals has its own blocks and its own points per game")
  void otherPlanTest() {
    final int deals = 2 * Regulation.DEALS_PER_BLOCK;
    final DealPlan plan = new DealPlan(Collections.nCopies(deals, DealType.FORCED_TRUMP));
    final List<Integer> giver = IntStream.range(0, deals).mapToObj(deal -> Regulation.POINTS_PER_DEAL).toList();
    final ScoreSheet sheet = sheet(giver).withDeal(1, new DealScore(Regulation.POINTS_PER_DEAL, 2));
    final SheetTotals totals = SheetTotals.of(sheet, plan);
    assertAll(
        () -> assertEquals(2, totals.giverSubtotals().size()),
        () -> assertEquals(1, totals.deduction()),
        () -> assertEquals(plan.pointsPerGame() - 1, totals.giverPoints()),
        () -> assertEquals(1, totals.receiverPoints()));
  }

}
