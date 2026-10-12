package ch.varani.jass.solver;

import java.time.Duration;
import java.util.Objects;

/**
 * The parameters of the CP-SAT solver: random seed, number of search workers and time limit.
 *
 * <p>With one worker and a fixed seed, the solving is reproducible: the same input always gives the same schedule. With
 * several workers, the workers race and the schedule found first depends on the timing of the threads; when the
 * solver proves that a schedule is optimal, its quality (the value of the objective) is reproducible, not the schedule
 * itself. The time limit bounds the solving when no schedule is found quickly.</p>
 *
 * <p>{@link #DEFAULT} suits the team mode, which has no objective; {@link #PARALLEL} suits the individual mode, whose
 * optimisation needs the portfolio of search strategies that several workers run.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param randomSeed the random seed of the solver.
 * @param workers the number of search workers, at least 1.
 * @param timeLimit the maximum duration of the solving, strictly positive.
 */
public record SolverOptions(int randomSeed, int workers, Duration timeLimit) {

  /** The default random seed. */
  public static final int DEFAULT_RANDOM_SEED = 0;
  /** The default number of search workers: one, so that the solving is reproducible. */
  public static final int DEFAULT_WORKERS = 1;
  /**
   * The default time limit: a tournament of a few dozen teams is solved in less than a second, a tournament of 100
   * players in individual mode in about 20 seconds.
   */
  public static final Duration DEFAULT_TIME_LIMIT = Duration.ofSeconds(60);
  /**
   * The number of search workers of the {@link #PARALLEL} options: eight workers run the portfolio of search
   * strategies of CP-SAT (local search, large neighbourhood search…), with which a tournament of 100 players in
   * individual mode is solved to optimality within the default time limit.
   */
  public static final int PARALLEL_WORKERS = 8;
  /** The default options: one worker, reproducible. */
  public static final SolverOptions DEFAULT = new SolverOptions(DEFAULT_RANDOM_SEED, DEFAULT_WORKERS,
                                                                DEFAULT_TIME_LIMIT);
  /** The options of the individual mode: {@link #PARALLEL_WORKERS} workers. */
  public static final SolverOptions PARALLEL = new SolverOptions(DEFAULT_RANDOM_SEED, PARALLEL_WORKERS,
                                                                 DEFAULT_TIME_LIMIT);

  /**
   * Creates options, checking their values.
   *
   * @param randomSeed the random seed of the solver.
   * @param workers the number of search workers.
   * @param timeLimit the maximum duration of the solving.
   *
   * @throws NullPointerException if the time limit is {@code null}.
   * @throws IllegalArgumentException if the number of workers is lower than 1 or the time limit is not strictly
   *                                  positive.
   */
  public SolverOptions {
    Objects.requireNonNull(timeLimit, "timeLimit");
    if (workers < 1) {
      throw new IllegalArgumentException("At least one worker is needed.");
    }
    if (timeLimit.isNegative() || timeLimit.isZero()) {
      throw new IllegalArgumentException("The time limit must be strictly positive.");
    }
  }

}
