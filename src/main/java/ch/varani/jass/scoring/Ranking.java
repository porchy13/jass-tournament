package ch.varani.jass.scoring;

import java.util.List;

/**
 * The ranking of an edition (rule 14), computed from its results, never stored.
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param entries the lines of the ranking, the best first; the excluded participants (rule 17) are not in it.
 * @param complete {@code true} if every game of the schedule has a result, i.e. the ranking is final.
 */
public record Ranking(List<RankingEntry> entries, boolean complete) {

  /**
   * Creates a ranking, copying its lines.
   *
   * @param entries the lines of the ranking.
   * @param complete whether every game has a result.
   */
  public Ranking {
    entries = List.copyOf(entries);
  }

  /**
   * Tells whether two participants share a rank, i.e. a drawing of lots is needed (rule 15) once the ranking is
   * complete.
   *
   * @return {@code true} if two lines have the same rank.
   */
  public boolean hasTies() {
    for (int i = 1; i < entries.size(); i++) {
      if (entries.get(i).rank() == entries.get(i - 1).rank()) {
        return true;
      }
    }
    return false;
  }

}
