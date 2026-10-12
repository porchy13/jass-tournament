package ch.varani.jass.domain;

import java.util.List;

/**
 * A round of the schedule: its number and the games played in it, one per table.
 *
 * <p>When the number of teams is odd, one team sits out each of the first rounds, and the last round is the catch-up
 * round played by these teams only (rule 1).</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param number the number of the round, starting at 1.
 * @param games the games of the round, sorted by table.
 */
public record TeamRound(int number, List<TeamGame> games) {

  /**
   * Creates a round, copying the list of games.
   *
   * @param number the number of the round, starting at 1.
   * @param games the games of the round, sorted by table.
   *
   * @throws NullPointerException if the list or one of its games is {@code null}.
   */
  public TeamRound {
    games = List.copyOf(games);
  }

}
