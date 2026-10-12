package ch.varani.jass.app;

import javafx.util.StringConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests of {@link Converters}: values shown with a text, never read back.
 */
final class ConvertersTest {

  /**
   * A value is shown with its text, and a text is never read back.
   */
  @Test
  @DisplayName("A value is shown with its text, a text is never read back")
  void showingTest() {
    final StringConverter<Integer> converter = Converters.showing(value -> "n" + value);
    assertAll(
        () -> assertEquals("n2", converter.toString(2)),
        () -> assertNull(converter.fromString("n3")));
  }

}
