package ch.varani.jass.report;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The options of the generated documents: the language of their texts and the logo printed on each of them.
 *
 * <p>The logo is the content of a PNG or JPEG image chosen by the user for the application (stored by
 * {@code ch.varani.jass.persistence.LogoStore}); without logo, the documents are printed without it.</p>
 *
 * <p>Immutable (the logo is copied), safe for concurrent use.</p>
 */
public final class DocumentOptions {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The language and country of the texts, dates and numbers, e.g. {@code fr-CH}. */
  private final Locale locale;
  /** The content of the logo, or {@code null} without logo. */
  private final byte[] logo;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the options of the documents.
   *
   * @param locale the language and country of the texts, dates and numbers.
   * @param logo the content of the logo (PNG or JPEG), or {@code null} without logo; it is copied.
   *
   * @throws NullPointerException if the locale is {@code null}.
   */
  public DocumentOptions(final Locale locale, final byte[] logo) {
    this.locale = Objects.requireNonNull(locale, "locale");
    this.logo = logo == null ? null : logo.clone();
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the language and country of the texts, dates and numbers.
   *
   * @return the locale.
   */
  public Locale locale() {
    return locale;
  }

  /**
   * Returns the content of the logo.
   *
   * @return a copy of the content, or nothing without logo.
   */
  public Optional<byte[]> logo() {
    return Optional.ofNullable(logo).map(byte[]::clone);
  }

  /**
   * Tells whether these options equal other ones: same locale and same logo content.
   *
   * @param other the other object.
   *
   * @return {@code true} if both options are equal.
   */
  @Override
  public boolean equals(final Object other) {
    return other instanceof final DocumentOptions options && locale.equals(options.locale)
           && Arrays.equals(logo, options.logo);
  }

  /**
   * Returns a hash code consistent with {@link #equals(Object)}.
   *
   * @return the hash code.
   */
  @Override
  public int hashCode() {
    return Objects.hash(locale, Arrays.hashCode(logo));
  }

}
