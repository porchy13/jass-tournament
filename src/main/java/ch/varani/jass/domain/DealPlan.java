package ch.varani.jass.domain;

import java.util.List;
import java.util.Objects;

/**
 * The content of the games of an edition: the type of each deal (<i>donne</i>), in the order in which they are played.
 * Every game of the edition follows the same plan.
 *
 * <p>The regulation ({@link #REGULATION}) defines 16 deals (rule 3): 4 deals of imposed trump ♥ ♣ ♦ ♠ (rule 8), then
 * 4 deals of <i>Schieber</i> (rule 9), 4 of <i>Kreuz</i> (rule 10) and 4 of forced trump (rule 11). Other
 * tournaments organise their games otherwise, e.g. with forced trump only: the plan can hold any sequence of
 * deals.</p>
 *
 * <p><b>Rule of the plan</b> (decided by the maintainer, not in the regulation): the number of deals is a positive
 * multiple of {@link Regulation#DEALS_PER_BLOCK}. The deal passes from one player to the next at each deal, so that
 * each of the four players at the table starts the same number of deals.</p>
 *
 * <p><b>Derived values</b>, never stored:</p>
 * <ul>
 *   <li>the blocks ({@link #blocks()}): the groups of {@link Regulation#DEALS_PER_BLOCK} consecutive deals, each
 *       closed by a subtotal on the score sheet, to ease the additions of the players. How a block is labelled on
 *       the screen and in the documents is a matter of presentation, not of this model;</li>
 *   <li>the points of a game ({@link #pointsPerGame()}, rule 5) and of a forfeit ({@link #forfeitPoints()}, rules 17
 *       and 19, half of a game).</li>
 * </ul>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param deals the types of the deals, in their order; a positive multiple of {@link Regulation#DEALS_PER_BLOCK}.
 */
public record DealPlan(List<DealType> deals) {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** Rules 3 and 8 to 11: the plan of the regulation, 16 deals in four blocks of four. */
  public static final DealPlan REGULATION = new DealPlan(List.of(
      DealType.IMPOSED_HEARTS, DealType.IMPOSED_CLUBS, DealType.IMPOSED_DIAMONDS, DealType.IMPOSED_SPADES,
      DealType.SCHIEBER, DealType.SCHIEBER, DealType.SCHIEBER, DealType.SCHIEBER,
      DealType.KREUZ, DealType.KREUZ, DealType.KREUZ, DealType.KREUZ,
      DealType.FORCED_TRUMP, DealType.FORCED_TRUMP, DealType.FORCED_TRUMP, DealType.FORCED_TRUMP));

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a plan, checking its number of deals.
   *
   * @param deals the types of the deals, in their order.
   *
   * @throws NullPointerException if the list or one of its deals is {@code null}.
   * @throws DomainException with {@link ValidationError#INVALID_DEALS} if the number of deals is not a positive
   *                         multiple of {@link Regulation#DEALS_PER_BLOCK}.
   */
  public DealPlan {
    deals = List.copyOf(Objects.requireNonNull(deals, "deals"));
    if (deals.isEmpty() || deals.size() % Regulation.DEALS_PER_BLOCK != 0) {
      throw new DomainException(ValidationError.INVALID_DEALS);
    }
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the number of deals of a game.
   *
   * @return the number of deals, a positive multiple of {@link Regulation#DEALS_PER_BLOCK}.
   */
  public int size() {
    return deals.size();
  }

  /**
   * Returns the number of blocks of a game: groups of {@link Regulation#DEALS_PER_BLOCK} consecutive deals, each
   * closed by a subtotal of the score sheet.
   *
   * @return the number of deals divided by {@link Regulation#DEALS_PER_BLOCK}.
   */
  public int blocks() {
    return deals.size() / Regulation.DEALS_PER_BLOCK;
  }

  /**
   * Rule 5: returns the total of the points of a game, the number of deals times {@link Regulation#POINTS_PER_DEAL}.
   *
   * @return the points of a game ({@link Regulation#POINTS_PER_GAME} for the plan of the regulation).
   */
  public int pointsPerGame() {
    return deals.size() * Regulation.POINTS_PER_DEAL;
  }

  /**
   * Rules 17 and 19: returns the points given to the opponents of a cheating or absent team, half of a game; an
   * integer, as the number of deals is even.
   *
   * @return the points of a forfeit ({@link Regulation#FORFEIT_POINTS} for the plan of the regulation).
   */
  public int forfeitPoints() {
    return pointsPerGame() / 2;
  }

}
