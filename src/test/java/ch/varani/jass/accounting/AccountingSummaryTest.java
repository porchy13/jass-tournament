package ch.varani.jass.accounting;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.EntryType;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Money;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TournamentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link AccountingSummary}: registration fees, totals per category and balance. The players are fictitious.
 */
final class AccountingSummaryTest {

  /** The date of the fictitious edition. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** The number of players of the team edition. */
  private static final int PLAYERS = 4;
  /** The number of players who paid in the team edition. */
  private static final int PAID = 3;

  /**
   * Creates an entry.
   *
   * @param type income or expense.
   * @param category the category.
   * @param amount the amount.
   *
   * @return the entry.
   */
  private static LedgerEntry entry(final EntryType type, final String category, final String amount) {
    return LedgerEntry.create(DATE, type, category, "", Money.of(amount));
  }

  /**
   * The fees paid count in the income, the fees outstanding apart; the entries are summed per category, the
   * categories merged when they differ by case or accents.
   */
  @Test
  @DisplayName("The fees paid and the entries per category give the balance")
  void summaryTest() {
    final Player paid = new Player("Anne", "A").withPaid(true);
    final TeamRegistrations teams = TeamRegistrations.empty()
        .add(new Team(paid, new Player("Bruno", "B").withPaid(true)))
        .add(new Team(new Player("Zoé", "Z").withPaid(true), new Player("Léo", "L")));
    final Ledger ledger = Ledger.EMPTY.withFee(Money.of("30"))
                                      .add(entry(EntryType.INCOME, "Sponsoring", "200"))
                                      .add(entry(EntryType.EXPENSE, "Prix", "120.50"))
                                      .add(entry(EntryType.INCOME, "sponsoring ", "50"))
                                      .add(entry(EntryType.EXPENSE, "Boissons", "80"));
    final AccountingSummary summary = AccountingSummary.of(
        Edition.create("Match", DATE, TournamentMode.TEAM).withRegistrations(teams).withLedger(ledger));
    assertAll(
        () -> assertEquals(PLAYERS, summary.players()),
        () -> assertEquals(PAID, summary.paidPlayers()),
        () -> assertEquals(new BigDecimal("90.00"), summary.feesPaid()),
        () -> assertEquals(new BigDecimal("30.00"), summary.feesOutstanding()),
        () -> assertEquals(List.of(new CategoryTotal("Sponsoring", new BigDecimal("250.00"), 2)), summary.incomes()),
        () -> assertEquals(List.of(new CategoryTotal("Boissons", new BigDecimal("80.00"), 1),
                                   new CategoryTotal("Prix", new BigDecimal("120.50"), 1)), summary.expenses()),
        () -> assertEquals(new BigDecimal("340.00"), summary.totalIncome()),
        () -> assertEquals(new BigDecimal("200.50"), summary.totalExpenses()),
        () -> assertEquals(new BigDecimal("139.50"), summary.balance()),
        () -> assertThrows(UnsupportedOperationException.class, () -> summary.incomes().clear()));
  }

  /**
   * Without fee nor income, the balance of the expenses is negative; in individual mode, every player owes the fee.
   */
  @Test
  @DisplayName("The balance may be negative; in individual mode every player owes the fee")
  void negativeTest() {
    final Edition edition = Edition.create("Match", DATE, TournamentMode.INDIVIDUAL)
                                   .withRegistrations(new IndividualRegistrations(List.of(new Player("Anne", ""))))
                                   .withLedger(Ledger.EMPTY.add(entry(EntryType.EXPENSE, "Salle", "10")));
    final AccountingSummary summary = AccountingSummary.of(edition);
    assertAll(
        () -> assertEquals(1, summary.players()),
        () -> assertEquals(new BigDecimal("0.00"), summary.feesOutstanding()),
        () -> assertEquals(List.of(), summary.incomes()),
        () -> assertEquals(new BigDecimal("0.00"), summary.totalIncome()),
        () -> assertEquals(new BigDecimal("-10.00"), summary.balance()));
  }

}
