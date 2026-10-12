package ch.varani.jass.app;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * The languages of the application (Q13): the language of the system chooses the texts and the formats of the user
 * interface and of the generated documents.
 *
 * <ul>
 *   <li>French: texts in French, formats of {@code fr-CH};</li>
 *   <li>German: texts in German, formats of {@code de-CH};</li>
 *   <li>Italian: texts in Italian, formats of {@code it-CH};</li>
 *   <li>English or any other language: texts in English, formats of {@code en-GB}.</li>
 * </ul>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class Languages {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** French of Switzerland. */
  static final Locale FRENCH = Locale.forLanguageTag("fr-CH");
  /** German of Switzerland. */
  static final Locale GERMAN = Locale.forLanguageTag("de-CH");
  /** Italian of Switzerland. */
  static final Locale ITALIAN = Locale.forLanguageTag("it-CH");
  /** British English, for English and the other languages. */
  static final Locale ENGLISH = Locale.forLanguageTag("en-GB");

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private Languages() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Chooses the locale of the application from the locale of the system.
   *
   * @param system the locale of the system.
   *
   * @return {@link #FRENCH}, {@link #GERMAN}, {@link #ITALIAN} or {@link #ENGLISH}.
   */
  static Locale resolve(final Locale system) {
    return switch (system.getLanguage()) {
      case "fr" -> FRENCH;
      case "de" -> GERMAN;
      case "it" -> ITALIAN;
      default -> ENGLISH;
    };
  }

  /**
   * Loads a bundle of texts: the bundle of the language of the locale, or the default bundle (English).
   *
   * <p>{@link ResourceBundle#getBundle(String, Locale, Module)} falls back to the default locale of the JVM before the
   * default bundle; the bundle found is therefore kept only if it is of the requested language.</p>
   *
   * @param base the base name of the bundle.
   * @param locale the locale of the application.
   *
   * @return the bundle.
   */
  static ResourceBundle bundle(final String base, final Locale locale) {
    final ResourceBundle found = ResourceBundle.getBundle(base, locale, Languages.class.getModule());
    if (found.getLocale().getLanguage().equals(locale.getLanguage())) {
      return found;
    }
    return ResourceBundle.getBundle(base, Locale.ROOT, Languages.class.getModule());
  }

}
