package ch.varani.jass.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rule 15: the outcome of the drawing of lots that breaks the ties of the ranking at the end of the games, done by the
 * application and stored in the edition, so that the ranking is stable and auditable.
 *
 * <p>The seed of the draw is recorded, so that the draw can be reproduced; the order gives, for the participants that
 * were tied, their order after the draw (the first one ranked first among the participants of its total). A tie is
 * broken only if every participant of the tie is in the order, i.e. if it was tied at the time of the draw.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param seed the seed of the pseudo-random generator of the draw.
 * @param order the numbers of the participants that were tied, in their order after the draw, each once.
 */
public record TieBreak(long seed, List<Integer> order) {

  /**
   * Creates the outcome of a draw, copying the order.
   *
   * @param seed the seed of the draw.
   * @param order the numbers of the participants, in their order after the draw.
   *
   * @throws NullPointerException if the order or one of its numbers is {@code null}.
   * @throws DomainException with {@link ValidationError#INVALID_TIE_BREAK} if a participant is twice in the order.
   */
  public TieBreak {
    order = List.copyOf(order);
    final Set<Integer> seen = new HashSet<>();
    for (final Integer participant : order) {
      if (!seen.add(participant)) {
        throw new DomainException(ValidationError.INVALID_TIE_BREAK);
      }
    }
  }

  /**
   * Returns the position of a participant in the draw.
   *
   * @param participant the number of the participant.
   *
   * @return the position, from 0, or -1 if the participant was not drawn.
   */
  public int positionOf(final int participant) {
    return order.indexOf(participant);
  }

}
