package ch.varani.jass.scoring;

import ch.varani.jass.domain.Pairing;

/**
 * The points of each side of a game, as counted in the ranking: from the score sheet (after the rule 16 correction),
 * or from a forfeit (absence, rule 19, or exclusion for cheating, rule 17).
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param pairing the game and the participants of its sides.
 * @param giverPoints the points of each participant of the giver side.
 * @param receiverPoints the points of each participant of the receiver side.
 * @param forfeit {@code true} if the points come from a forfeit, {@code false} if from the deals.
 */
public record GameResult(Pairing pairing, int giverPoints, int receiverPoints, boolean forfeit) {

  /**
   * Returns the points of a participant in this game.
   *
   * @param participant the number of the participant.
   *
   * @return the points of its side, or 0 if it does not play in this game.
   */
  public int pointsOf(final int participant) {
    if (pairing.giver().contains(participant)) {
      return giverPoints;
    }
    return pairing.receiver().contains(participant) ? receiverPoints : 0;
  }

  /**
   * Tells whether a participant plays in this game.
   *
   * @param participant the number of the participant.
   *
   * @return {@code true} if the participant is on one of the sides.
   */
  public boolean involves(final int participant) {
    return pairing.giver().contains(participant) || pairing.receiver().contains(participant);
  }

}
