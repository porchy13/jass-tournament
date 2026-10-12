package ch.varani.jass.solver;

import ch.varani.jass.domain.Schedule;

/**
 * A solver computing the schedule of a tournament: {@link TeamScheduleSolver} in team mode,
 * {@link IndividualScheduleSolver} in individual mode.
 *
 * <p>An instance computes one schedule: {@link #solve()} is called once, from one thread, usually a background thread
 * of the user interface; {@link #cancel()} may be called from any thread.</p>
 *
 * @param <S> the type of the computed schedule.
 */
public sealed interface ScheduleSolver<S extends Schedule> permits TeamScheduleSolver, IndividualScheduleSolver {

  /**
   * Computes the schedule.
   *
   * @return the schedule, or the reason why there is none.
   */
  SolverResult<S> solve();

  /**
   * Cancels the solving: stops the search if it is running; {@link #solve()} then returns
   * {@link SolverResult.Reason#CANCELLED}. Safe to call from any thread, at any time.
   */
  void cancel();

}
