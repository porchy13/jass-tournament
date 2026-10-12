package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.ValidationError;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.util.Objects;

/**
 * A phone number of a person of the directory, optional contact detail, used for WhatsApp.
 *
 * <p><b>Validation and normalisation</b> with Google's <i>libphonenumber</i>: the number is read as typed (spaces,
 * dots, hyphens and parentheses allowed), in the national format of Switzerland ({@link #DEFAULT_REGION}) unless it
 * starts with {@code +} or {@code 00} followed by a country code; it must be a valid number of its country (numbering
 * plan of the metadata of the library); it is kept in the international format E.164 (e.g. {@code +41791234567}),
 * so that two spellings of the same number are equal (duplicate detection). The text is limited to
 * {@link #MAX_LENGTH} characters before it is read.</p>
 *
 * <p>Immutable, safe for concurrent use (the library is thread-safe).</p>
 *
 * @param value the number in the format E.164.
 */
public record PhoneNumber(String value) {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The country of the numbers typed without country code: Switzerland. */
  public static final String DEFAULT_REGION = "CH";
  /** The maximum length of a number as typed. */
  public static final int MAX_LENGTH = 40;
  /** The phone number library. */
  private static final PhoneNumberUtil LIBRARY = PhoneNumberUtil.getInstance();

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a phone number, checking it and converting it to the format E.164.
   *
   * @param value the number, as typed or imported, or already in the format E.164.
   *
   * @throws NullPointerException if the number is {@code null}.
   * @throws DomainException with {@link ValidationError#INVALID_PHONE} if the number cannot be read or is not a valid
   *                         number of its country.
   */
  public PhoneNumber {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.length() > MAX_LENGTH) {
      throw new DomainException(ValidationError.INVALID_PHONE);
    }
    final Phonenumber.PhoneNumber parsed = parse(value);
    if (!LIBRARY.isValidNumber(parsed)) {
      throw new DomainException(ValidationError.INVALID_PHONE);
    }
    value = LIBRARY.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
  }

  /**
   * Reads a number with the library, in the national format of {@link #DEFAULT_REGION} unless it has a country code.
   *
   * @param text the number, as typed.
   *
   * @return the number read, not yet validated.
   *
   * @throws DomainException with {@link ValidationError#INVALID_PHONE} if the text cannot be read as a number.
   */
  private static Phonenumber.PhoneNumber parse(final String text) {
    try {
      return LIBRARY.parse(text, DEFAULT_REGION);
    } catch (NumberParseException _) {
      throw new DomainException(ValidationError.INVALID_PHONE);
    }
  }

}
