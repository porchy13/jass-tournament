package ch.varani.jass.domain;

import java.util.List;

/**
 * The schedule of a team tournament: the rounds, and in each round the games with their table and places.
 *
 * <p>The schedule refers to the teams by their number in the registrations, so that renaming a player keeps it valid;
 * {@link Edition} checks that it matches the number of teams and of games of the edition.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param teams the number of teams of the tournament.
 * @param games the number of games each team plays.
 * @param rounds the rounds, sorted by number.
 */
public record TeamSchedule(int teams, int games, List<TeamRound> rounds) implements Schedule {

  /**
   * Creates a schedule, copying the list of rounds.
   *
   * @param teams the number of teams of the tournament.
   * @param games the number of games each team plays.
   * @param rounds the rounds, sorted by number.
   *
   * @throws NullPointerException if the list or one of its rounds is {@code null}.
   */
  public TeamSchedule {
    rounds = List.copyOf(rounds);
  }

  /**
   * Returns the number of rounds needed to play the given number of games: one per game, plus a catch-up round when
   * the number of teams is odd (rule 1).
   *
   * @param teams the number of teams.
   * @param games the number of games each team plays.
   *
   * @return the number of rounds.
   */
  public static int roundsFor(final int teams, final int games) {
    return games + teams % 2;
  }

  /**
   * Returns the {@link TournamentMode#TEAM} mode.
   *
   * @return {@link TournamentMode#TEAM}.
   */
  @Override
  public TournamentMode mode() {
    return TournamentMode.TEAM;
  }

  /**
   * Returns the number of teams.
   *
   * @return the number of teams.
   */
  @Override
  public int participants() {
    return teams;
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
                                                                 List.of(game.giver()), List.of(game.receiver()))))
                 .sorted(PAIRING_ORDER)
                 .toList();
  }

}
