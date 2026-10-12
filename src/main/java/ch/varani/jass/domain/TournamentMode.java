package ch.varani.jass.domain;

/**
 * The mode of a tournament, chosen when an edition is created.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum TournamentMode {

  /** The players register by teams of two, and the ranking is per team. */
  TEAM,

  /** The players register alone, the solver builds the teams, and the ranking is per player. */
  INDIVIDUAL

}
