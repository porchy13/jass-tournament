package ch.varani.jass.app;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.ValidationError;
import ch.varani.jass.persistence.FileFormatException;
import ch.varani.jass.persistence.FormatError;
import ch.varani.jass.update.UpdateError;
import ch.varani.jass.update.UpdateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests of {@link Messages}: texts, dates, and the messages of the errors in every language.
 */
final class MessagesTest {

  /** The points of a game of the regulation, a number with a group separator. */
  private static final int GAME = 2512;
  /** The points of a deal, a number without group separator. */
  private static final int DEAL = 157;
  /** A date of the tests. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);

  /**
   * Provides the locales of the application.
   *
   * @return the four locales.
   */
  static Stream<Locale> locales() {
    return Stream.of(Languages.FRENCH, Languages.GERMAN, Languages.ITALIAN, Languages.ENGLISH);
  }

  /**
   * Every error of the domain and of the files has a message in every language.
   *
   * @param locale the locale.
   */
  @ParameterizedTest
  @MethodSource("locales")
  @DisplayName("Every error code has a message in every language")
  void errorsTest(final Locale locale) {
    final Messages messages = new Messages(locale);
    for (final ValidationError error : ValidationError.values()) {
      assertFalse(messages.error(new DomainException(error)).isBlank(), error.name());
    }
    for (final FormatError error : FormatError.values()) {
      assertFalse(messages.error(new FileFormatException(error)).isBlank(), error.name());
    }
    for (final UpdateError error : UpdateError.values()) {
      assertFalse(messages.error(new UpdateException(error)).isBlank(), error.name());
    }
  }

  /**
   * The errors are explained according to their kind.
   */
  @Test
  @DisplayName("The errors are explained according to their kind")
  void kindsTest() {
    final Messages messages = new Messages(Languages.FRENCH);
    assertAll(
        () -> assertEquals("Donnez un nom à l’édition.",
                           messages.error(new DomainException(ValidationError.BLANK_EDITION_NAME))),
        () -> assertEquals("Le fichier est trop volumineux.",
                           messages.error(new FileFormatException(FormatError.TOO_LARGE))),
        () -> assertEquals(messages.get("error.update.UNAVAILABLE"),
                           messages.error(new UpdateException(UpdateError.UNAVAILABLE))),
        () -> assertEquals(messages.get("error.io"), messages.error(new IOException("disk"))),
        () -> assertEquals(messages.get("error.unexpected"), messages.error(new IllegalStateException())));
  }

  /**
   * The texts take their arguments, and the dates follow the locale.
   */
  @Test
  @DisplayName("The texts take their arguments and the dates follow the locale")
  void textsTest() {
    final Messages french = new Messages(Languages.FRENCH);
    final Messages german = new Messages(Languages.GERMAN);
    assertAll(
        () -> assertEquals("Match – Tournoi de Jass", french.get("window.title", "Match")),
        () -> assertEquals("20 nov. 2026", french.date(DATE)),
        () -> assertEquals("20.11.2026", german.date(DATE)),
        () -> assertEquals("20 Nov 2026", new Messages(Languages.ENGLISH).date(DATE)),
        () -> assertEquals(Languages.GERMAN, german.locale()),
        () -> assertEquals("Jassturnier", german.bundle().getString("application.title")),
        () -> assertEquals(DATE, german.dateFormat().parse("20.11.2026", LocalDate::from)));
  }

  /**
   * The numbers are formatted in the locale of the application.
   */
  @Test
  @DisplayName("The numbers are grouped as in the locale")
  void numbersTest() {
    assertAll(() -> assertEquals("2,512", new Messages(Languages.ENGLISH).number(GAME)),
              () -> assertEquals("2’512", new Messages(Languages.GERMAN).number(GAME)),
              () -> assertEquals("157", new Messages(Languages.FRENCH).number(DEAL)));
  }

}
