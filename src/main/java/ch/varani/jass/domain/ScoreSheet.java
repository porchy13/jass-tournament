package ch.varani.jass.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The score sheet of one game (rule 7), as entered by the user: the game (round and table of the schedule), the
 * points of each deal of the plan of the edition, and the absent sides (rule 19).
 *
 * <p>Only the raw input is kept: the subtotals, the totals, the rule 16 correction and the forfeits are computed by
 * the scoring ({@code ch.varani.jass.scoring}), never stored. A sheet of an absent side may leave its deals not
 * played.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param round the number of the round of the game.
 * @param table the number of the table of the game.
 * @param deals the points of the deals, one per deal of the plan of the edition, in their order.
 * @param absence the absent sides.
 */
public record ScoreSheet(int round, int table, List<DealScore> deals, Absence absence) {

  /**
   * Creates a score sheet, copying the deals.
   *
   * @param round the number of the round of the game.
   * @param table the number of the table of the game.
   * @param deals the points of the deals.
   * @param absence the absent sides.
   *
   * @throws NullPointerException if the deals, one of them or the absence is {@code null}.
   */
  public ScoreSheet {
    deals = List.copyOf(deals);
    Objects.requireNonNull(absence, "absence");
  }

  /**
   * Creates an empty score sheet: every deal not played, both sides present.
   *
   * @param round the number of the round of the game.
   * @param table the number of the table of the game.
   * @param plan the plan of the deals of the edition.
   *
   * @return the empty sheet.
   */
  public static ScoreSheet empty(final int round, final int table, final DealPlan plan) {
    return new ScoreSheet(round, table, Collections.nCopies(plan.size(), DealScore.NOT_PLAYED), Absence.NONE);
  }

  /**
   * Tells whether this sheet is the one of a game.
   *
   * @param otherRound the number of the round.
   * @param otherTable the number of the table.
   *
   * @return {@code true} if the sheet has this round and this table.
   */
  public boolean isFor(final int otherRound, final int otherTable) {
    return round == otherRound && table == otherTable;
  }

  /**
   * Returns a copy of this sheet with the points of one deal.
   *
   * @param deal the number of the deal, from 1.
   * @param score the points of the deal.
   *
   * @return the new sheet.
   *
   * @throws IndexOutOfBoundsException if there is no deal with this number.
   */
  public ScoreSheet withDeal(final int deal, final DealScore score) {
    final List<DealScore> changed = new ArrayList<>(deals);
    changed.set(deal - 1, Objects.requireNonNull(score, "score"));
    return new ScoreSheet(round, table, changed, absence);
  }

  /**
   * Returns a copy of this sheet with other absent sides.
   *
   * @param newAbsence the absent sides.
   *
   * @return the new sheet.
   */
  public ScoreSheet withAbsence(final Absence newAbsence) {
    return new ScoreSheet(round, table, deals, newAbsence);
  }

}
