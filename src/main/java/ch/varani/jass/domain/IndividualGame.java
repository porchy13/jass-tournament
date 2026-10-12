package ch.varani.jass.domain;

import java.util.Objects;

/**
 * A game (<i>partie</i>) of a round of the {@link TournamentMode#INDIVIDUAL} mode, played at a table by two teams of
 * two players formed by the solver.
 *
 * <p>As in team mode ({@link TeamGame}), table {@code k} has the places {@code 2k − 1} and {@code 2k}: the
 * <i>giver</i>, the team that deals first, sits at the smaller place (rule 6), the <i>receiver</i> at the greater
 * place.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param table the number of the table, starting at 1.
 * @param giver the team that deals first (rule 6).
 * @param receiver the other team.
 */
public record IndividualGame(int table, Partners giver, Partners receiver) {

  /**
   * Creates a game.
   *
   * @param table the number of the table, starting at 1.
   * @param giver the team that deals first.
   * @param receiver the other team.
   *
   * @throws NullPointerException if a team is {@code null}.
   */
  public IndividualGame {
    Objects.requireNonNull(giver, "giver");
    Objects.requireNonNull(receiver, "receiver");
  }

  /**
   * Returns the place of the giver at the table, the smaller of the two places (rule 6).
   *
   * @return the place {@code 2 × table − 1}.
   */
  public int giverPlace() {
    return 2 * table - 1;
  }

  /**
   * Returns the place of the receiver at the table, the greater of the two places.
   *
   * @return the place {@code 2 × table}.
   */
  public int receiverPlace() {
    return 2 * table;
  }

}
