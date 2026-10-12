package ch.varani.jass.solver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link SolverOptions}: default values and validation.
 */
final class SolverOptionsTest {

  /**
   * The default options use the default seed, number of workers and time limit.
   */
  @Test
  @DisplayName("The default options use one worker, the default seed and the default time limit")
  void defaultTest() {
    assertAll(
        () -> assertEquals(SolverOptions.DEFAULT_RANDOM_SEED, SolverOptions.DEFAULT.randomSeed()),
        () -> assertEquals(1, SolverOptions.DEFAULT.workers()),
        () -> assertEquals(SolverOptions.DEFAULT_TIME_LIMIT, SolverOptions.DEFAULT.timeLimit()));
  }

  /**
   * The parallel options differ from the default ones only by their number of workers.
   */
  @Test
  @DisplayName("The parallel options use several workers, the default seed and the default time limit")
  void parallelTest() {
    assertAll(
        () -> assertEquals(SolverOptions.DEFAULT_RANDOM_SEED, SolverOptions.PARALLEL.randomSeed()),
        () -> assertEquals(SolverOptions.PARALLEL_WORKERS, SolverOptions.PARALLEL.workers()),
        () -> assertEquals(SolverOptions.DEFAULT_TIME_LIMIT, SolverOptions.PARALLEL.timeLimit()));
  }

  /**
   * Less than one worker, a time limit that is not strictly positive and a missing time limit are rejected.
   */
  @Test
  @DisplayName("Less than one worker and a time limit that is not strictly positive are rejected")
  void validationTest() {
    final Duration limit = SolverOptions.DEFAULT_TIME_LIMIT;
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new SolverOptions(0, 0, limit)),
        () -> assertThrows(IllegalArgumentException.class, () -> new SolverOptions(0, 1, Duration.ZERO)),
        () -> assertThrows(IllegalArgumentException.class, () -> new SolverOptions(0, 1, Duration.ofSeconds(-1))),
        () -> assertThrows(NullPointerException.class, () -> new SolverOptions(0, 1, null)));
  }

}
