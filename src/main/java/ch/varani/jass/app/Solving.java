package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.solver.IndividualScheduleSolver;
import ch.varani.jass.solver.ScheduleSolver;
import ch.varani.jass.solver.SolverOptions;
import ch.varani.jass.solver.SolverResult;
import ch.varani.jass.solver.TeamScheduleSolver;

import java.time.Duration;

/**
 * The computation of the schedule of an edition, as seen by the user interface: solve, possibly cancel, within a
 * time limit that the user interface shows as the progress of the computation.
 *
 * <p>{@link #of(Edition)} creates the solver of the mode of the edition: in team mode one worker
 * ({@link SolverOptions#DEFAULT}, the same registrations always give the same schedule), in individual mode eight
 * ({@link SolverOptions#PARALLEL}). The tests replace it through {@link AppContext}.</p>
 *
 * <p>{@link #solve()} runs on a background thread; {@link #cancel()} may be called from any thread.</p>
 */
interface Solving {

  /**
   * Computes the schedule.
   *
   * @return the schedule, or the reason why there is none.
   */
  SolverResult<?> solve();

  /**
   * Stops the computation; {@link #solve()} then returns as soon as possible.
   */
  void cancel();

  /**
   * Returns the longest time the computation may take: the time limit of the solver.
   *
   * @return the time limit; {@link SolverOptions#DEFAULT_TIME_LIMIT} unless the computation says otherwise.
   */
  default Duration timeLimit() {
    return SolverOptions.DEFAULT_TIME_LIMIT;
  }

  /**
   * Creates the computation of the schedule of an edition with the CP-SAT solver of its mode.
   *
   * @param edition the edition, with its registrations.
   *
   * @return the computation.
   */
  static Solving of(final Edition edition) {
    final int participants = edition.registrations().size();
    final boolean team = edition.mode() == TournamentMode.TEAM;
    final SolverOptions options = team ? SolverOptions.DEFAULT : SolverOptions.PARALLEL;
    final ScheduleSolver<?> solver = team ? new TeamScheduleSolver(participants, edition.games(), options)
                                          : new IndividualScheduleSolver(participants, edition.games(), options);
    return new Solving() {

      /**
       * Computes the schedule with the solver.
       *
       * @return the result of the solver.
       */
      @Override
      public SolverResult<?> solve() {
        return solver.solve();
      }

      /**
       * Cancels the solver.
       */
      @Override
      public void cancel() {
        solver.cancel();
      }

      /**
       * Returns the time limit of the options of the solver.
       *
       * @return the time limit.
       */
      @Override
      public Duration timeLimit() {
        return options.timeLimit();
      }

    };
  }

}
