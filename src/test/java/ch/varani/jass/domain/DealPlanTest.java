package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link DealPlan}: the plan of the regulation, the rule of the number of deals, the blocks and the points.
 */
final class DealPlanTest {

  /** The number of the ninth deal, the first one of the third block. */
  private static final int NINTH = 9;
  /** The number of the thirteenth deal, the first one of the fourth block. */
  private static final int THIRTEENTH = 13;
  /** A number of deals that is a multiple of 4, other than the regulation. */
  private static final int TWELVE = 12;

  /**
   * Rules 3 and 8 to 11: the plan of the regulation has 16 deals: imposed trump ♥ ♣ ♦ ♠, then four deals of each of
   * <i>Schieber</i>, <i>Kreuz</i> and forced trump.
   */
  @Test
  @DisplayName("Rules 3 and 8 to 11: 16 deals, imposed ♥ ♣ ♦ ♠, then Schieber, Kreuz and forced trump")
  void regulationTest() {
    final DealPlan plan = DealPlan.REGULATION;
    assertAll(
        () -> assertEquals(Regulation.DEALS_PER_GAME, plan.size()),
        () -> assertEquals(List.of(DealType.IMPOSED_HEARTS, DealType.IMPOSED_CLUBS, DealType.IMPOSED_DIAMONDS,
                                   DealType.IMPOSED_SPADES), plan.deals().subList(0, Regulation.DEALS_PER_BLOCK)),
        () -> assertEquals(Collections.nCopies(Regulation.DEALS_PER_BLOCK, DealType.SCHIEBER),
                           plan.deals().subList(Regulation.DEALS_PER_BLOCK, NINTH - 1)),
        () -> assertEquals(Collections.nCopies(Regulation.DEALS_PER_BLOCK, DealType.KREUZ),
                           plan.deals().subList(NINTH - 1, THIRTEENTH - 1)),
        () -> assertEquals(Collections.nCopies(Regulation.DEALS_PER_BLOCK, DealType.FORCED_TRUMP),
                           plan.deals().subList(THIRTEENTH - 1, Regulation.DEALS_PER_GAME)));
  }

  /**
   * Rules 5, 17 and 19: the plan of the regulation gives 2'512 points per game and 1'256 points per forfeit.
   */
  @Test
  @DisplayName("Rules 5, 17 and 19: the regulation plan totals 2'512 points, a forfeit 1'256 points")
  void regulationPointsTest() {
    assertAll(
        () -> assertEquals(Regulation.POINTS_PER_GAME, DealPlan.REGULATION.pointsPerGame()),
        () -> assertEquals(Regulation.FORFEIT_POINTS, DealPlan.REGULATION.forfeitPoints()),
        () -> assertEquals(Regulation.DEALS_PER_BLOCK, DealPlan.REGULATION.blocks()));
  }

  /**
   * The points of a game, of a forfeit and the number of blocks follow the number of deals of another plan.
   */
  @Test
  @DisplayName("The points and the blocks follow the number of deals")
  void otherPlanPointsTest() {
    final DealPlan plan = new DealPlan(Collections.nCopies(TWELVE, DealType.FORCED_TRUMP));
    assertAll(
        () -> assertEquals(TWELVE * Regulation.POINTS_PER_DEAL, plan.pointsPerGame()),
        () -> assertEquals(TWELVE * Regulation.POINTS_PER_DEAL / 2, plan.forfeitPoints()),
        () -> assertEquals(TWELVE / Regulation.DEALS_PER_BLOCK, plan.blocks()));
  }

  /**
   * Rule 8: the order of the imposed trumps is the one of the plan; a game may hold imposed trumps only, four deals
   * of each suit in any order, in four blocks.
   */
  @Test
  @DisplayName("Rule 8: the plan gives the suits and the order of the imposed trumps")
  void imposedTrumpsOnlyTest() {
    final List<DealType> deals = new ArrayList<>();
    for (final DealType suit : List.of(DealType.IMPOSED_HEARTS, DealType.IMPOSED_SPADES, DealType.IMPOSED_CLUBS,
                                       DealType.IMPOSED_DIAMONDS)) {
      deals.addAll(Collections.nCopies(Regulation.DEALS_PER_BLOCK, suit));
    }
    final DealPlan plan = new DealPlan(deals);
    assertAll(
        () -> assertEquals(DealType.IMPOSED_HEARTS, plan.deals().get(0)),
        () -> assertEquals(DealType.IMPOSED_SPADES, plan.deals().get(Regulation.DEALS_PER_BLOCK)),
        () -> assertEquals(Regulation.DEALS_PER_BLOCK, plan.blocks()));
  }

  /**
   * Provides the numbers of deals that are not a positive multiple of 4, up to one more than the regulation.
   *
   * @return the numbers of deals.
   */
  static IntStream invalidSizes() {
    return IntStream.rangeClosed(0, Regulation.DEALS_PER_GAME + 1)
                    .filter(size -> size == 0 || size % Regulation.DEALS_PER_BLOCK != 0);
  }

  /**
   * The number of deals must be a positive multiple of 4, so that each player at the table starts the same number of
   * deals.
   *
   * @param size the number of deals.
   */
  @ParameterizedTest
  @MethodSource("invalidSizes")
  @DisplayName("The number of deals must be a positive multiple of 4, so that each player starts as many deals")
  void invalidSizeTest(final int size) {
    final List<DealType> deals = Collections.nCopies(size, DealType.SCHIEBER);
    assertEquals(ValidationError.INVALID_DEALS,
                 assertThrows(DomainException.class, () -> new DealPlan(deals)).getError());
  }

  /**
   * A missing list or deal is rejected, and the plan does not share the list it was created with.
   */
  @Test
  @DisplayName("A missing deal is rejected and the plan is immutable")
  void immutabilityTest() {
    final List<DealType> deals = new ArrayList<>(Collections.nCopies(Regulation.DEALS_PER_BLOCK, DealType.KREUZ));
    final DealPlan plan = new DealPlan(deals);
    deals.set(0, DealType.SCHIEBER);
    final List<DealType> withNull = new ArrayList<>(deals);
    withNull.set(0, null);
    assertAll(
        () -> assertEquals(DealType.KREUZ, plan.deals().get(0)),
        () -> assertThrows(UnsupportedOperationException.class, () -> plan.deals().set(0, DealType.SCHIEBER)),
        () -> assertThrows(NullPointerException.class, () -> new DealPlan(null)),
        () -> assertThrows(NullPointerException.class, () -> new DealPlan(withNull)));
  }

}
