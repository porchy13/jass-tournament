package ch.varani.jass.scoring;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.ScoreSheet;

import java.util.ArrayList;
import java.util.List;

/**
 * The totals of a score sheet, computed from its deals, never stored: the subtotals of each block of
 * {@link Regulation#DEALS_PER_BLOCK} deals, the total of each side, and the rule 16 correction.
 *
 * <p><b>Algorithm</b>: the points of each deal (typed or derived, 0 if not played, rule 2) are summed per side, block
 * by block, then over the game. Rule 16: if the sum of both totals exceeds the points of a game of the plan
 * ({@link DealPlan#pointsPerGame()}, 2'512 for the regulation), each side loses half of the excess, rounded up; below
 * it, nothing is corrected (interrupted game, or an arithmetic error of the players, Q2). The deals whose columns do
 * not sum to {@link Regulation#POINTS_PER_DEAL} are listed, to be highlighted.</p>
 *
 * <p>The absences and exclusions are not taken into account here: see {@link Scoring}.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param giverSubtotals the subtotals of the giver side (column A), one per block.
 * @param receiverSubtotals the subtotals of the receiver side (column B), one per block.
 * @param giverTotal the total of the giver side, before correction.
 * @param receiverTotal the total of the receiver side, before correction.
 * @param deduction the points deducted from each side by rule 16, 0 if the total does not exceed a game.
 * @param inconsistentDeals the numbers of the deals (from 1) whose columns do not sum to the points of a deal.
 */
public record SheetTotals(List<Integer> giverSubtotals, List<Integer> receiverSubtotals, int giverTotal,
                          int receiverTotal, int deduction, List<Integer> inconsistentDeals) {

  /**
   * Creates totals, copying the lists.
   *
   * @param giverSubtotals the subtotals of the giver side.
   * @param receiverSubtotals the subtotals of the receiver side.
   * @param giverTotal the total of the giver side.
   * @param receiverTotal the total of the receiver side.
   * @param deduction the rule 16 deduction of each side.
   * @param inconsistentDeals the numbers of the inconsistent deals.
   */
  public SheetTotals {
    giverSubtotals = List.copyOf(giverSubtotals);
    receiverSubtotals = List.copyOf(receiverSubtotals);
    inconsistentDeals = List.copyOf(inconsistentDeals);
  }

  /**
   * Computes the totals of a score sheet.
   *
   * @param sheet the score sheet, with one value per deal of the plan.
   * @param plan the plan of the deals of the edition.
   *
   * @return the totals.
   */
  public static SheetTotals of(final ScoreSheet sheet, final DealPlan plan) {
    final List<Integer> giverSubtotals = new ArrayList<>();
    final List<Integer> receiverSubtotals = new ArrayList<>();
    final List<Integer> inconsistent = new ArrayList<>();
    int giverTotal = 0;
    int receiverTotal = 0;
    for (int block = 0; block < plan.blocks(); block++) {
      int giverBlock = 0;
      int receiverBlock = 0;
      for (int deal = block * Regulation.DEALS_PER_BLOCK; deal < (block + 1) * Regulation.DEALS_PER_BLOCK; deal++) {
        final DealScore score = sheet.deals().get(deal);
        giverBlock += score.giverPoints();
        receiverBlock += score.receiverPoints();
        if (!score.isConsistent()) {
          inconsistent.add(deal + 1);
        }
      }
      giverSubtotals.add(giverBlock);
      receiverSubtotals.add(receiverBlock);
      giverTotal += giverBlock;
      receiverTotal += receiverBlock;
    }
    final int excess = giverTotal + receiverTotal - plan.pointsPerGame();
    final int deduction = excess > 0 ? (excess + 1) / 2 : 0;
    return new SheetTotals(giverSubtotals, receiverSubtotals, giverTotal, receiverTotal, deduction, inconsistent);
  }

  /**
   * Returns the points of the giver side after the rule 16 correction.
   *
   * @return the total of the giver side minus the deduction.
   */
  public int giverPoints() {
    return giverTotal - deduction;
  }

  /**
   * Returns the points of the receiver side after the rule 16 correction.
   *
   * @return the total of the receiver side minus the deduction.
   */
  public int receiverPoints() {
    return receiverTotal - deduction;
  }

}
