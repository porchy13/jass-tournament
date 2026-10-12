package ch.varani.jass.app;

import javafx.application.ColorScheme;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Theme} (DESIGN.md §2, §3): the light or dark tokens follow the system unless the user chose a
 * theme; the accent of the system replaces the accent of the application on request.
 */
@ExtendWith(ApplicationExtension.class)
final class ThemeTest {

  /**
   * The theme follows the mode of the system, then the choice of the user; the style sheets are the tokens, the base
   * and the components.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("The theme follows the system unless the user chooses one")
  void modeTest(final FxRobot robot) {
    final ObjectProperty<ColorScheme> scheme = new SimpleObjectProperty<>(ColorScheme.LIGHT);
    final ObjectProperty<Color> accent = new SimpleObjectProperty<>(Color.BLUE);
    final List<Boolean> dark = new ArrayList<>();
    final List<String> sheets = new ArrayList<>();
    robot.interact(() -> {
      final Scene scene = new Scene(new StackPane());
      final Theme theme = new Theme(scene, scheme, accent);
      dark.add(theme.isDark());
      scheme.set(ColorScheme.DARK);
      dark.add(theme.isDark());
      sheets.add(scene.getStylesheets().getFirst());
      theme.configure(ThemeMode.LIGHT, false);
      dark.add(theme.isDark());
      theme.configure(ThemeMode.DARK, false);
      dark.add(theme.isDark());
      sheets.add(scene.getRoot().getStyle());
    });
    assertAll(
        () -> assertEquals(List.of(false, true, false, true), dark),
        () -> assertTrue(sheets.getFirst().endsWith(Theme.DARK_TOKENS)),
        () -> assertEquals("", sheets.getLast()),
        () -> assertTrue(Theme.stylesheets(false).getFirst().endsWith(Theme.LIGHT_TOKENS)),
        () -> assertTrue(Theme.stylesheets(false).getLast().endsWith(Theme.COMPONENTS)));
  }

  /**
   * The accent of the system replaces the accent tokens, mixed with white in the light theme and with black in the
   * dark one; the text on a light accent is black, on a dark one white; a change of the accent is applied.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("The accent of the system replaces the accent of the application")
  void accentTest(final FxRobot robot) {
    final ObjectProperty<Color> accent = new SimpleObjectProperty<>(Color.rgb(0, 0, 255));
    final List<String> styles = new ArrayList<>();
    robot.interact(() -> {
      final Scene scene = new Scene(new StackPane());
      final Theme theme = new Theme(scene, new SimpleObjectProperty<>(ColorScheme.LIGHT), accent);
      theme.configure(ThemeMode.SYSTEM, true);
      styles.add(scene.getRoot().getStyle());
      accent.set(Color.YELLOW);
      styles.add(scene.getRoot().getStyle());
    });
    final String dark = Theme.accentStyle(Color.rgb(0, 0, 255), true);
    assertAll(
        () -> assertTrue(styles.getFirst().startsWith("-c-accent: rgb(0, 0, 255); -c-accent-subtle: rgb("),
                         styles.getFirst()),
        () -> assertTrue(styles.getFirst().endsWith("-c-on-accent: rgb(255, 255, 255);"), styles.getFirst()),
        () -> assertTrue(styles.getLast().endsWith("-c-on-accent: rgb(0, 0, 0);"), styles.getLast()),
        () -> assertTrue(dark.contains("-c-accent-subtle: rgb(0, 0, 71)"), dark),
        () -> assertFalse(dark.contains("rgb(255, 255, 0)")));
  }

}
