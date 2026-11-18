package ch.varani.jass.app;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Regulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The plan of the deals being edited (F15), in the form of a new edition and in the content of the games of an open
 * edition: the type of each deal, changed deal by deal, or by blocks of {@link Regulation#DEALS_PER_BLOCK} deals so
 * that the plan stays valid (a positive multiple of 4 deals); the plan of the regulation can be restored at any time.
 *
 * <p>The class holds no JavaFX object: it is tested without the toolkit.</p>
 *
 * <p>Not thread-safe: confined to the JavaFX Application Thread.</p>
 */
final class DealPlanDraft {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The deals of the plan, in their order. */
  private final List<DealType> deals;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a draft starting from a plan.
   *
   * @param plan the initial plan.
   */
  DealPlanDraft(final DealPlan plan) {
    deals = new ArrayList<>(plan.deals());
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the deals of the plan.
   *
   * @return an unmodifiable copy of the deals, in their order.
   */
  List<DealType> deals() {
    return List.copyOf(deals);
  }

  /**
   * Returns the plan of the deals.
   *
   * @return the plan, always valid.
   */
  DealPlan plan() {
    return new DealPlan(deals);
  }

  // *******************************************************************************************************************
  // Changes
  // *******************************************************************************************************************

  /**
   * Changes the type of a deal.
   *
   * @param index the index of the deal, from 0.
   * @param type the new type.
   *
   * @return {@code true} if the type changed, {@code false} if the deal already had it.
   */
  boolean setDeal(final int index, final DealType type) {
    return deals.set(index, type) != type;
  }

  /**
   * Replaces the plan by the plan of the regulation.
   */
  void useRegulation() {
    deals.clear();
    deals.addAll(DealPlan.REGULATION.deals());
  }

  /**
   * Adds a block of {@link Regulation#DEALS_PER_BLOCK} deals of forced trump at the end of the plan.
   */
  void addBlock() {
    deals.addAll(Collections.nCopies(Regulation.DEALS_PER_BLOCK, DealType.FORCED_TRUMP));
  }

  /**
   * Removes the last block of {@link Regulation#DEALS_PER_BLOCK} deals, keeping at least one block.
   */
  void removeBlock() {
    if (deals.size() > Regulation.DEALS_PER_BLOCK) {
      deals.subList(deals.size() - Regulation.DEALS_PER_BLOCK, deals.size()).clear();
    }
  }

}
