package ch.varani.jass.domain;

/**
 * The way the trump of a deal (<i>donne</i>) is chosen (rules 8 to 11).
 *
 * <p>The type of each deal of a game ({@link DealType}) belongs to one trump mode; the plan of the deals of an
 * edition ({@link DealPlan}) may combine them freely.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum TrumpMode {

  /** Rule 8: the trump suit is imposed by the score sheet (<i>atout imposé</i>). */
  IMPOSED_TRUMP,

  /**
   * Rule 9: the player who must choose the trump may pass the choice to their partner (<i>atout Schieber</i>, from
   * the Swiss German <i>schieben</i>, to push).
   */
  SCHIEBER,

  /**
   * Rule 10: the trump is the suit of a card shown after the first cut (<i>atout Kreuz</i>, Swiss German for
   * <i>cross</i>).
   */
  KREUZ,

  /** Rule 11: the player who must choose the trump announces it before the other players take their cards. */
  FORCED_TRUMP

}
