package ch.varani.jass.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Settings}: defaults, reading of saved values (valid, missing or invalid), values to save and
 * changes.
 */
final class SettingsTest {

  /** A left or top of the window. */
  private static final double POSITION = 40;
  /** A width of the window. */
  private static final double WIDTH = 1100;
  /** A height of the window. */
  private static final double HEIGHT = 700;
  /** A position of a divider. */
  private static final double DIVIDER = 0.3;

  /**
   * Without saved value, the settings are the defaults: theme of the system, window of 1280 × 800, centred.
   */
  @Test
  @DisplayName("Without saved value, the settings are the defaults")
  void defaultTest() {
    final Settings settings = Settings.of(Map.of());
    assertAll(
        () -> assertEquals(Settings.DEFAULT, settings),
        () -> assertEquals(ThemeMode.SYSTEM, settings.theme()),
        () -> assertEquals(Settings.DEFAULT_WIDTH, settings.width()),
        () -> assertEquals(Settings.DEFAULT_HEIGHT, settings.height()),
        () -> assertTrue(Double.isNaN(settings.x())),
        () -> assertTrue(Double.isNaN(settings.sidebar())));
  }

  /**
   * The saved values are read back as they were written.
   */
  @Test
  @DisplayName("The saved values are read back as written")
  void roundTripTest() {
    final Settings settings = Settings.DEFAULT.withAppearance(ThemeMode.DARK, true, true)
                                              .withWindow(POSITION, POSITION, WIDTH, HEIGHT, true)
                                              .withDividers(DIVIDER, 1 - DIVIDER);
    final Settings read = Settings.of(settings.values());
    assertAll(
        () -> assertEquals(settings, read),
        () -> assertEquals("DARK", settings.values().get(Settings.KEY_THEME)),
        () -> assertEquals("true", settings.values().get(Settings.KEY_MAXIMIZED)));
  }

  /**
   * An unknown theme, a value that is not a number or a number that is not finite take their default.
   */
  @Test
  @DisplayName("Invalid values take their default")
  void invalidTest() {
    final Settings settings = Settings.of(Map.of(Settings.KEY_THEME, "PINK", Settings.KEY_WINDOW_WIDTH, "wide",
                                                 Settings.KEY_WINDOW_HEIGHT, "Infinity", Settings.KEY_WINDOW_X, "NaN",
                                                 Settings.KEY_SYSTEM_ACCENT, "yes"));
    assertEquals(Settings.DEFAULT, settings);
  }

}
