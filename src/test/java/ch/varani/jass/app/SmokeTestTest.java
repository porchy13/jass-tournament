package ch.varani.jass.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link SmokeTest} and of the argument {@value SmokeTest#ARGUMENT} of {@link Launcher}: the smoke test
 * fails without schedule, and passes through the launcher (solver, PDF, JavaFX toolkit started and stopped). In a JVM
 * of its own, as the JavaFX toolkit starts once per JVM.
 */
final class SmokeTestTest {

  /**
   * Without schedule (a single team), the smoke test fails before starting JavaFX; the launcher then runs the whole
   * smoke test and returns.
   */
  @Test
  @DisplayName("The smoke test fails without schedule and passes through the launcher")
  void smokeTest() {
    final IllegalStateException unsolved = assertThrows(IllegalStateException.class, () -> SmokeTest.run(1));
    assertAll(
        () -> assertTrue(unsolved.getMessage().startsWith("No schedule"), unsolved.getMessage()),
        () -> assertDoesNotThrow(() -> Launcher.main(new String[] {SmokeTest.ARGUMENT})));
  }

}
