package ch.varani.jass.report;

import java.text.MessageFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The localized texts of the documents ({@code ch/varani/jass/report/documents*.properties}), and the formats of
 * their dates and numbers in the language of the documents (e.g. {@code 20 novembre 2026}, {@code 2'512} in
 * {@code fr-CH}).
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
final class Texts {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The base name of the bundle of the texts. */
  static final String BUNDLE_NAME = "ch.varani.jass.report.documents";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The language of the texts. */
  private final Locale locale;
  /** The texts. */
  private final ResourceBundle bundle;
  /** The format of the dates. */
  private final DateTimeFormatter dates;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Loads the texts of a language.
   *
   * @param locale the language and country of the documents.
   */
  Texts(final Locale locale) {
    this.locale = locale;
    this.bundle = bundle(locale);
    this.dates = DateTimeFormatter.ofPattern(bundle.getString("date.pattern"), locale);
  }

  /**
   * Loads the bundle of a language: the bundle of that language, or the default bundle (English) when there is none.
   *
   * <p>{@link ResourceBundle#getBundle(String, Locale, Module)} falls back to the default locale of the system before
   * the default bundle: on a computer in French, the English documents would be in French. The bundle found is
   * therefore kept only if it is of the requested language.</p>
   *
   * @param locale the language of the documents.
   *
   * @return the bundle.
   */
  private static ResourceBundle bundle(final Locale locale) {
    final ResourceBundle found = ResourceBundle.getBundle(BUNDLE_NAME, locale, Texts.class.getModule());
    if (found.getLocale().getLanguage().equals(locale.getLanguage())) {
      return found;
    }
    return ResourceBundle.getBundle(BUNDLE_NAME, Locale.ROOT, Texts.class.getModule());
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns a text, its arguments inserted.
   *
   * @param key the key of the text.
   * @param arguments the values of the placeholders {@code {0}}, {@code {1}}…
   *
   * @return the text.
   */
  String get(final String key, final Object... arguments) {
    return new MessageFormat(bundle.getString(key), locale).format(arguments);
  }

  /**
   * Formats a date, e.g. {@code 20 novembre 2026}.
   *
   * @param date the date.
   *
   * @return the date in the language of the documents.
   */
  String date(final LocalDate date) {
    return dates.format(date);
  }

  /**
   * Formats a whole number with the grouping of the language, e.g. {@code 2'512}.
   *
   * @param number the number.
   *
   * @return the number in the language of the documents.
   */
  String number(final int number) {
    return NumberFormat.getIntegerInstance(locale).format(number);
  }

}
