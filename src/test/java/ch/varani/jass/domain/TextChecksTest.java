package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link TextChecks}: the names of the players and of the editions reject the characters that cannot be
 * stored in the edition files.
 */
final class TextChecksTest {

  /**
   * Letters with accents, symbols and emoji (a surrogate pair with a joiner) are accepted.
   *
   * @param text a text with allowed characters.
   */
  @ParameterizedTest
  @ValueSource(strings = {"Zoé", "Jean-Claude d'Été", "♥ ♣ ♦ ♠", "👩‍💻", ""})
  @DisplayName("Accents, symbols and emoji are accepted")
  void allowedTest(final String text) {
    assertEquals(text, TextChecks.checkCharacters(text));
  }

  /**
   * Control characters, lone surrogates and unassigned code points are rejected.
   *
   * @param text a text with a character that is not allowed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"A\u0001", "A\u007F", "A\u0085", "A\uD800", "A\uDC00B", "A￾", "A￿"})
  @DisplayName("Control characters, lone surrogates and unassigned code points are rejected")
  void rejectedTest(final String text) {
    assertEquals(ValidationError.INVALID_CHARACTER,
                 assertThrows(DomainException.class, () -> TextChecks.checkCharacters(text)).getError());
  }

  /**
   * The names of the players and of the editions are checked.
   */
  @Test
  @DisplayName("The names of the players and of the editions are checked")
  void namesTest() {
    final LocalDate date = LocalDate.of(2026, Month.NOVEMBER, 20);
    assertAll(
        () -> assertEquals(ValidationError.INVALID_CHARACTER,
                           assertThrows(DomainException.class, () -> new Player("A\u0000", "B")).getError()),
        () -> assertEquals(ValidationError.INVALID_CHARACTER,
                           assertThrows(DomainException.class, () -> new Player("A", "B\u0000")).getError()),
        () -> assertEquals(ValidationError.INVALID_CHARACTER,
                           assertThrows(DomainException.class,
                                        () -> Edition.create("Match\u0007", date, TournamentMode.TEAM)).getError()));
  }

}
