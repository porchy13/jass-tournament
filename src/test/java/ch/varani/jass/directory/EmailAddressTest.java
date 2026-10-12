package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link EmailAddress}: cleaning, lower case and rejection of the invalid forms.
 */
final class EmailAddressTest {

  /** The longest label of a domain. */
  private static final int LONGEST_LABEL = 63;
  /** A number of labels of {@link #LONGEST_LABEL} characters that exceeds the maximum length of an address. */
  private static final int LABELS = 4;

  /**
   * An address is stripped and put in lower case, so that two spellings of the same address are equal.
   */
  @Test
  @DisplayName("An address is stripped and put in lower case")
  void normaliseTest() {
    assertAll(
        () -> assertEquals("anne.fontaine@example.ch", new EmailAddress("  Anne.Fontaine@Example.CH ").value()),
        () -> assertEquals(new EmailAddress("zoe+jass@mail.example.com"),
                           new EmailAddress("ZOE+JASS@MAIL.EXAMPLE.COM")),
        () -> assertEquals("o'brien_1@sub-domain.example.org",
                           new EmailAddress("o'brien_1@sub-domain.example.org").value()));
  }

  /**
   * Provides addresses that are not valid.
   *
   * @return the addresses.
   */
  static Stream<String> invalidAddresses() {
    final String local = "a".repeat(EmailAddress.MAX_LOCAL_LENGTH);
    final String label = "a".repeat(LONGEST_LABEL);
    return Stream.of("", "anne", "anne@", "@example.ch", "anne@example", "anne@@example.ch", "an ne@example.ch",
                     "anne..fontaine@example.ch", ".anne@example.ch", "anne@-example.ch", "anne@example.c",
                     "anne@exämple.ch", "zoé@example.ch", "anne@example.ch.", "anne@[192.168.0.1]",
                     "\"anne\"@example.ch", local + "a@example.ch", "anne@" + label + "a.ch",
                     "anne@" + (label + ".").repeat(LABELS) + "ch");
  }

  /**
   * An address that does not have the form {@code local@domain}, or is too long, is rejected.
   *
   * @param address the address.
   */
  @ParameterizedTest
  @MethodSource("invalidAddresses")
  @DisplayName("An address with an invalid form is rejected")
  void invalidTest(final String address) {
    assertEquals(ValidationError.INVALID_EMAIL,
                 assertThrows(DomainException.class, () -> new EmailAddress(address)).getError());
  }

  /**
   * The longest local part is accepted, and a missing address is a programming error.
   */
  @Test
  @DisplayName("The longest local part is accepted, a null address is rejected")
  void limitsTest() {
    final String local = "a".repeat(EmailAddress.MAX_LOCAL_LENGTH);
    assertAll(
        () -> assertEquals(local + "@example.ch", new EmailAddress(local + "@example.ch").value()),
        () -> assertThrows(NullPointerException.class, () -> new EmailAddress(null)));
  }

}
