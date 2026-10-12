package ch.varani.jass.domain;

import java.util.List;

/**
 * The registrations of an edition: teams in team mode, players in individual mode.
 *
 * <p>The registrations are numbered from 1 in their order of registration; the number of a team (or of a player in
 * individual mode) is its position. Removing a registration renumbers the following ones.</p>
 *
 * <p>Every implementation is immutable (the modifications return new instances) and guarantees that no person is
 * registered twice. Safe for concurrent use.</p>
 */
public sealed interface Registrations permits TeamRegistrations, IndividualRegistrations {

  /**
   * Returns the tournament mode of these registrations.
   *
   * @return {@link TournamentMode#TEAM} or {@link TournamentMode#INDIVIDUAL}.
   */
  TournamentMode mode();

  /**
   * Returns the number of registrations (teams or players).
   *
   * @return the number of registrations.
   */
  int size();

  /**
   * Returns every registered player, in the order of the registrations.
   *
   * @return an unmodifiable {@link List} of the players.
   */
  List<Player> allPlayers();

  /**
   * Returns the minimum number of registrations needed to play the given number of games: each participant must meet
   * {@code games} different opponents (team mode) or partners (individual mode). This is a necessary condition only;
   * the solver decides whether a schedule exists.
   *
   * @param games the number of games of each participant.
   *
   * @return the minimum number of registrations.
   */
  int minimumSize(int games);

}
