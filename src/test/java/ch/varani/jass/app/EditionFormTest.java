package ch.varani.jass.app;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.Money;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.TournamentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link EditionForm}: values of a new or duplicated edition, plan of the deals, check and result.
 */
final class EditionFormTest {

  /** The date of the tests. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** A number of games other than the regulation. */
  private static final int SIX = 6;

  /**
   * A new form proposes the settings of the regulation and needs a name.
   */
  @Test
  @DisplayName("A new form proposes the settings of the regulation and needs a name")
  void createTest() {
    final EditionForm form = EditionForm.create(DATE);
    assertAll(
        () -> assertFalse(form.isDuplicate()),
        () -> assertEquals("", form.name()),
        () -> assertEquals(DATE, form.date()),
        () -> assertEquals(TournamentMode.TEAM, form.mode()),
        () -> assertEquals(Regulation.GAMES, form.games()),
        () -> assertEquals(DealPlan.REGULATION.deals(), form.deals()),
        () -> assertEquals(Optional.of("error.validation.BLANK_EDITION_NAME"), form.problem()));
  }

  /**
   * The form builds the edition of its values once they are valid; a missing date or a number of games lower than 1
   * is reported.
   */
  @Test
  @DisplayName("The form builds the edition of its values once they are valid")
  void buildTest() {
    final EditionForm form = EditionForm.create(DATE);
    form.setName("Match");
    form.setMode(TournamentMode.INDIVIDUAL);
    form.setGames(SIX);
    form.setDeal(0, DealType.SCHIEBER);
    final Edition edition = form.build();
    form.setGames(0);
    final Optional<String> games = form.problem();
    form.setGames(SIX);
    form.setDate(null);
    assertAll(
        () -> assertEquals("Match", edition.name()),
        () -> assertEquals(TournamentMode.INDIVIDUAL, edition.mode()),
        () -> assertEquals(SIX, edition.games()),
        () -> assertEquals(DealType.SCHIEBER, edition.deals().deals().get(0)),
        () -> assertEquals(Optional.of("error.validation.INVALID_GAMES"), games),
        () -> assertEquals(Optional.of(EditionForm.MISSING_DATE), form.problem()),
        () -> assertThrows(NullPointerException.class, form::build));
  }

  /**
   * The plan is edited by blocks of four deals, at least one block, and the plan of the regulation can be restored.
   */
  @Test
  @DisplayName("The plan is edited by blocks of four deals")
  void blocksTest() {
    final EditionForm form = EditionForm.create(DATE);
    form.addBlock();
    final int added = form.deals().size();
    for (int i = 0; i < Regulation.DEALS_PER_GAME; i++) {
      form.removeBlock();
    }
    final int minimum = form.deals().size();
    form.useRegulation();
    assertAll(
        () -> assertEquals(Regulation.DEALS_PER_GAME + Regulation.DEALS_PER_BLOCK, added),
        () -> assertEquals(Regulation.DEALS_PER_BLOCK, minimum),
        () -> assertEquals(DealPlan.REGULATION.deals(), form.deals()),
        () -> assertThrows(UnsupportedOperationException.class, () -> form.deals().clear()));
  }

  /**
   * F16: a duplicate takes over the mode (fixed), the games, the plan and the fee, with an empty name and the date
   * one year later.
   */
  @Test
  @DisplayName("F16: a duplicate keeps the mode, the games, the plan and the fee")
  void duplicateTest() {
    final DealPlan forced = new DealPlan(Collections.nCopies(Regulation.DEALS_PER_BLOCK,
                                                            DealType.FORCED_TRUMP));
    final Edition source = Edition.create("2026", DATE, TournamentMode.INDIVIDUAL).withGames(SIX).withDeals(forced)
                                  .withLedger(Ledger.EMPTY.withFee(Money.of("30")));
    final EditionForm form = EditionForm.duplicate(source);
    form.setMode(TournamentMode.TEAM);
    form.setName("2027");
    final Edition copy = form.build();
    assertAll(
        () -> assertTrue(form.isDuplicate()),
        () -> assertEquals(TournamentMode.INDIVIDUAL, form.mode()),
        () -> assertEquals(DATE.plusYears(1), copy.date()),
        () -> assertEquals(TournamentMode.INDIVIDUAL, copy.mode()),
        () -> assertEquals(SIX, copy.games()),
        () -> assertEquals(forced, copy.deals()),
        () -> assertEquals(Money.of("30"), copy.ledger().fee()),
        () -> assertEquals("2027", copy.name()));
  }

}
