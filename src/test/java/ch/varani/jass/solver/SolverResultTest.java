package ch.varani.jass.solver;

import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.solver.SolverResult.Reason;
import ch.varani.jass.solver.SolverResult.Solved;
import ch.varani.jass.solver.SolverResult.Unsolved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link SolverResult}: both outcomes carry their value and reject a missing one.
 */
final class SolverResultTest {

  /**
   * A result carries its schedule or its reason; a missing value is rejected.
   */
  @Test
  @DisplayName("A result carries its schedule or its reason, never null")
  void resultTest() {
    final TeamSchedule schedule = new TeamSchedule(0, 2, List.of());
    assertAll(
        () -> assertEquals(schedule, new Solved<>(schedule).schedule()),
        () -> assertEquals(Reason.TIME_LIMIT, new Unsolved<>(Reason.TIME_LIMIT).reason()),
        () -> assertThrows(NullPointerException.class, () -> new Solved<>(null)),
        () -> assertThrows(NullPointerException.class, () -> new Unsolved<>(null)));
  }

}
