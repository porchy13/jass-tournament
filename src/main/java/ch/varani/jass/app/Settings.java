package ch.varani.jass.app;

import java.util.Map;
import java.util.TreeMap;

/**
 * The settings of the user interface, kept in the data folder between two sessions (DESIGN.md §2, §9): the theme,
 * the accent of the system, the reduced animations, the size, position and state of the window, and the positions of
 * the dividers of the side navigation and of the detail panel.
 *
 * <p><b>Reading</b> ({@link #of(Map)}): a missing or invalid value takes its default; an unknown position is
 * {@link Double#NaN} (the window is centred, the divider keeps its default place). The values never hold personal
 * data.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param theme the theme chosen.
 * @param systemAccent whether the accent colour of the system replaces the accent of the application.
 * @param reducedMotion whether the animations are replaced by immediate changes.
 * @param x the left of the window, or {@link Double#NaN} to centre it.
 * @param y the top of the window, or {@link Double#NaN} to centre it.
 * @param width the width of the window.
 * @param height the height of the window.
 * @param maximized whether the window is maximized.
 * @param sidebar the position of the divider of the side navigation (0 to 1), or {@link Double#NaN}.
 * @param detail the position of the divider of the detail panel (0 to 1), or {@link Double#NaN}.
 */
record Settings(ThemeMode theme, boolean systemAccent, boolean reducedMotion, double x, double y, double width,
                double height, boolean maximized, double sidebar, double detail) {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The width of the window at the first start, in pixels. */
  static final double DEFAULT_WIDTH = 1280;
  /** The height of the window at the first start, in pixels. */
  static final double DEFAULT_HEIGHT = 800;
  /** The settings at the first start. */
  static final Settings DEFAULT = new Settings(ThemeMode.SYSTEM, false, false, Double.NaN, Double.NaN, DEFAULT_WIDTH,
                                               DEFAULT_HEIGHT, false, Double.NaN, Double.NaN);
  /** The key of the theme. */
  static final String KEY_THEME = "theme";
  /** The key of the accent of the system. */
  static final String KEY_SYSTEM_ACCENT = "accent.system";
  /** The key of the reduced animations. */
  static final String KEY_REDUCED_MOTION = "motion.reduced";
  /** The key of the left of the window. */
  static final String KEY_WINDOW_X = "window.x";
  /** The key of the top of the window. */
  static final String KEY_WINDOW_Y = "window.y";
  /** The key of the width of the window. */
  static final String KEY_WINDOW_WIDTH = "window.width";
  /** The key of the height of the window. */
  static final String KEY_WINDOW_HEIGHT = "window.height";
  /** The key of the maximized window. */
  static final String KEY_MAXIMIZED = "window.maximized";
  /** The key of the divider of the side navigation. */
  static final String KEY_SIDEBAR = "divider.sidebar";
  /** The key of the divider of the detail panel. */
  static final String KEY_DETAIL = "divider.detail";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Reads the settings from their saved values.
   *
   * @param values the saved values by key, possibly incomplete or invalid.
   *
   * @return the settings, with the default of every missing or invalid value.
   */
  static Settings of(final Map<String, String> values) {
    return new Settings(theme(values.get(KEY_THEME)),
                        Boolean.parseBoolean(values.get(KEY_SYSTEM_ACCENT)),
                        Boolean.parseBoolean(values.get(KEY_REDUCED_MOTION)),
                        number(values.get(KEY_WINDOW_X), DEFAULT.x()),
                        number(values.get(KEY_WINDOW_Y), DEFAULT.y()),
                        number(values.get(KEY_WINDOW_WIDTH), DEFAULT_WIDTH),
                        number(values.get(KEY_WINDOW_HEIGHT), DEFAULT_HEIGHT),
                        Boolean.parseBoolean(values.get(KEY_MAXIMIZED)),
                        number(values.get(KEY_SIDEBAR), DEFAULT.sidebar()),
                        number(values.get(KEY_DETAIL), DEFAULT.detail()));
  }

  /**
   * Reads a theme.
   *
   * @param value the saved value, or {@code null}.
   *
   * @return the theme, or {@link ThemeMode#SYSTEM} if the value is missing or unknown.
   */
  private static ThemeMode theme(final String value) {
    try {
      return ThemeMode.valueOf(String.valueOf(value));
    } catch (IllegalArgumentException _) {
      return ThemeMode.SYSTEM;
    }
  }

  /**
   * Reads a finite number.
   *
   * @param value the saved value, or {@code null}.
   * @param fallback the value when the saved one is missing, not a number or not finite.
   *
   * @return the number.
   */
  private static double number(final String value, final double fallback) {
    try {
      final double number = Double.parseDouble(String.valueOf(value));
      return Double.isFinite(number) ? number : fallback;
    } catch (NumberFormatException _) {
      return fallback;
    }
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns the values to save.
   *
   * @return the values by key, sorted.
   */
  Map<String, String> values() {
    final Map<String, String> values = new TreeMap<>();
    values.put(KEY_THEME, theme.name());
    values.put(KEY_SYSTEM_ACCENT, String.valueOf(systemAccent));
    values.put(KEY_REDUCED_MOTION, String.valueOf(reducedMotion));
    values.put(KEY_WINDOW_X, String.valueOf(x));
    values.put(KEY_WINDOW_Y, String.valueOf(y));
    values.put(KEY_WINDOW_WIDTH, String.valueOf(width));
    values.put(KEY_WINDOW_HEIGHT, String.valueOf(height));
    values.put(KEY_MAXIMIZED, String.valueOf(maximized));
    values.put(KEY_SIDEBAR, String.valueOf(sidebar));
    values.put(KEY_DETAIL, String.valueOf(detail));
    return values;
  }

  /**
   * Returns these settings with another appearance.
   *
   * @param newTheme the theme chosen.
   * @param newSystemAccent whether the accent colour of the system is used.
   * @param newReducedMotion whether the animations are reduced.
   *
   * @return the new settings.
   */
  Settings withAppearance(final ThemeMode newTheme, final boolean newSystemAccent, final boolean newReducedMotion) {
    return new Settings(newTheme, newSystemAccent, newReducedMotion, x, y, width, height, maximized, sidebar, detail);
  }

  /**
   * Returns these settings with another state of the window.
   *
   * @param newX the left of the window.
   * @param newY the top of the window.
   * @param newWidth the width of the window.
   * @param newHeight the height of the window.
   * @param newMaximized whether the window is maximized.
   *
   * @return the new settings.
   */
  Settings withWindow(final double newX, final double newY, final double newWidth, final double newHeight,
                      final boolean newMaximized) {
    return new Settings(theme, systemAccent, reducedMotion, newX, newY, newWidth, newHeight, newMaximized, sidebar,
                        detail);
  }

  /**
   * Returns these settings with other positions of the dividers.
   *
   * @param newSidebar the position of the divider of the side navigation, or {@link Double#NaN}.
   * @param newDetail the position of the divider of the detail panel, or {@link Double#NaN}.
   *
   * @return the new settings.
   */
  Settings withDividers(final double newSidebar, final double newDetail) {
    return new Settings(theme, systemAccent, reducedMotion, x, y, width, height, maximized, newSidebar, newDetail);
  }

}
