package ch.varani.jass.app;

import javafx.application.ColorScheme;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;

/**
 * The theme of the window (DESIGN.md §2, §3, §12): the style sheets of the light or dark tokens, then the base and the
 * components, following the light or dark mode of the system unless the user chose one; and, when the user asks for
 * it, the accent colour of the system instead of the accent of the application.
 *
 * <p><b>Algorithm</b>: the dark theme is used in {@link ThemeMode#DARK}, or in {@link ThemeMode#SYSTEM} when the
 * system is in dark mode; the scene gets {@value #DARK_TOKENS} or {@value #LIGHT_TOKENS}, {@value #BASE} and
 * {@value #COMPONENTS}. The accent of the system replaces the tokens {@code -c-accent}, {@code -c-accent-subtle},
 * {@code -c-selection} and {@code -c-on-accent} on the root of the scene: the subtle colour and the selection are the
 * accent mixed with white (light theme) or black (dark theme), the text on the accent is black on a light accent
 * (luminance above 0.6), white otherwise. The theme is applied again when the mode or the accent of the system
 * changes.</p>
 *
 * <p>The message boxes take the same style sheets and tokens ({@link Dialogs}).</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class Theme {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The tokens of the light theme. */
  static final String LIGHT_TOKENS = "tokens-light.css";
  /** The tokens of the dark theme. */
  static final String DARK_TOKENS = "tokens-dark.css";
  /** The base styles. */
  static final String BASE = "base.css";
  /** The styles of the components. */
  static final String COMPONENTS = "components.css";
  /** The share of white in the subtle accent of the light theme. */
  private static final double LIGHT_SUBTLE = 0.88;
  /** The share of white in the selection of the light theme. */
  private static final double LIGHT_SELECTION = 0.82;
  /** The share of black in the subtle accent of the dark theme. */
  private static final double DARK_SUBTLE = 0.72;
  /** The share of black in the selection of the dark theme. */
  private static final double DARK_SELECTION = 0.6;
  /** The luminance above which the text on the accent is black. */
  private static final double BRIGHT = 0.6;
  /** The weight of the red in the luminance (ITU-R BT.709). */
  private static final double RED_WEIGHT = 0.2126;
  /** The weight of the green in the luminance (ITU-R BT.709). */
  private static final double GREEN_WEIGHT = 0.7152;
  /** The weight of the blue in the luminance (ITU-R BT.709). */
  private static final double BLUE_WEIGHT = 0.0722;
  /** The largest value of a colour component in the CSS {@code rgb()} notation. */
  private static final int COMPONENT = 255;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The scene of the window. */
  private final Scene scene;
  /** The light or dark mode of the system. */
  private final ObservableValue<ColorScheme> scheme;
  /** The accent colour of the system. */
  private final ObservableValue<Color> accent;
  /** The choice of the user. */
  private ThemeMode mode = ThemeMode.SYSTEM;
  /** Whether the accent colour of the system replaces the accent of the application. */
  private boolean systemAccent;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Applies the theme following the system to a scene, and follows the changes of the system.
   *
   * @param scene the scene of the window, with its root.
   * @param scheme the light or dark mode of the system ({@code Platform.getPreferences().colorSchemeProperty()}).
   * @param accent the accent colour of the system ({@code Platform.getPreferences().accentColorProperty()}).
   */
  Theme(final Scene scene, final ObservableValue<ColorScheme> scheme, final ObservableValue<Color> accent) {
    this.scene = scene;
    this.scheme = scheme;
    this.accent = accent;
    scheme.addListener((property, before, after) -> apply());
    accent.addListener((property, before, after) -> apply());
    apply();
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Changes the choices of the user, and applies them.
   *
   * @param newMode the theme chosen.
   * @param useSystemAccent whether the accent colour of the system replaces the accent of the application.
   */
  void configure(final ThemeMode newMode, final boolean useSystemAccent) {
    mode = newMode;
    systemAccent = useSystemAccent;
    apply();
  }

  /**
   * Tells whether the dark theme is shown.
   *
   * @return {@code true} for the dark theme.
   */
  boolean isDark() {
    return switch (mode) {
      case LIGHT -> false;
      case DARK -> true;
      case SYSTEM -> scheme.getValue() == ColorScheme.DARK;
    };
  }

  /**
   * Applies the style sheets and the accent to the scene.
   */
  private void apply() {
    final boolean dark = isDark();
    scene.getStylesheets().setAll(stylesheets(dark));
    scene.getRoot().setStyle(systemAccent ? accentStyle(accent.getValue(), dark) : "");
  }

  /**
   * Returns the style sheets of a theme, in the order they apply.
   *
   * @param dark {@code true} for the dark theme.
   *
   * @return the addresses of the tokens, the base and the components.
   */
  static List<String> stylesheets(final boolean dark) {
    return List.of(url(dark ? DARK_TOKENS : LIGHT_TOKENS), url(BASE), url(COMPONENTS));
  }

  /**
   * Returns the address of a style sheet of this package.
   *
   * @param name the name of the style sheet.
   *
   * @return its address.
   */
  private static String url(final String name) {
    return Theme.class.getResource(name).toExternalForm();
  }

  /**
   * Returns the tokens of an accent colour, to set on the root of a scene.
   *
   * @param color the accent colour.
   * @param dark {@code true} for the dark theme.
   *
   * @return the CSS declarations of the accent tokens.
   */
  static String accentStyle(final Color color, final boolean dark) {
    final Color subtle = dark ? color.interpolate(Color.BLACK, DARK_SUBTLE) : color.interpolate(Color.WHITE,
                                                                                                 LIGHT_SUBTLE);
    final Color selection = dark ? color.interpolate(Color.BLACK, DARK_SELECTION) : color.interpolate(Color.WHITE,
                                                                                                      LIGHT_SELECTION);
    final double luminance = RED_WEIGHT * color.getRed() + GREEN_WEIGHT * color.getGreen()
                             + BLUE_WEIGHT * color.getBlue();
    final Color onAccent = luminance > BRIGHT ? Color.BLACK : Color.WHITE;
    return "-c-accent: " + css(color) + "; -c-accent-subtle: " + css(subtle) + "; -c-selection: " + css(selection)
           + "; -c-on-accent: " + css(onAccent) + ";";
  }

  /**
   * Writes a colour in the CSS notation {@code rgb(r, g, b)}.
   *
   * @param color the colour.
   *
   * @return its notation.
   */
  private static String css(final Color color) {
    return String.format(Locale.ROOT, "rgb(%d, %d, %d)", Math.round(color.getRed() * COMPONENT),
                         Math.round(color.getGreen() * COMPONENT), Math.round(color.getBlue() * COMPONENT));
  }

}
