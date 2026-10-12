package ch.varani.jass.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link Languages}: the locale of the application follows the language of the system, English for the
 * languages without texts, whatever the default locale of the JVM.
 */
final class LanguagesTest {

  /**
   * Q13: French, German and Italian systems get the texts and formats of Switzerland; English and the other languages
   * get British English.
   */
  @Test
  @DisplayName("Q13: fr, de and it give the Swiss locales, any other language en-GB")
  void resolveTest() {
    assertAll(
        () -> assertEquals(Locale.forLanguageTag("fr-CH"), Languages.resolve(Locale.FRANCE)),
        () -> assertEquals(Locale.forLanguageTag("fr-CH"), Languages.resolve(Locale.forLanguageTag("fr-CA"))),
        () -> assertEquals(Locale.forLanguageTag("de-CH"), Languages.resolve(Locale.GERMANY)),
        () -> assertEquals(Locale.forLanguageTag("it-CH"), Languages.resolve(Locale.ITALIAN)),
        () -> assertEquals(Locale.forLanguageTag("en-GB"), Languages.resolve(Locale.US)),
        () -> assertEquals(Locale.forLanguageTag("en-GB"), Languages.resolve(Locale.forLanguageTag("es-ES"))),
        () -> assertEquals(Locale.forLanguageTag("en-GB"), Languages.resolve(Locale.ROOT)));
  }

  /**
   * The texts of English come from the default bundle even on a system whose default locale has texts.
   */
  @Test
  @DisplayName("English texts are used even when the JVM defaults to French")
  void bundleTest() {
    final Locale system = Locale.getDefault();
    try {
      Locale.setDefault(Locale.FRENCH);
      assertAll(
          () -> assertEquals("Jass Tournament",
                             Languages.bundle(Messages.BUNDLE_NAME, Languages.ENGLISH).getString("application.title")),
          () -> assertEquals("Jassturnier",
                             Languages.bundle(Messages.BUNDLE_NAME, Languages.GERMAN).getString("application.title")),
          () -> assertEquals("Torneo di Jass",
                             Languages.bundle(Messages.BUNDLE_NAME, Languages.ITALIAN).getString("application.title")),
          () -> assertEquals("Tournoi de Jass",
                             Languages.bundle(Messages.BUNDLE_NAME, Languages.FRENCH).getString("application.title")));
    } finally {
      Locale.setDefault(system);
    }
  }

}
