package ch.varani.jass.domain;

import java.util.List;

/**
 * A round of the schedule of the {@link TournamentMode#INDIVIDUAL} mode: its number and the games played in it, one
 * per table.
 *
 * <p>When the number of players is not a multiple of {@link Regulation#PLAYERS_PER_TABLE}, the surplus players sit
 * out each of the first rounds, and the last round is the catch-up round played by these players only, as rule 1
 * does for the teams.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param number the number of the round, starting at 1.
 * @param games the games of the round, sorted by table.
 */
public record IndividualRound(int number, List<IndividualGame> games) {

  /**
   * Creates a round, copying the list of games.
   *
   * @param number the number of the round, starting at 1.
   * @param games the games of the round, sorted by table.
   *
   * @throws NullPointerException if the list or one of its games is {@code null}.
   */
  public IndividualRound {
    games = List.copyOf(games);
  }

}
