package ch.varani.jass.domain;

/**
 * The constants of the regulation of the <i>match aux cartes</i> ({@code doc/mac_reglement_2025.pdf}), each citing
 * the rule it comes from.
 *
 * <p>Every number of the regulation used by the application is defined here and nowhere else.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class Regulation {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** Rule 1: the number of games (<i>parties</i>) each participant plays. */
  public static final int GAMES = 4;
  /**
   * Rule 3: the number of deals (<i>donnes</i>) of a game of the regulation; an edition may define another plan of
   * deals ({@link DealPlan}).
   */
  public static final int DEALS_PER_GAME = 16;
  /** Rule 4: the total of the points of a deal (no announce is valid, not even the Stöck). */
  public static final int POINTS_PER_DEAL = 157;
  /**
   * Rule 5: the total of the points of a game, i.e. {@link #DEALS_PER_GAME} × {@link #POINTS_PER_DEAL}; see
   * {@link DealPlan#pointsPerGame()} for another plan of deals.
   */
  public static final int POINTS_PER_GAME = 2_512;
  /**
   * The number of deals of a block, closed by a subtotal on the score sheet (four blocks of the regulation, rules 8 to
   * 11). Decided by the maintainer: the number of deals of a game is a multiple of it, so that each of the
   * {@link #PLAYERS_PER_TABLE} players at a table starts the same number of deals ({@link DealPlan}).
   */
  public static final int DEALS_PER_BLOCK = 4;
  /** The total of the points of a block, i.e. {@link #DEALS_PER_BLOCK} × {@link #POINTS_PER_DEAL}. */
  public static final int POINTS_PER_BLOCK = 628;
  /**
   * Rules 17 and 19: the points given to the opponents of a cheating or absent team, half of a game of the regulation;
   * see {@link DealPlan#forfeitPoints()} for another plan of deals.
   */
  public static final int FORFEIT_POINTS = 1_256;
  /** Team mode: the number of players of a team. */
  public static final int PLAYERS_PER_TEAM = 2;
  /** Individual mode: the number of players at a table, two teams of {@link #PLAYERS_PER_TEAM} players. */
  public static final int PLAYERS_PER_TABLE = 4;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private Regulation() {
  }

}
