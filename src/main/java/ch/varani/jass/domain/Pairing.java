package ch.varani.jass.domain;

import java.util.List;

/**
 * A game of a schedule seen by the scoring, the same in both modes: its round and table, and the participants of each
 * side, the giver (place {@code 2 × table − 1}, column A of the score sheet, deals first, rule 6) and the receiver
 * (column B).
 *
 * <p>In team mode a side is one team (its number); in individual mode a side is the two partners formed by the solver
 * (their player numbers). Every participant of a side gets the points of the side.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param round the number of the round, from 1.
 * @param table the number of the table, from 1.
 * @param giver the participants of the giver side.
 * @param receiver the participants of the receiver side.
 */
public record Pairing(int round, int table, List<Integer> giver, List<Integer> receiver) {

  /**
   * Creates a pairing, copying the lists.
   *
   * @param round the number of the round.
   * @param table the number of the table.
   * @param giver the participants of the giver side.
   * @param receiver the participants of the receiver side.
   *
   * @throws NullPointerException if a list or one of its numbers is {@code null}.
   */
  public Pairing {
    giver = List.copyOf(giver);
    receiver = List.copyOf(receiver);
  }

}
