package ch.varani.jass.solver;

import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.solver.SolverResult.Reason;
import ch.varani.jass.solver.SolverResult.Unsolved;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the schedule of a team tournament (team mode of the regulation) with the CP-SAT solver of Google OR-Tools.
 *
 * <p>The teams are numbered from 1 to {@code teams}, as in the registrations. The number of rounds is the number of
 * games, plus one catch-up round when the number of teams is odd (rule 1).</p>
 *
 * <p><b>Validation</b>, before any modelling: an odd number of games ({@link Reason#ODD_GAMES}) or not more teams than
 * games ({@link Reason#NOT_ENOUGH_TEAMS}) cannot give a schedule.</p>
 *
 * <p><b>Decision variables</b>: one boolean {@code x[i][j][r]} per ordered pair of teams {@code (i, j)} and per round
 * {@code r}, equal to 1 when team {@code i} gives (deals first, rule 6) and team {@code j} receives in round
 * {@code r}.</p>
 *
 * <p><b>Constraints</b>:</p>
 * <ol>
 *   <li>a team never plays against itself;</li>
 *   <li>two teams play against each other at most once;</li>
 *   <li>each team plays exactly the number of games (rule 1);</li>
 *   <li>each team plays exactly once per round when the number of teams is even, at most once otherwise;</li>
 *   <li>each team gives {@code games / 2} times;</li>
 *   <li>each team receives {@code games / 2} times;</li>
 *   <li>when the number of teams is odd, exactly one team sits out each of the first {@code games} rounds (rule 1).
 *       With constraints 3 and 4, the teams that sat out are all different and the catch-up round is played by them
 *       only.</li>
 * </ol>
 *
 * <p>There is no objective: the first schedule found is returned. In each round, the games are sorted by giver and
 * then by receiver, and numbered from table 1; the giver sits at the smaller place of the table (rule 6).</p>
 *
 * <p>The solver runs with the seed, the number of workers and the time limit of its {@link SolverOptions}; with the
 * {@link SolverOptions#DEFAULT default options}, the same input always gives the same schedule.
 * {@link #cancel()} stops the search from another thread; a cancellation requested before the search starts is
 * reported at its end, which the time limit bounds.</p>
 *
 * <p><b>Postconditions</b>: {@link #solve()} returns a {@link SolverResult.Solved} result whose schedule satisfies
 * every constraint, or an {@link Unsolved} result with the reason.</p>
 *
 * <p>An instance computes one schedule: {@link #solve()} is called once, from one thread; {@link #cancel()} may be
 * called from any thread.</p>
 */
public final class TeamScheduleSolver implements ScheduleSolver<TeamSchedule> {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The number of teams. */
  private final int teams;
  /** The number of games each team plays. */
  private final int games;
  /** The number of rounds, catch-up round included. */
  private final int rounds;
  /** The search of the CP-SAT solver, with its options and its cancellation. */
  private final CpSatSearch search;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a solver for a team tournament.
   *
   * @param teams the number of teams, numbered from 1.
   * @param games the number of games each team plays ({@code Regulation.GAMES} in the regulation).
   * @param options the parameters of the solver.
   *
   * @throws NullPointerException if the options are {@code null}.
   * @throws IllegalArgumentException if the number of games is lower than 1.
   */
  public TeamScheduleSolver(final int teams, final int games, final SolverOptions options) {
    if (games < 1) {
      throw new IllegalArgumentException("At least one game is needed.");
    }
    this.teams = teams;
    this.games = games;
    this.rounds = TeamSchedule.roundsFor(teams, games);
    this.search = new CpSatSearch(options);
  }

  // *******************************************************************************************************************
  // Constraints
  // *******************************************************************************************************************

  /**
   * Constraint 1: a team never plays against itself, i.e. the sum of the variables {@code x[i][i][r]} over the rounds
   * is 0 for every team {@code i}.
   *
   * @param model the model to which the constraint is added.
   * @param confrontations the decision variables.
   */
  private void createConstraint1(final CpModel model, final BoolVar[][][] confrontations) {
    for (int i = 0; i < teams; i++) {
      final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
      for (int r = 0; r < rounds; r++) {
        linearExprBuilder.add(confrontations[i][i][r]);
      }
      model.addEquality(linearExprBuilder.build(), 0);
    }
  }

  /**
   * Constraint 2: two teams play against each other at most once, i.e. the sum of {@code x[i][j][r] + x[j][i][r]}
   * over the rounds is at most 1 for every pair of distinct teams.
   *
   * @param model the model to which the constraint is added.
   * @param confrontations the decision variables.
   */
  private void createConstraint2(final CpModel model, final BoolVar[][][] confrontations) {
    for (int i = 0; i < teams; i++) {
      for (int j = i + 1; j < teams; j++) {
        final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
        for (int r = 0; r < rounds; r++) {
          linearExprBuilder.add(confrontations[i][j][r]).add(confrontations[j][i][r]);
        }
        model.addLessOrEqual(linearExprBuilder.build(), 1);
      }
    }
  }

  /**
   * Constraint 3: each team plays exactly the number of games, i.e. the sum of the games given and received by a team
   * over all the rounds equals {@code games} (rule 1).
   *
   * @param model the model to which the constraint is added.
   * @param confrontations the decision variables.
   */
  private void createConstraint3(final CpModel model, final BoolVar[][][] confrontations) {
    for (int i = 0; i < teams; i++) {
      final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
      for (int r = 0; r < rounds; r++) {
        addGamesOfTeamInRound(linearExprBuilder, confrontations, i, r);
      }
      model.addEquality(linearExprBuilder.build(), games);
    }
  }

  /**
   * Constraint 4: each team plays once per round; exactly once when the number of teams is even, at most once when it
   * is odd (one team sits out each regular round and plays the catch-up round, rule 1).
   *
   * @param model the model to which the constraint is added.
   * @param confrontations the decision variables.
   */
  private void createConstraint4(final CpModel model, final BoolVar[][][] confrontations) {
    final boolean evenTeams = teams % 2 == 0;
    for (int i = 0; i < teams; i++) {
      for (int r = 0; r < rounds; r++) {
        final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
        addGamesOfTeamInRound(linearExprBuilder, confrontations, i, r);
        if (evenTeams) {
          model.addEquality(linearExprBuilder.build(), 1);
        } else {
          model.addLessOrEqual(linearExprBuilder.build(), 1);
        }
      }
    }
  }

  /**
   * Constraint 5: each team gives {@code games / 2} times, i.e. the sum of {@code x[i][j][r]} over the opponents and
   * the rounds equals {@code games / 2} for every team {@code i}.
   *
   * @param model the model to which the constraint is added.
   * @param confrontations the decision variables.
   */
  private void createConstraint5(final CpModel model, final BoolVar[][][] confrontations) {
    for (int i = 0; i < teams; i++) {
      final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
      for (int j = 0; j < teams; j++) {
        for (int r = 0; r < rounds; r++) {
          linearExprBuilder.add(confrontations[i][j][r]);
        }
      }
      model.addEquality(linearExprBuilder.build(), games / 2);
    }
  }

  /**
   * Constraint 6: each team receives {@code games / 2} times, i.e. the sum of {@code x[j][i][r]} over the opponents
   * and the rounds equals {@code games / 2} for every team {@code i}.
   *
   * @param model the model to which the constraint is added.
   * @param confrontations the decision variables.
   */
  private void createConstraint6(final CpModel model, final BoolVar[][][] confrontations) {
    for (int i = 0; i < teams; i++) {
      final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
      for (int j = 0; j < teams; j++) {
        for (int r = 0; r < rounds; r++) {
          linearExprBuilder.add(confrontations[j][i][r]);
        }
      }
      model.addEquality(linearExprBuilder.build(), games / 2);
    }
  }

  /**
   * Constraint 7: when the number of teams is odd, each of the first {@code games} rounds has exactly
   * {@code (teams − 1) / 2} games, i.e. exactly one team sits out (rule 1). The teams that sat out then play the
   * catch-up round, the last one: since a team plays at most once per round (constraint 4) and plays every game
   * (constraint 3), it cannot sit out twice. Nothing is added when the number of teams is even.
   *
   * @param model the model to which the constraint is added.
   * @param confrontations the decision variables.
   */
  private void createConstraint7(final CpModel model, final BoolVar[][][] confrontations) {
    if (teams % 2 == 0) {
      return;
    }
    for (int r = 0; r < games; r++) {
      final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
      for (int i = 0; i < teams; i++) {
        for (int j = 0; j < teams; j++) {
          linearExprBuilder.add(confrontations[i][j][r]);
        }
      }
      model.addEquality(linearExprBuilder.build(), (teams - 1) / 2);
    }
  }

  /**
   * Adds to an expression the variables of the games played by a team in a round, as giver and as receiver, against
   * every other team.
   *
   * @param linearExprBuilder the expression to complete.
   * @param confrontations the decision variables.
   * @param team the index of the team.
   * @param round the index of the round.
   */
  private void addGamesOfTeamInRound(final LinearExprBuilder linearExprBuilder,
                                     final BoolVar[][][] confrontations,
                                     final int team,
                                     final int round) {
    for (int j = 0; j < teams; j++) {
      if (j != team) {
        linearExprBuilder.add(confrontations[team][j][round]).add(confrontations[j][team][round]);
      }
    }
  }

  // *******************************************************************************************************************
  // Solving
  // *******************************************************************************************************************

  /**
   * Computes the schedule.
   *
   * <p>The steps are: validation of the number of games and of teams, creation of the decision variables, addition of
   * the seven constraints, solving with the {@link SolverOptions}, then conversion of the solution into a schedule, or
   * of the status of the solver into the reason why there is no schedule. A cancelled solving is reported as such,
   * even when a schedule was found.</p>
   *
   * @return the schedule, or the reason why there is none.
   *
   * @throws IllegalStateException if the solver reports an invalid model (a programming error).
   */
  @Override
  public SolverResult<TeamSchedule> solve() {
    if (games % 2 != 0) {
      return new Unsolved<>(Reason.ODD_GAMES);
    }
    if (teams <= games) {
      return new Unsolved<>(Reason.NOT_ENOUGH_TEAMS);
    }

    final CpModel model = new CpModel();
    final BoolVar[][][] confrontations = createVariables(model);
    createConstraint1(model, confrontations);
    createConstraint2(model, confrontations);
    createConstraint3(model, confrontations);
    createConstraint4(model, confrontations);
    createConstraint5(model, confrontations);
    createConstraint6(model, confrontations);
    createConstraint7(model, confrontations);
    return search.solve(model, teams, () -> createSchedule(confrontations));
  }

  /**
   * Cancels the solving: stops the search if it is running; {@link #solve()} then returns
   * {@link Reason#CANCELLED}. Safe to call from any thread, at any time.
   */
  @Override
  public void cancel() {
    search.cancel();
  }

  /**
   * Creates the decision variables {@code x[i][j][r]}, one boolean per ordered pair of teams and per round.
   *
   * @param model the model in which the variables are created.
   *
   * @return the decision variables, indexed by giver, receiver and round.
   */
  private BoolVar[][][] createVariables(final CpModel model) {
    final BoolVar[][][] confrontations = new BoolVar[teams][teams][rounds];
    for (int i = 0; i < teams; i++) {
      for (int j = 0; j < teams; j++) {
        for (int r = 0; r < rounds; r++) {
          confrontations[i][j][r] = model.newBoolVar("x_" + i + "_" + j + "_" + r);
        }
      }
    }
    return confrontations;
  }

  /**
   * Converts the solution into a schedule: each variable {@code x[i][j][r]} equal to 1 becomes a game of round
   * {@code r + 1} where team {@code i + 1} gives and team {@code j + 1} receives. In each round, the games are sorted
   * by giver and then by receiver and numbered from table 1.
   *
   * @param confrontations the decision variables, holding the solution.
   *
   * @return the schedule.
   */
  private TeamSchedule createSchedule(final BoolVar[][][] confrontations) {
    final List<TeamRound> schedule = new ArrayList<>(rounds);
    for (int r = 0; r < rounds; r++) {
      final List<TeamGame> round = new ArrayList<>();
      for (int i = 0; i < teams; i++) {
        for (int j = 0; j < teams; j++) {
          if (search.value(confrontations[i][j][r])) {
            round.add(new TeamGame(round.size() + 1, i + 1, j + 1));
          }
        }
      }
      schedule.add(new TeamRound(r + 1, round));
    }
    return new TeamSchedule(teams, games, schedule);
  }

}
