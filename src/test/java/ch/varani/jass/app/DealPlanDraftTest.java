package ch.varani.jass.app;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Regulation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link DealPlanDraft}: the plan of the deals edited deal by deal, by blocks of four deals, or back to the
 * plan of the regulation.
 */
final class DealPlanDraftTest {

  /**
   * The plan is edited by blocks of four deals, at least one block, and the plan of the regulation can be restored.
   */
  @Test
  @DisplayName("The plan is edited by blocks of four deals")
  void blocksTest() {
    final DealPlanDraft draft = new DealPlanDraft(DealPlan.REGULATION);
    draft.addBlock();
    final int added = draft.deals().size();
    final DealType last = draft.deals().getLast();
    for (int i = 0; i < Regulation.DEALS_PER_GAME; i++) {
      draft.removeBlock();
    }
    final int minimum = draft.deals().size();
    draft.useRegulation();
    final List<DealType> deals = draft.deals();
    assertAll(
        () -> assertEquals(Regulation.DEALS_PER_GAME + Regulation.DEALS_PER_BLOCK, added),
        () -> assertEquals(DealType.FORCED_TRUMP, last),
        () -> assertEquals(Regulation.DEALS_PER_BLOCK, minimum),
        () -> assertEquals(DealPlan.REGULATION, draft.plan()),
        () -> assertThrows(UnsupportedOperationException.class, deals::clear));
  }

  /**
   * A deal changes its type, and choosing the type it already has changes nothing.
   */
  @Test
  @DisplayName("A deal changes its type; choosing the same type changes nothing")
  void setDealTest() {
    final DealPlanDraft draft = new DealPlanDraft(DealPlan.REGULATION);
    final boolean changed = draft.setDeal(0, DealType.SCHIEBER);
    final boolean same = draft.setDeal(0, DealType.SCHIEBER);
    assertAll(
        () -> assertTrue(changed),
        () -> assertFalse(same),
        () -> assertEquals(DealType.SCHIEBER, draft.plan().deals().getFirst()),
        () -> assertEquals(DealPlan.REGULATION.deals().subList(1, Regulation.DEALS_PER_GAME),
                           draft.deals().subList(1, Regulation.DEALS_PER_GAME)));
  }

}
