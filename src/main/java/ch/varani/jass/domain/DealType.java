package ch.varani.jass.domain;

/**
 * The content of a deal (<i>donne</i>) of a game: its trump mode and, for an imposed trump, the trump suit (rules 8
 * to 11).
 *
 * <p>The suits are named here only; their symbols (♥ ♣ ♦ ♠) are drawn by the user interface and the documents.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum DealType {

  /** Rule 8: imposed trump, hearts (♥). */
  IMPOSED_HEARTS(TrumpMode.IMPOSED_TRUMP),

  /** Rule 8: imposed trump, clubs (♣). */
  IMPOSED_CLUBS(TrumpMode.IMPOSED_TRUMP),

  /** Rule 8: imposed trump, diamonds (♦). */
  IMPOSED_DIAMONDS(TrumpMode.IMPOSED_TRUMP),

  /** Rule 8: imposed trump, spades (♠). */
  IMPOSED_SPADES(TrumpMode.IMPOSED_TRUMP),

  /** Rule 9: <i>Schieber</i> trump. */
  SCHIEBER(TrumpMode.SCHIEBER),

  /** Rule 10: <i>Kreuz</i> trump. */
  KREUZ(TrumpMode.KREUZ),

  /** Rule 11: forced trump. */
  FORCED_TRUMP(TrumpMode.FORCED_TRUMP);

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The trump mode of the deal. */
  private final TrumpMode mode;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a type of deal.
   *
   * @param mode the trump mode of the deal.
   */
  DealType(final TrumpMode mode) {
    this.mode = mode;
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the trump mode of the deal; the four imposed trumps share theirs.
   *
   * @return the trump mode.
   */
  public TrumpMode mode() {
    return mode;
  }

}
