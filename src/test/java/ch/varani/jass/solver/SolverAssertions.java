package ch.varani.jass.solver;

import ch.varani.jass.domain.Schedule;
import ch.varani.jass.solver.SolverResult.Solved;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Assertions on the results of the solvers, shared by the solver tests.
 */
final class SolverAssertions {

  /**
   * Prevents the instantiation of this utility class.
   */
  private SolverAssertions() {
  }

  /**
   * Asserts that a solving found a schedule and returns it.
   *
   * @param <S> the type of the schedule.
   * @param result the result of the solving.
   *
   * @return the schedule of the result.
   */
  static <S extends Schedule> S solved(final SolverResult<S> result) {
    if (result instanceof final Solved<S> solved) {
      return solved.schedule();
    }
    return fail("No schedule: " + result);
  }

}
