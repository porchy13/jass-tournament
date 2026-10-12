package ch.varani.jass.domain;

/**
 * A game (<i>partie</i>) of a round, played by two teams at a table.
 *
 * <p>Table {@code k} has the places {@code 2k − 1} and {@code 2k}. The <i>giver</i>, the team that deals first, sits
 * at the smaller place (rule 6); the other team, the <i>receiver</i>, sits at the greater place. The teams are
 * identified by their number in the registrations ({@link TeamRegistrations#get(int)}).</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param table the number of the table, starting at 1.
 * @param giver the number of the team that deals first (rule 6).
 * @param receiver the number of the other team.
 */
public record TeamGame(int table, int giver, int receiver) {

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
