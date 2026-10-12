package ch.varani.jass.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of money entered in the accounting of an edition, in Swiss francs (CHF, the only currency, Q7).
 *
 * <p>The amount is exact ({@link BigDecimal}, never a floating-point number): between 0 and {@link #MAX}, with at
 * most two decimals (the centimes), kept with exactly two decimals so that equal amounts are equal records. The
 * computed values (totals, balance, which may be negative) are plain {@link BigDecimal} values.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param value the amount in CHF, with two decimals.
 */
public record Money(BigDecimal value) {

  /** The number of decimals of an amount: the centimes. */
  public static final int DECIMALS = 2;
  /** The greatest amount accepted, far above any amount of an edition, to catch typing errors. */
  public static final BigDecimal MAX = new BigDecimal("999999.99");
  /** The amount of zero francs. */
  public static final Money ZERO = new Money(BigDecimal.ZERO);

  /**
   * Creates an amount, checking it.
   *
   * @param value the amount in CHF.
   *
   * @throws NullPointerException if the value is {@code null}.
   * @throws DomainException with {@link ValidationError#INVALID_AMOUNT} if the amount is negative, greater than
   *                         {@link #MAX}, or has more than two decimals.
   */
  public Money {
    Objects.requireNonNull(value, "value");
    if (value.signum() < 0 || value.compareTo(MAX) > 0 || value.stripTrailingZeros().scale() > DECIMALS) {
      throw new DomainException(ValidationError.INVALID_AMOUNT);
    }
    value = value.setScale(DECIMALS, RoundingMode.UNNECESSARY);
  }

  /**
   * Reads an amount written with a dot as decimal separator, e.g. {@code 30}, {@code 12.5} or {@code 1234.50}.
   *
   * @param text the amount.
   *
   * @return the amount.
   *
   * @throws NullPointerException if the text is {@code null}.
   * @throws DomainException with {@link ValidationError#INVALID_AMOUNT} if the text is not a valid amount.
   */
  public static Money of(final String text) {
    try {
      return new Money(new BigDecimal(text.strip()));
    } catch (NumberFormatException _) {
      throw new DomainException(ValidationError.INVALID_AMOUNT);
    }
  }

  /**
   * Tells whether the amount is zero.
   *
   * @return {@code true} for zero francs.
   */
  public boolean isZero() {
    return value.signum() == 0;
  }

}
