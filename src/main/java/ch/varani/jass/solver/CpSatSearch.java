package ch.varani.jass.solver;

import ch.varani.jass.domain.Schedule;
import ch.varani.jass.solver.SolverResult.Reason;
import ch.varani.jass.solver.SolverResult.Solved;
import ch.varani.jass.solver.SolverResult.Unsolved;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.Literal;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The search of the CP-SAT solver of Google OR-Tools, shared by the schedule solvers: it loads the native libraries,
 * applies the {@link SolverOptions}, runs the search, handles the cancellation and converts the status of the solver.
 *
 * <p>{@link #solve(CpModel, int, Supplier)} is called once; it reads the solution through {@link #value(Literal)}.
 * {@link #cancel()} may come at any time, from any thread. A cancellation requested before the search
 * starts is reported at its end, which the time limit bounds.</p>
 *
 * <p>Confined to the thread of the solving, except {@link #cancel()} which is safe to call from any thread.</p>
 */
final class CpSatSearch {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The number of milliseconds in a second, to convert the durations of the solver. */
  private static final double MILLIS_PER_SECOND = 1000.0;
  /** The logger of the search; it never logs the names of the participants. */
  private static final Logger LOGGER = Logger.getLogger(CpSatSearch.class.getName());

  /* Loads the native libraries of OR-Tools. */
  static {
    NativeLibraries.load();
  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The parameters of the solver. */
  private final SolverOptions options;
  /** The CP-SAT solver, kept to stop its search on cancellation and to read the solution. */
  private final CpSolver solver;
  /** Whether the search has been cancelled. */
  private final AtomicBoolean cancelled;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a search with the given parameters.
   *
   * @param options the parameters of the solver.
   *
   * @throws NullPointerException if the options are {@code null}.
   */
  CpSatSearch(final SolverOptions options) {
    this.options = Objects.requireNonNull(options, "options");
    this.solver = new CpSolver();
    this.cancelled = new AtomicBoolean();
  }

  // *******************************************************************************************************************
  // Search
  // *******************************************************************************************************************

  /**
   * Solves a model and converts the outcome into a result.
   *
   * <p>The steps are: application of the seed, the number of workers and the time limit of the options, search, log
   * of the status and of the duration with the number of participants (never their names), then conversion: a
   * cancelled search is reported as such, even when a solution was found; otherwise the solution is converted into a
   * schedule, or the status into the reason why there is none.</p>
   *
   * @param <S> the type of the schedule.
   * @param model the model to solve.
   * @param participants the number of participants, for the log.
   * @param schedule converts the solution into a schedule, through {@link #value(Literal)}; called only when a
   *                 solution has been found.
   *
   * @return the schedule, or the reason why there is none.
   *
   * @throws IllegalStateException if the solver reports an invalid model (a programming error).
   */
  <S extends Schedule> SolverResult<S> solve(final CpModel model, final int participants, final Supplier<S> schedule) {
    solver.getParameters()
          .setRandomSeed(options.randomSeed())
          .setNumWorkers(options.workers())
          .setMaxTimeInSeconds(options.timeLimit().toMillis() / MILLIS_PER_SECOND);
    final CpSolverStatus status = solver.solve(model);
    final Duration wallTime = Duration.ofMillis(Math.round(solver.wallTime() * MILLIS_PER_SECOND));
    LOGGER.log(Level.INFO, "Solving for {0} participants ended with status {1} in {2}.",
               new Object[]{participants, status, wallTime});

    if (cancelled.get()) {
      return new Unsolved<>(Reason.CANCELLED);
    }
    if (hasSolution(status)) {
      return new Solved<>(schedule.get());
    }
    return new Unsolved<>(reasonOf(status));
  }

  /**
   * Returns the value of a literal in the solution found by {@link #solve(CpModel, int, Supplier)}.
   *
   * @param literal a literal of the solved model.
   *
   * @return the value of the literal.
   */
  boolean value(final Literal literal) {
    return solver.booleanValue(literal);
  }

  /**
   * Cancels the search: stops it if it is running. Safe to call from any thread, at any time.
   */
  void cancel() {
    cancelled.set(true);
    solver.stopSearch();
  }

  /**
   * Tells whether a status of the CP-SAT solver means that a solution has been found.
   *
   * @param status the status returned by the solver.
   *
   * @return {@code true} for {@link CpSolverStatus#OPTIMAL} and {@link CpSolverStatus#FEASIBLE}, {@code false}
   *         otherwise.
   */
  static boolean hasSolution(final CpSolverStatus status) {
    return status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE;
  }

  /**
   * Converts a status of the CP-SAT solver without solution into the reason why there is no schedule.
   *
   * @param status the status returned by the solver, without solution.
   *
   * @return {@link Reason#INFEASIBLE} for {@link CpSolverStatus#INFEASIBLE}, {@link Reason#TIME_LIMIT} for
   *         {@link CpSolverStatus#UNKNOWN} (the search stopped before finding a solution).
   *
   * @throws IllegalStateException for any other status: an invalid model is a programming error, and a status with a
   *                               solution has no reason.
   */
  static Reason reasonOf(final CpSolverStatus status) {
    return switch (status) {
      case INFEASIBLE -> Reason.INFEASIBLE;
      case UNKNOWN -> Reason.TIME_LIMIT;
      default -> throw new IllegalStateException("Unexpected status of the solver: " + status);
    };
  }

}
