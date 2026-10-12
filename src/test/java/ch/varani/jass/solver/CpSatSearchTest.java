package ch.varani.jass.solver;

import com.google.ortools.sat.CpSolverStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link CpSatSearch}: conversion of the statuses of the CP-SAT solver and missing options.
 */
final class CpSatSearchTest {

  /**
   * Missing options are rejected.
   */
  @Test
  @DisplayName("Missing options are rejected")
  void missingOptionsTest() {
    assertThrows(NullPointerException.class, () -> new CpSatSearch(null));
  }

  /**
   * Only the {@code OPTIMAL} and {@code FEASIBLE} statuses of the solver mean that a solution has been found.
   *
   * @param status the status of the solver.
   */
  @ParameterizedTest
  @EnumSource(CpSolverStatus.class)
  @DisplayName("Only OPTIMAL and FEASIBLE mean that a solution has been found")
  void hasSolutionTest(final CpSolverStatus status) {
    assertEquals(status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE,
                 CpSatSearch.hasSolution(status));
  }

  /**
   * The statuses without solution are converted into a reason; the other ones are a programming error.
   *
   * @param status the status of the solver.
   */
  @ParameterizedTest
  @EnumSource(CpSolverStatus.class)
  @DisplayName("INFEASIBLE and UNKNOWN give a reason, the other statuses are rejected")
  void reasonOfTest(final CpSolverStatus status) {
    switch (status) {
      case INFEASIBLE -> assertEquals(SolverResult.Reason.INFEASIBLE, CpSatSearch.reasonOf(status));
      case UNKNOWN -> assertEquals(SolverResult.Reason.TIME_LIMIT, CpSatSearch.reasonOf(status));
      default -> assertThrows(IllegalStateException.class, () -> CpSatSearch.reasonOf(status));
    }
  }

}
