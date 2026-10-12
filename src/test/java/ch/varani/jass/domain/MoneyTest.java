package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Money}: amounts in CHF with two decimals, between zero and the maximum.
 */
final class MoneyTest {

  /**
   * An amount is kept with two decimals, so that equal amounts are equal.
   */
  @Test
  @DisplayName("An amount is kept with two decimals")
  void scaleTest() {
    assertAll(
        () -> assertEquals("30.00", Money.of(" 30 ").value().toPlainString()),
        () -> assertEquals(Money.of("12.5"), new Money(new BigDecimal("12.50"))),
        () -> assertEquals("999999.99", new Money(Money.MAX).value().toPlainString()),
        () -> assertTrue(Money.ZERO.isZero()),
        () -> assertFalse(Money.of("0.05").isZero()),
        () -> assertEquals("1.20", Money.of("1.200").value().toPlainString()));
  }

  /**
   * A negative amount, an amount above the maximum, more than two decimals or a text that is not a number are
   * rejected.
   *
   * @param text the amount.
   */
  @ParameterizedTest
  @ValueSource(strings = {"-1", "1000000", "0.001", "12,50", "1'000", "abc", ""})
  @DisplayName("An invalid amount is rejected")
  void invalidTest(final String text) {
    assertEquals(ValidationError.INVALID_AMOUNT, assertThrows(DomainException.class, () -> Money.of(text)).getError());
  }

  /**
   * A missing amount is a programming error.
   */
  @Test
  @DisplayName("A null amount is rejected")
  void nullTest() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> new Money(null)),
        () -> assertThrows(NullPointerException.class, () -> Money.of(null)));
  }

}
