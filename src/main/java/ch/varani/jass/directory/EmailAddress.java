package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.ValidationError;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An e-mail address of a person of the directory, optional contact detail.
 *
 * <p><b>Validation</b>: the address is stripped of its leading and trailing spaces and put in lower case, so that two
 * spellings of the same address are equal (duplicate detection); it must then have at most {@link #MAX_LENGTH}
 * characters and the form {@code local@domain}: the local part (at most {@link #MAX_LOCAL_LENGTH} characters) made of
 * ASCII letters, digits and the symbols allowed by RFC 5322, in dot-separated words; the domain made of at least two
 * labels of letters, digits and inner hyphens, the last one of two letters at least. Quoted local parts, IP literals
 * and internationalised addresses are rejected: they are not used by the participants, and the length is checked
 * before the pattern, so that the pattern always runs on a short text.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param value the address, in lower case.
 */
public record EmailAddress(String value) {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The maximum length of an address (RFC 5321, path of 256 characters minus the angle brackets). */
  public static final int MAX_LENGTH = 254;
  /** The maximum length of the local part of an address (RFC 5321). */
  public static final int MAX_LOCAL_LENGTH = 64;
  /** The allowed form of an address, once in lower case. */
  private static final Pattern FORM = Pattern.compile(
      "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
      + "@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}");

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates an e-mail address, cleaning and checking it.
   *
   * @param value the address, as typed or imported.
   *
   * @throws NullPointerException if the address is {@code null}.
   * @throws DomainException with {@link ValidationError#INVALID_EMAIL} if the address does not have a valid form.
   */
  public EmailAddress {
    value = Objects.requireNonNull(value, "value").strip().toLowerCase(Locale.ROOT);
    if (value.length() > MAX_LENGTH || value.indexOf('@') > MAX_LOCAL_LENGTH || !FORM.matcher(value).matches()) {
      throw new DomainException(ValidationError.INVALID_EMAIL);
    }
  }

}
