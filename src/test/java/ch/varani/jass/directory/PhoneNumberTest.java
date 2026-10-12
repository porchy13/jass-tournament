package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link PhoneNumber}: conversion to the format E.164, Switzerland by default, and rejection of the invalid
 * numbers. The numbers are fictitious.
 */
final class PhoneNumberTest {

  /** A fictitious Swiss mobile number in the format E.164. */
  private static final String SWISS = "+41790000001";

  /**
   * A Swiss number in its national or international spellings gives the same number in the format E.164.
   *
   * @param typed the number as typed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"079 000 00 01", "0790000001", "+41 79 000 00 01", "0041 79 000 00 01", "079-000.00.01",
                          " (079) 000 00 01 ", "+41790000001"})
  @DisplayName("A Swiss number in any spelling gives the same number in the format E.164")
  void swissTest(final String typed) {
    assertEquals(SWISS, new PhoneNumber(typed).value());
  }

  /**
   * A number of another country is accepted with its country code.
   */
  @Test
  @DisplayName("A number of another country is accepted with its country code")
  void otherCountryTest() {
    assertAll(
        () -> assertEquals("+33600000001", new PhoneNumber("+33 6 00 00 00 01").value()),
        () -> assertEquals("+33600000001", new PhoneNumber("0033 600 000 001").value()));
  }

  /**
   * A text that is not a number, a number that is not valid in its country, or a too long text is rejected.
   *
   * @param typed the number as typed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "abc", "123", "079 000 00", "079 000 00 01 23 45", "+41 12 345 67 89", "+999 123 456",
                          "079 000 00 01 0000000000000000000000000000"})
  @DisplayName("A text that is not a valid number of its country is rejected")
  void invalidTest(final String typed) {
    assertEquals(ValidationError.INVALID_PHONE,
                 assertThrows(DomainException.class, () -> new PhoneNumber(typed)).getError());
  }

  /**
   * A missing number is a programming error.
   */
  @Test
  @DisplayName("A null number is rejected")
  void nullTest() {
    assertThrows(NullPointerException.class, () -> new PhoneNumber(null));
  }

}
