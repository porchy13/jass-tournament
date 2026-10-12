package ch.varani.jass.app;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.ValidationError;

import java.util.regex.Pattern;

/**
 * The entry of a score sheet (F9), without the toolkit: the text shown in each column of a deal, the reading of a
 * typed text, the absent sides and the keeping of a sheet in the results.
 *
 * <p><b>Columns</b>: column A is the giver side (odd place, deals first, rule 6), column B the receiver side. A column
 * shows its typed value, or the value <i>derived</i> from the other column ({@code 157 − value}, rule 4), or nothing
 * when the deal is not played (rule 2). Typing a column makes it <i>manual</i>; leaving the text shown unchanged
 * keeps the column as it was, so that a derived value stays derived; emptying a column makes it derived again (or the
 * deal not played when the other column is empty too).</p>
 *
 * <p><b>Results</b>: a sheet where nothing is entered (every deal not played, both sides present) is not kept, so
 * that a game whose sheet is emptied has no result again.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class ScoreEntry {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** A typed value: one to three digits, the range being checked by {@link DealScore}. */
  private static final Pattern POINTS = Pattern.compile("\\d{1,3}");

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * A column of the score sheet.
   */
  enum Column {

    /** Column A: the giver side, which deals first (rule 6). */
    GIVER,
    /** Column B: the receiver side. */
    RECEIVER

  }

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private ScoreEntry() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns the text shown in a column of a deal.
   *
   * @param score the points of the deal.
   * @param column the column.
   *
   * @return the typed or derived value, or an empty text if the deal is not played.
   */
  static String text(final DealScore score, final Column column) {
    if (!score.isPlayed()) {
      return "";
    }
    return String.valueOf(column == Column.GIVER ? score.giverPoints() : score.receiverPoints());
  }

  /**
   * Tells whether a column of a deal shows a value derived from the other column.
   *
   * @param score the points of the deal.
   * @param column the column.
   *
   * @return {@code true} if the column is not typed and the other one is.
   */
  static boolean derived(final DealScore score, final Column column) {
    return column == Column.GIVER ? score.isGiverDerived() : score.isReceiverDerived();
  }

  /**
   * Reads the text typed in a column of a deal.
   *
   * <p>Algorithm: the text is stripped; if it is the text shown, the deal is unchanged (a derived value stays
   * derived); an empty text clears the column; otherwise the text must be a number of one to three digits, which
   * becomes the value of the column.</p>
   *
   * @param score the points of the deal before the entry.
   * @param column the column typed.
   * @param typed the text of the column.
   *
   * @return the points of the deal after the entry.
   *
   * @throws DomainException with {@link ValidationError#INVALID_POINTS} if the text is not a number between 0 and
   *                         157.
   */
  static DealScore enter(final DealScore score, final Column column, final String typed) {
    final String value = typed.strip();
    if (value.equals(text(score, column))) {
      return score;
    }
    Integer points = null;
    if (!value.isEmpty()) {
      if (!POINTS.matcher(value).matches()) {
        throw new DomainException(ValidationError.INVALID_POINTS);
      }
      points = Integer.valueOf(value);
    }
    return column == Column.GIVER ? new DealScore(points, score.receiver())
                                  : new DealScore(score.giver(), points);
  }

  /**
   * Returns the absent sides from the boxes of the sheet (rule 19).
   *
   * @param giver whether the giver side is absent.
   * @param receiver whether the receiver side is absent.
   *
   * @return the absent sides.
   */
  static Absence absence(final boolean giver, final boolean receiver) {
    if (giver) {
      return receiver ? Absence.BOTH : Absence.GIVER;
    }
    return receiver ? Absence.RECEIVER : Absence.NONE;
  }

  /**
   * Tells whether a side is absent.
   *
   * @param absence the absent sides of a sheet.
   * @param column the column of the side.
   *
   * @return {@code true} if the side of this column is absent.
   */
  static boolean absent(final Absence absence, final Column column) {
    return absence == Absence.BOTH || absence == (column == Column.GIVER ? Absence.GIVER : Absence.RECEIVER);
  }

  /**
   * Keeps a score sheet in the results: the sheet replaces the previous one of its game, or the game has no sheet
   * any more when nothing is entered on it.
   *
   * @param results the results.
   * @param sheet the sheet of a game.
   * @param plan the plan of the deals of the edition.
   *
   * @return the new results.
   */
  static Results keep(final Results results, final ScoreSheet sheet, final DealPlan plan) {
    if (sheet.equals(ScoreSheet.empty(sheet.round(), sheet.table(), plan))) {
      return results.withoutSheet(sheet.round(), sheet.table());
    }
    return results.withSheet(sheet);
  }

}
