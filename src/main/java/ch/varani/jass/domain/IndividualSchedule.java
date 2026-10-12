package ch.varani.jass.domain;

import java.util.List;

/**
 * The schedule of a tournament in {@link TournamentMode#INDIVIDUAL} mode: the rounds, and in each round the games with
 * their table, their places and the teams formed by the solver.
 *
 * <p>The schedule refers to the players by their number in the registrations, so that renaming a player keeps it
 * valid; {@link Edition} checks that it matches the number of players and of games of the edition.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param players the number of players of the tournament.
 * @param games the number of games each player plays.
 * @param rounds the rounds, sorted by number.
 */
public record IndividualSchedule(int players, int games, List<IndividualRound> rounds) implements Schedule {

  /**
   * Creates a schedule, copying the list of rounds.
   *
   * @param players the number of players of the tournament.
   * @param games the number of games each player plays.
   * @param rounds the rounds, sorted by number.
   *
   * @throws NullPointerException if the list or one of its rounds is {@code null}.
   */
  public IndividualSchedule {
    rounds = List.copyOf(rounds);
  }

  /**
   * Returns the number of rounds needed to play the given number of games: one per game, plus a catch-up round when
   * the number of players is not a multiple of {@link Regulation#PLAYERS_PER_TABLE}, as rule 1 does for the teams.
   *
   * @param players the number of players.
   * @param games the number of games each player plays.
   *
   * @return the number of rounds.
   */
  public static int roundsFor(final int players, final int games) {
    return players % Regulation.PLAYERS_PER_TABLE == 0 ? games : games + 1;
  }

  /**
   * Returns the {@link TournamentMode#INDIVIDUAL} mode.
   *
   * @return {@link TournamentMode#INDIVIDUAL}.
   */
  @Override
  public TournamentMode mode() {
    return TournamentMode.INDIVIDUAL;
  }

  /**
   * Returns the number of players.
   *
   * @return the number of players.
   */
  @Override
  public int participants() {
    return players;
  }

  /**
   * Returns the games of the schedule, sorted by round then table.
   *
   * @return the games with the participants of their sides.
   */
  @Override
  public List<Pairing> pairings() {
    return rounds.stream()
                 .flatMap(round -> round.games().stream()
                                        .map(game -> new Pairing(round.number(), game.table(),
                                                                 List.of(game.giver().first(), game.giver().second()),
                                                                 List.of(game.receiver().first(),
                                                                         game.receiver().second()))))
                 .sorted(PAIRING_ORDER)
                 .toList();
  }

}
