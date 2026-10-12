package ch.varani.jass.app;

/**
 * The choice of the theme of the user interface (DESIGN.md §2): follow the light or dark mode of the system, or
 * always light, or always dark.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
enum ThemeMode {

  /** The theme follows the light or dark mode of the system. */
  SYSTEM,
  /** Always the light theme. */
  LIGHT,
  /** Always the dark theme. */
  DARK

}
