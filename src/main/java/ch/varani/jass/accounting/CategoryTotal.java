package ch.varani.jass.accounting;

import java.math.BigDecimal;

/**
 * The total of the entries of one category of the accounting, of incomes or of expenses.
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param category the category, in its first spelling.
 * @param amount the total in CHF, with two decimals.
 * @param entries the number of entries of the category.
 */
public record CategoryTotal(String category, BigDecimal amount, int entries) {
}
