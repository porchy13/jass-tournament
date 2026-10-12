package ch.varani.jass.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The results of an edition as entered by the user: the score sheets of the games, the participants excluded for
 * cheating (rule 17) and the outcome of the drawing of lots (rule 15).
 *
 * <p>Only the raw input is kept; the totals and the ranking are computed by the scoring
 * ({@code ch.varani.jass.scoring}). {@link Edition} checks that the results match its schedule, its plan of the deals
 * and its registrations.</p>
 *
 * <p>Every modification returns a new instance. Immutable, safe for concurrent use.</p>
 *
 * @param sheets the score sheets, at most one per game, sorted by round then table.
 * @param excluded the numbers of the participants excluded for cheating (teams, or players in individual mode),
 *                 sorted, each once.
 * @param tieBreak the outcome of the drawing of lots, or {@code null} if no draw was done.
 */
public record Results(List<ScoreSheet> sheets, List<Integer> excluded, TieBreak tieBreak) {

  /** The results of an edition where nothing has been entered. */
  public static final Results EMPTY = new Results(List.of(), List.of(), null);

  /**
   * Creates results, sorting the sheets and the excluded participants.
   *
   * @param sheets the score sheets.
   * @param excluded the numbers of the excluded participants.
   * @param tieBreak the outcome of the drawing of lots, or {@code null}.
   *
   * @throws NullPointerException if a list or one of its elements is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_SHEET} if two sheets are for the same game.
   */
  public Results {
    sheets = sheets.stream().sorted(Comparator.comparingInt(ScoreSheet::round)
                                              .thenComparingInt(ScoreSheet::table)).toList();
    for (int i = 1; i < sheets.size(); i++) {
      if (sheets.get(i).isFor(sheets.get(i - 1).round(), sheets.get(i - 1).table())) {
        throw new DomainException(ValidationError.DUPLICATE_SHEET);
      }
    }
    excluded = List.copyOf(new TreeSet<>(excluded));
  }

  /**
   * Tells whether nothing has been entered.
   *
   * @return {@code true} if there is no sheet, no exclusion and no draw.
   */
  public boolean isEmpty() {
    return sheets.isEmpty() && excluded.isEmpty() && tieBreak == null;
  }

  /**
   * Returns the score sheet of a game.
   *
   * @param round the number of the round.
   * @param table the number of the table.
   *
   * @return the sheet, or nothing if it has not been entered.
   */
  public Optional<ScoreSheet> sheet(final int round, final int table) {
    return sheets.stream().filter(sheet -> sheet.isFor(round, table)).findFirst();
  }

  /**
   * Returns a copy of these results with the score sheet of a game, replacing its previous sheet.
   *
   * @param sheet the sheet.
   *
   * @return the new results.
   */
  public Results withSheet(final ScoreSheet sheet) {
    final List<ScoreSheet> changed = new ArrayList<>(withoutSheet(sheet.round(), sheet.table()).sheets);
    changed.add(sheet);
    return new Results(changed, excluded, tieBreak);
  }

  /**
   * Returns a copy of these results without the score sheet of a game.
   *
   * @param round the number of the round.
   * @param table the number of the table.
   *
   * @return the new results.
   */
  public Results withoutSheet(final int round, final int table) {
    return new Results(sheets.stream().filter(sheet -> !sheet.isFor(round, table)).toList(), excluded, tieBreak);
  }

  /**
   * Returns a copy of these results where a participant is excluded for cheating (rule 17), or no longer excluded.
   *
   * @param participant the number of the team, or of the player in individual mode.
   * @param isExcluded {@code true} to exclude the participant, {@code false} to cancel the exclusion.
   *
   * @return the new results.
   */
  public Results withExclusion(final int participant, final boolean isExcluded) {
    final TreeSet<Integer> changed = new TreeSet<>(excluded);
    if (isExcluded) {
      changed.add(participant);
    } else {
      changed.remove(participant);
    }
    return new Results(sheets, List.copyOf(changed), tieBreak);
  }

  /**
   * Returns a copy of these results with the outcome of a drawing of lots.
   *
   * @param newTieBreak the outcome of the draw, or {@code null} to cancel it.
   *
   * @return the new results.
   */
  public Results withTieBreak(final TieBreak newTieBreak) {
    return new Results(sheets, excluded, newTieBreak);
  }

}
