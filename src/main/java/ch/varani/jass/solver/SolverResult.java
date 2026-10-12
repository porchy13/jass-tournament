package ch.varani.jass.solver;

import ch.varani.jass.domain.Schedule;

import java.util.Objects;

/**
 * The outcome of a solving: either a schedule ({@link Solved}) or the reason why there is none ({@link Unsolved}).
 *
 * <p>Every implementation is immutable and safe for concurrent use.</p>
 *
 * @param <S> the type of the schedule: {@link ch.varani.jass.domain.TeamSchedule} or
 *            {@link ch.varani.jass.domain.IndividualSchedule}.
 */
public sealed interface SolverResult<S extends Schedule> permits SolverResult.Solved, SolverResult.Unsolved {

  /**
   * The reasons why no schedule is returned. The user interface translates each of them into a localized message.
   *
   * <p>Immutable, safe for concurrent use.</p>
   */
  enum Reason {

    /** The number of games is odd: a participant cannot give in half of its games and receive in the other half. */
    ODD_GAMES,

    /** There are not more teams than games: a team cannot meet a different opponent in each game. */
    NOT_ENOUGH_TEAMS,

    /**
     * There are not more players than games, or fewer than a table: a player cannot have a different partner in each
     * game.
     */
    NOT_ENOUGH_PLAYERS,

    /**
     * Individual mode: the surplus players, who sit out the first rounds, cannot play a catch-up round. Either they
     * would not fill complete tables, or there are more of them than players, so that a player would sit out twice.
     */
    NO_CATCH_UP_ROUND,

    /** The solver proved that no schedule satisfies the constraints. */
    INFEASIBLE,

    /** The time limit was reached before a schedule was found. */
    TIME_LIMIT,

    /** The solving was cancelled. */
    CANCELLED

  }

  /**
   * A schedule has been found.
   *
   * <p>Immutable, safe for concurrent use.</p>
   *
   * @param <S> the type of the schedule.
   * @param schedule the schedule, which satisfies every constraint of the model.
   */
  record Solved<S extends Schedule>(S schedule) implements SolverResult<S> {

    /**
     * Creates the result of a successful solving.
     *
     * @param schedule the schedule.
     *
     * @throws NullPointerException if the schedule is {@code null}.
     */
    public Solved {
      Objects.requireNonNull(schedule, "schedule");
    }

  }

  /**
   * No schedule has been found.
   *
   * <p>Immutable, safe for concurrent use.</p>
   *
   * @param <S> the type of the schedule that would have been returned.
   * @param reason the reason why there is no schedule.
   */
  record Unsolved<S extends Schedule>(Reason reason) implements SolverResult<S> {

    /**
     * Creates the result of a solving without schedule.
     *
     * @param reason the reason why there is no schedule.
     *
     * @throws NullPointerException if the reason is {@code null}.
     */
    public Unsolved {
      Objects.requireNonNull(reason, "reason");
    }

  }

}
