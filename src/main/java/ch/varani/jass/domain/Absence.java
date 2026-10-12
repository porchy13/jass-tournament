package ch.varani.jass.domain;

/**
 * The sides of a game that are absent (rule 19), marked on its score sheet. In individual mode, the absence of a
 * player makes the whole side (the player and the partner given by the solver) absent, as decided by the maintainer.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum Absence {

  /** Both sides are present: the game is scored from its deals. */
  NONE,

  /** The giver side is absent: it gets 0 points, the receiver side half of a game (rule 19). */
  GIVER,

  /** The receiver side is absent: it gets 0 points, the giver side half of a game (rule 19). */
  RECEIVER,

  /** Both sides are absent: both get 0 points. */
  BOTH

}
