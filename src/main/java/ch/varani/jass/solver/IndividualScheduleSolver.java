package ch.varani.jass.solver;

import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.solver.SolverResult.Reason;
import ch.varani.jass.solver.SolverResult.Unsolved;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.IntVar;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import com.google.ortools.sat.Literal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes the schedule of a tournament in individual mode with the CP-SAT solver of Google OR-Tools: the players
 * register alone, and the solver forms the teams of each game.
 *
 * <p>The players are numbered from 1 to {@code players}, as in the registrations. A table seats
 * {@link Regulation#PLAYERS_PER_TABLE} players: two teams of two partners, the <i>giver</i> (rule 6) and the
 * <i>receiver</i>. When the number of players is not a multiple of {@link Regulation#PLAYERS_PER_TABLE}, the
 * {@code surplus = players mod 4} players that do not fill a table sit out each of the first {@code games} rounds, and
 * a catch-up round of {@code games × surplus / 4} tables lets the players who sat out play their missing game, as rule
 * 1 does for the teams. Each player then sits out at most once.</p>
 *
 * <p><b>Validation</b>, before any modelling: an odd number of games ({@link Reason#ODD_GAMES}); fewer players than
 * a table, or not more players than games ({@link Reason#NOT_ENOUGH_PLAYERS}); surplus players who would not fill
 * the tables of the catch-up round, or who would sit out twice ({@link Reason#NO_CATCH_UP_ROUND}).</p>
 *
 * <p><b>Decision variables</b>: one boolean {@code x[p][r][t][s]} per player {@code p}, round {@code r}, table
 * {@code t} of the round and side {@code s} of the table (0: giver, 1: receiver), equal to 1 when the player sits at
 * this side. For each pair of players {@code (p, q)} and each round {@code r}, two auxiliary booleans
 * {@code partners[p][q][r]} and {@code opponents[p][q][r]}, forced to 1 by clauses when the players sit at the same
 * side, respectively at opposite sides of the same table.</p>
 *
 * <p><b>Constraints</b>:</p>
 * <ol>
 *   <li>a player plays at most once per round;</li>
 *   <li>a player plays exactly the number of games (rule 1); with constraint 1 and the number of tables of each round,
 *       the players who sit out one of the first rounds are all different and play the catch-up round;</li>
 *   <li>each side of each table has exactly {@link Regulation#PLAYERS_PER_TEAM} players;</li>
 *   <li>a player gives {@code games / 2} times (rule 6, as in team mode);</li>
 *   <li>two players are partners at most once over the tournament;</li>
 *   <li>two players at opposite sides of a table are opponents; their number of meetings is the number of rounds in
 *       which they are opponents (having been partners does not count).</li>
 * </ol>
 *
 * <p><b>Objective</b>: the solver minimises first the maximum number of meetings over all the pairs of players, then
 * the total number of repeated meetings ({@code Σ max(0, meetings − 1)}), as one weighted sum whose weight of the
 * maximum exceeds any possible total. The minimisation makes the auxiliary booleans exact in an optimal schedule.</p>
 *
 * <p><b>Symmetry breaking</b>: the players are interchangeable, so the first round is fixed: players 1 and 2 give
 * and players 3 and 4 receive at table 1, players 5 to 8 sit at table 2, and so on; the surplus players sit out the
 * first round. The tables of a round are interchangeable, so player {@code p} (numbered from 0) sits at a table
 * {@code t ≤ p} in the other rounds: sorting the tables by their smallest player gives such a schedule.</p>
 *
 * <p>The solver runs with the seed, the number of workers and the time limit of its {@link SolverOptions}. When the
 * time limit is reached after a schedule has been found, the best schedule found is returned, even if it is not
 * proven optimal. In each round, the games are sorted by the smallest number of the giver team and numbered from table
 * 1; the giver sits at the smaller place of the table (rule 6).</p>
 *
 * <p><b>Postconditions</b>: {@link #solve()} returns a {@link SolverResult.Solved} result whose schedule satisfies
 * every constraint, or an {@link Unsolved} result with the reason.</p>
 *
 * <p>An instance computes one schedule: {@link #solve()} is called once, from one thread; {@link #cancel()} may be
 * called from any thread.</p>
 */
public final class IndividualScheduleSolver implements ScheduleSolver<IndividualSchedule> {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The number of sides of a table: the giver and the receiver. */
  private static final int SIDES = 2;
  /** The index of the side of the giver. */
  private static final int GIVER = 0;
  /** The index of the side of the receiver. */
  private static final int RECEIVER = 1;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The number of players. */
  private final int players;
  /** The number of games each player plays. */
  private final int games;
  /** The number of rounds, catch-up round included. */
  private final int rounds;
  /** The number of players who sit out each of the first {@link #games} rounds. */
  private final int surplus;
  /** The search of the CP-SAT solver, with its options and its cancellation. */
  private final CpSatSearch search;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a solver for a tournament in individual mode.
   *
   * @param players the number of players, numbered from 1.
   * @param games the number of games each player plays ({@code Regulation.GAMES} in the regulation).
   * @param options the parameters of the solver.
   *
   * @throws NullPointerException if the options are {@code null}.
   * @throws IllegalArgumentException if the number of games is lower than 1.
   */
  public IndividualScheduleSolver(final int players, final int games, final SolverOptions options) {
    if (games < 1) {
      throw new IllegalArgumentException("At least one game is needed.");
    }
    this.players = players;
    this.games = games;
    this.rounds = IndividualSchedule.roundsFor(players, games);
    this.surplus = Math.floorMod(players, Regulation.PLAYERS_PER_TABLE);
    this.search = new CpSatSearch(options);
  }

  // *******************************************************************************************************************
  // Constraints
  // *******************************************************************************************************************

  /**
   * Constraint 1: a player plays at most once per round, i.e. the sum of {@code x[p][r][t][s]} over the tables and
   * the sides is at most 1 for every player and every round.
   *
   * @param model the model to which the constraint is added.
   * @param seats the decision variables.
   */
  private void createConstraint1(final CpModel model, final BoolVar[][][][] seats) {
    for (int p = 0; p < players; p++) {
      for (int r = 0; r < rounds; r++) {
        final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
        addSeatsOfPlayerInRound(linearExprBuilder, seats, p, r);
        model.addLessOrEqual(linearExprBuilder.build(), 1);
      }
    }
  }

  /**
   * Constraint 2: a player plays exactly the number of games (rule 1), i.e. the sum of {@code x[p][r][t][s]} over the
   * rounds, the tables and the sides equals {@code games} for every player. With constraint 1 and the number of tables
   * of each round, the players who sit out one of the first rounds are all different and play the catch-up round.
   *
   * @param model the model to which the constraint is added.
   * @param seats the decision variables.
   */
  private void createConstraint2(final CpModel model, final BoolVar[][][][] seats) {
    for (int p = 0; p < players; p++) {
      final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
      for (int r = 0; r < rounds; r++) {
        addSeatsOfPlayerInRound(linearExprBuilder, seats, p, r);
      }
      model.addEquality(linearExprBuilder.build(), games);
    }
  }

  /**
   * Constraint 3: each side of each table has exactly {@link Regulation#PLAYERS_PER_TEAM} players, i.e. the sum of
   * {@code x[p][r][t][s]} over the players equals 2 for every round, table and side.
   *
   * @param model the model to which the constraint is added.
   * @param seats the decision variables.
   */
  private void createConstraint3(final CpModel model, final BoolVar[][][][] seats) {
    for (int r = 0; r < rounds; r++) {
      for (int t = 0; t < tables(r); t++) {
        for (int s = 0; s < SIDES; s++) {
          final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
          for (int p = 0; p < players; p++) {
            linearExprBuilder.add(seats[p][r][t][s]);
          }
          model.addEquality(linearExprBuilder.build(), Regulation.PLAYERS_PER_TEAM);
        }
      }
    }
  }

  /**
   * Constraint 4: a player gives {@code games / 2} times (rule 6, as in team mode), i.e. the sum of
   * {@code x[p][r][t][GIVER]} over the rounds and the tables equals {@code games / 2} for every player.
   *
   * @param model the model to which the constraint is added.
   * @param seats the decision variables.
   */
  private void createConstraint4(final CpModel model, final BoolVar[][][][] seats) {
    for (int p = 0; p < players; p++) {
      final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
      for (int r = 0; r < rounds; r++) {
        for (int t = 0; t < tables(r); t++) {
          linearExprBuilder.add(seats[p][r][t][GIVER]);
        }
      }
      model.addEquality(linearExprBuilder.build(), games / 2);
    }
  }

  /**
   * Constraint 5: two players are partners at most once over the tournament. The boolean {@code partners[p][q][r]}
   * is forced to 1 by the clause {@code ¬x[p][r][t][s] ∨ ¬x[q][r][t][s] ∨ partners[p][q][r]} at every side of every
   * table, and the sum of {@code partners[p][q][r]} over the rounds is at most 1.
   *
   * @param model the model to which the constraint is added.
   * @param seats the decision variables.
   */
  private void createConstraint5(final CpModel model, final BoolVar[][][][] seats) {
    for (int p = 0; p < players; p++) {
      for (int q = p + 1; q < players; q++) {
        final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
        for (int r = 0; r < rounds; r++) {
          final BoolVar partner = model.newBoolVar("partners_" + p + "_" + q + "_" + r);
          for (int t = 0; t < tables(r); t++) {
            for (int s = 0; s < SIDES; s++) {
              model.addBoolOr(new Literal[]{seats[p][r][t][s].not(), seats[q][r][t][s].not(), partner});
            }
          }
          linearExprBuilder.add(partner);
        }
        model.addLessOrEqual(linearExprBuilder.build(), 1);
      }
    }
  }

  /**
   * Constraint 6: two players at opposite sides of a table in a round are opponents. The boolean
   * {@code opponents[p][q][r]} is forced to 1 by the clauses {@code ¬x[p][r][t][GIVER] ∨ ¬x[q][r][t][RECEIVER] ∨
   * opponents[p][q][r]} and {@code ¬x[p][r][t][RECEIVER] ∨ ¬x[q][r][t][GIVER] ∨ opponents[p][q][r]} at every table;
   * the number of meetings of the pair is the sum of {@code opponents[p][q][r]} over the rounds.
   *
   * @param model the model to which the constraint is added.
   * @param seats the decision variables.
   * @param meetings the number of meetings {@code meetings[p][q]} of each pair, for {@code p < q}, filled by this
   *                 method.
   */
  private void createConstraint6(final CpModel model, final BoolVar[][][][] seats, final LinearExpr[][] meetings) {
    for (int p = 0; p < players; p++) {
      for (int q = p + 1; q < players; q++) {
        final LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
        for (int r = 0; r < rounds; r++) {
          final BoolVar opponent = model.newBoolVar("opponents_" + p + "_" + q + "_" + r);
          for (int t = 0; t < tables(r); t++) {
            model.addBoolOr(new Literal[]{seats[p][r][t][GIVER].not(), seats[q][r][t][RECEIVER].not(), opponent});
            model.addBoolOr(new Literal[]{seats[p][r][t][RECEIVER].not(), seats[q][r][t][GIVER].not(), opponent});
          }
          linearExprBuilder.add(opponent);
        }
        meetings[p][q] = linearExprBuilder.build();
      }
    }
  }

  /**
   * Symmetry breaking: fixes the first round, in which players {@code 4t + 1} and {@code 4t + 2} give and players
   * {@code 4t + 3} and {@code 4t + 4} receive at table {@code t + 1}, the surplus players sitting out; in the other
   * rounds, player {@code p} (numbered from 0) sits at a table {@code t ≤ p}. Any schedule can be brought into this
   * form, by renumbering the players (they are interchangeable), then by sorting the tables of each round by their
   * smallest player: the {@code t}-th smallest of these players is at least {@code t}.
   *
   * @param model the model to which the constraint is added.
   * @param seats the decision variables.
   */
  private void breakSymmetries(final CpModel model, final BoolVar[][][][] seats) {
    for (int p = 0; p < players - surplus; p++) {
      final int table = p / Regulation.PLAYERS_PER_TABLE;
      final int side = p % Regulation.PLAYERS_PER_TABLE / Regulation.PLAYERS_PER_TEAM;
      model.addEquality(seats[p][0][table][side], 1);
    }
    for (int r = 1; r < rounds; r++) {
      for (int p = 0; p < players; p++) {
        for (int t = p + 1; t < tables(r); t++) {
          for (int s = 0; s < SIDES; s++) {
            model.addEquality(seats[p][r][t][s], 0);
          }
        }
      }
    }
  }

  /**
   * Sets the objective: minimise first the maximum number of meetings of two players, then the total number of
   * repeated meetings. Both are combined into one weighted sum, the weight of the maximum being greater than the
   * greatest possible total, so that the order of the two criteria is respected.
   *
   * @param model the model whose objective is set.
   * @param meetings the number of meetings of each pair of players of constraint 6.
   */
  private void createObjective(final CpModel model, final LinearExpr[][] meetings) {
    final IntVar maximum = model.newIntVar(0, rounds, "maximum");
    final LinearExprBuilder repeated = LinearExpr.newBuilder();
    long pairs = 0;
    for (int p = 0; p < players; p++) {
      for (int q = p + 1; q < players; q++) {
        model.addLessOrEqual(meetings[p][q], maximum);
        final IntVar excess = model.newIntVar(0, rounds, "repeated_" + p + "_" + q);
        model.addGreaterOrEqual(excess, LinearExpr.affine(meetings[p][q], 1, -1));
        repeated.add(excess);
        pairs++;
      }
    }
    final long weight = pairs * rounds + 1;
    model.minimize(LinearExpr.newBuilder().addTerm(maximum, weight).add(repeated.build()).build());
  }

  /**
   * Adds to an expression the variables of the seats of a player in a round, at every table and side.
   *
   * @param linearExprBuilder the expression to complete.
   * @param seats the decision variables.
   * @param player the index of the player.
   * @param round the index of the round.
   */
  private void addSeatsOfPlayerInRound(final LinearExprBuilder linearExprBuilder, final BoolVar[][][][] seats,
                                       final int player, final int round) {
    for (int t = 0; t < tables(round); t++) {
      for (int s = 0; s < SIDES; s++) {
        linearExprBuilder.add(seats[player][round][t][s]);
      }
    }
  }

  /**
   * Returns the number of tables of a round: {@code players / 4} in the first {@code games} rounds,
   * {@code games × surplus / 4} in the catch-up round.
   *
   * @param round the index of the round.
   *
   * @return the number of tables of the round.
   */
  private int tables(final int round) {
    if (round < games) {
      return players / Regulation.PLAYERS_PER_TABLE;
    }
    return games * surplus / Regulation.PLAYERS_PER_TABLE;
  }

  // *******************************************************************************************************************
  // Solving
  // *******************************************************************************************************************

  /**
   * Computes the schedule.
   *
   * <p>The steps are: validation of the number of games and of players, creation of the decision variables, addition
   * of the six constraints, of the symmetry breaking and of the objective, solving with the {@link SolverOptions},
   * then conversion of the best solution found into a schedule, or of the status of the solver into the reason why
   * there is no schedule. A cancelled solving is reported as such, even when a schedule was found.</p>
   *
   * @return the schedule, or the reason why there is none.
   *
   * @throws IllegalStateException if the solver reports an invalid model (a programming error).
   */
  @Override
  public SolverResult<IndividualSchedule> solve() {
    if (games % 2 != 0) {
      return new Unsolved<>(Reason.ODD_GAMES);
    }
    if (players < Regulation.PLAYERS_PER_TABLE || players <= games) {
      return new Unsolved<>(Reason.NOT_ENOUGH_PLAYERS);
    }
    final int satOut = games * surplus;
    if (satOut % Regulation.PLAYERS_PER_TABLE != 0 || satOut > players) {
      return new Unsolved<>(Reason.NO_CATCH_UP_ROUND);
    }

    final CpModel model = new CpModel();
    final BoolVar[][][][] seats = createVariables(model);
    final LinearExpr[][] meetings = new LinearExpr[players][players];
    createConstraint1(model, seats);
    createConstraint2(model, seats);
    createConstraint3(model, seats);
    createConstraint4(model, seats);
    createConstraint5(model, seats);
    createConstraint6(model, seats, meetings);
    breakSymmetries(model, seats);
    createObjective(model, meetings);
    return search.solve(model, players, () -> createSchedule(seats));
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
   * Creates the decision variables {@code x[p][r][t][s]}, one boolean per player, round, table of the round and side.
   *
   * @param model the model in which the variables are created.
   *
   * @return the decision variables, indexed by player, round, table and side.
   */
  private BoolVar[][][][] createVariables(final CpModel model) {
    final BoolVar[][][][] seats = new BoolVar[players][rounds][][];
    for (int p = 0; p < players; p++) {
      for (int r = 0; r < rounds; r++) {
        seats[p][r] = new BoolVar[tables(r)][SIDES];
        for (int t = 0; t < tables(r); t++) {
          for (int s = 0; s < SIDES; s++) {
            seats[p][r][t][s] = model.newBoolVar("x_" + p + "_" + r + "_" + t + "_" + s);
          }
        }
      }
    }
    return seats;
  }

  /**
   * Converts the solution into a schedule: at each table of each round, the two players of the giver side form the
   * giver team and the two players of the receiver side the receiver team (players numbered from 1). In each round,
   * the games are sorted by the smallest number of the giver team and numbered from table 1.
   *
   * @param seats the decision variables, holding the solution.
   *
   * @return the schedule.
   */
  private IndividualSchedule createSchedule(final BoolVar[][][][] seats) {
    final List<IndividualRound> schedule = new ArrayList<>(rounds);
    for (int r = 0; r < rounds; r++) {
      final List<IndividualGame> round = new ArrayList<>();
      for (int t = 0; t < tables(r); t++) {
        round.add(new IndividualGame(0, partnersAt(seats, r, t, GIVER), partnersAt(seats, r, t, RECEIVER)));
      }
      round.sort(Comparator.comparingInt(game -> game.giver().first()));
      final List<IndividualGame> numbered = new ArrayList<>(round.size());
      for (final IndividualGame game : round) {
        numbered.add(new IndividualGame(numbered.size() + 1, game.giver(), game.receiver()));
      }
      schedule.add(new IndividualRound(r + 1, numbered));
    }
    return new IndividualSchedule(players, games, schedule);
  }

  /**
   * Returns the two players sitting at a side of a table in the solution.
   *
   * @param seats the decision variables, holding the solution.
   * @param round the index of the round.
   * @param table the index of the table.
   * @param side the index of the side.
   *
   * @return the two partners, numbered from 1.
   */
  private Partners partnersAt(final BoolVar[][][][] seats, final int round, final int table, final int side) {
    final int[] found = new int[Regulation.PLAYERS_PER_TEAM];
    int count = 0;
    for (int p = 0; p < players; p++) {
      if (search.value(seats[p][round][table][side])) {
        found[count++] = p + 1;
      }
    }
    return new Partners(found[0], found[1]);
  }

}
