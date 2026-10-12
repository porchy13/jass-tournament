package ch.varani.jass.accounting;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.EntryType;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Money;
import ch.varani.jass.domain.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The financial overview of an edition (F8), computed from its accounting and its registrations, never stored.
 *
 * <p><b>Algorithm</b>: the registration fees are the fee of a player times the number of registered players, split
 * into the fees paid (players marked as paid) and the fees outstanding; the entries are summed per type and per
 * category (categories compared with accents, case and spaces ignored, named after their first spelling, sorted);
 * the total income is the fees paid plus the incomes; the balance is the total income minus the expenses, possibly
 * negative. The fees outstanding are not counted in the balance until they are paid. All the amounts are in CHF with
 * two decimals.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param players the number of registered players.
 * @param paidPlayers the number of players who have paid their fee.
 * @param feesPaid the registration fees received.
 * @param feesOutstanding the registration fees not received yet.
 * @param incomes the incomes per category.
 * @param expenses the expenses per category.
 * @param totalIncome the fees paid plus the incomes.
 * @param totalExpenses the sum of the expenses.
 * @param balance the total income minus the total expenses.
 */
public record AccountingSummary(int players, int paidPlayers, BigDecimal feesPaid, BigDecimal feesOutstanding,
                                List<CategoryTotal> incomes, List<CategoryTotal> expenses, BigDecimal totalIncome,
                                BigDecimal totalExpenses, BigDecimal balance) {

  /**
   * Creates a summary, copying the lists.
   *
   * @param players the number of registered players.
   * @param paidPlayers the number of players who have paid.
   * @param feesPaid the fees received.
   * @param feesOutstanding the fees not received yet.
   * @param incomes the incomes per category.
   * @param expenses the expenses per category.
   * @param totalIncome the total income.
   * @param totalExpenses the total of the expenses.
   * @param balance the balance.
   */
  public AccountingSummary {
    incomes = List.copyOf(incomes);
    expenses = List.copyOf(expenses);
  }

  /**
   * Computes the financial overview of an edition.
   *
   * @param edition the edition.
   *
   * @return the summary.
   */
  public static AccountingSummary of(final Edition edition) {
    final List<Player> players = edition.registrations().allPlayers();
    final int paid = (int) players.stream().filter(Player::paid).count();
    final int unpaid = players.size() - paid;
    final BigDecimal fee = edition.ledger().fee().value();
    final BigDecimal feesPaid = fee.multiply(BigDecimal.valueOf(paid));
    final BigDecimal feesOutstanding = fee.multiply(BigDecimal.valueOf(unpaid));
    final List<CategoryTotal> incomes = totals(edition.ledger().entries(), EntryType.INCOME);
    final List<CategoryTotal> expenses = totals(edition.ledger().entries(), EntryType.EXPENSE);
    final BigDecimal totalIncome = feesPaid.add(sum(incomes));
    final BigDecimal totalExpenses = sum(expenses);
    return new AccountingSummary(players.size(), paid, feesPaid, feesOutstanding, incomes, expenses, totalIncome,
                                 totalExpenses, totalIncome.subtract(totalExpenses));
  }

  /**
   * Sums the entries of one type per category.
   *
   * @param entries the entries of the accounting.
   * @param type the type of the entries summed.
   *
   * @return the totals per category, sorted by category (comparison form).
   */
  private static List<CategoryTotal> totals(final List<LedgerEntry> entries, final EntryType type) {
    final Map<String, CategoryTotal> totals = new LinkedHashMap<>();
    for (final LedgerEntry entry : entries) {
      if (entry.type() == type) {
        totals.merge(Player.comparisonKey(entry.category()),
                     new CategoryTotal(entry.category(), entry.amount().value(), 1),
                     (known, added) -> new CategoryTotal(known.category(), known.amount().add(added.amount()),
                                                         known.entries() + 1));
      }
    }
    return totals.entrySet().stream()
                 .sorted(Map.Entry.comparingByKey())
                 .map(Map.Entry::getValue)
                 .toList();
  }

  /**
   * Sums the totals of categories.
   *
   * @param totals the totals per category.
   *
   * @return their sum, with two decimals.
   */
  private static BigDecimal sum(final List<CategoryTotal> totals) {
    return totals.stream().map(CategoryTotal::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                 .setScale(Money.DECIMALS, RoundingMode.UNNECESSARY);
  }

}
