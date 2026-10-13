package ch.varani.jass.domain;

import java.util.List;

/**
 * The registrations of an edition: teams in team mode, players in individual mode.
 *
 * <p>The registrations are numbered from 1 in their order of registration; the number of a team (or of a player in
 * individual mode) is its position. Removing a registration renumbers the following ones.</p>
 *
 * <p><b>Waiting list</b>: the participants registered once the edition is full ({@link Edition#capacity()}) wait in a
 * list, first in, first out, numbered from 1 too: the first one takes the place of a participant who withdraws
 * ({@link #promote()}, {@link #replaceByFirstWaiting(int)}). They take part neither in the schedule nor in the
 * accounting; {@link #size()} and {@link #allPlayers()} count the registered participants only.</p>
 *
 * <p>Every implementation is immutable (the modifications return new instances) and guarantees that no person is
 * registered twice, the waiting list included. Safe for concurrent use.</p>
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
   * Returns the number of participants on the waiting list.
   *
   * @return the number of waiting teams (or players in individual mode).
   */
  int waitingSize();

  /**
   * Returns every player of the waiting list, in its order.
   *
   * @return an unmodifiable {@link List} of the waiting players.
   */
  List<Player> waitingPlayers();

  /**
   * Returns the players of a participant: the two players of a team, or the player in individual mode.
   *
   * @param number the number of the registered participant, or its position on the waiting list, starting at 1.
   * @param waiting {@code true} for a position on the waiting list.
   *
   * @return an unmodifiable {@link List} of the players.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no participant has this number.
   */
  List<Player> participant(int number, boolean waiting);

  /**
   * Registers a new participant made of players, at the end of the registered participants or of the waiting list.
   *
   * @param players the two players of a team, or the player in individual mode.
   * @param waiting {@code true} to add the participant to the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#WRONG_TEAM_SIZE} if the number of players does not match the
   *                         mode, or with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  Registrations enrol(List<Player> players, boolean waiting);

  /**
   * Replaces a participant by another one made of players, which keeps its number or its position, e.g. to correct a
   * name.
   *
   * @param number the number of the registered participant, or its position on the waiting list, starting at 1.
   * @param players the two players of a team, or the player in individual mode.
   * @param waiting {@code true} for a position on the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no participant has this number, with
   *                         {@link ValidationError#WRONG_TEAM_SIZE} if the number of players does not match the mode,
   *                         or with {@link ValidationError#DUPLICATE_PLAYER} if a person would be registered twice.
   */
  Registrations correct(int number, List<Player> players, boolean waiting);

  /**
   * Removes the registered participant with the given number; the following ones are renumbered.
   *
   * @param number the number of the participant to remove, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no participant has this number.
   */
  Registrations remove(int number);

  /**
   * Removes a participant from the waiting list; the following ones move up.
   *
   * @param position the position on the waiting list, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list has no such
   *                         position.
   */
  Registrations removeWaiting(int position);

  /**
   * Registers the first participant of the waiting list, which gets the next number.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list is empty.
   */
  Registrations promote();

  /**
   * Moves the last registered participant to the head of the waiting list, e.g. when the maximum is lowered.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no participant is registered.
   */
  Registrations demote();

  /**
   * Replaces a registered participant by the first participant of the waiting list, which takes its number (and its
   * place in the schedule); the replaced participant leaves the edition.
   *
   * @param number the number of the participant to replace, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list is empty or if no
   *                         participant has this number.
   */
  Registrations replaceByFirstWaiting(int number);

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
