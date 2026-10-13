package ch.varani.jass.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * The registrations of the {@link TournamentMode#INDIVIDUAL} mode: the registered players, numbered from 1 in their
 * order of registration, and the waiting list, first in, first out, of the players registered once the edition was
 * full.
 *
 * <p>Every modification returns a new instance and checks that no person is registered twice, the waiting list
 * included.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param players the registered players, in their order of registration.
 * @param waiting the waiting list, in its order of registration.
 */
public record IndividualRegistrations(List<Player> players, List<Player> waiting) implements Registrations {

  /**
   * Creates the registrations, copying the lists.
   *
   * @param players the registered players.
   * @param waiting the waiting list.
   *
   * @throws NullPointerException if a list or one of its elements is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is registered twice.
   */
  public IndividualRegistrations {
    players = List.copyOf(players);
    waiting = List.copyOf(waiting);
    RegistrationChecks.checkNoDuplicate(Stream.concat(players.stream(), waiting.stream()).toList());
  }

  /**
   * Creates the registrations without waiting list.
   *
   * @param players the registered players.
   *
   * @throws NullPointerException if the list or one of its elements is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is registered twice.
   */
  public IndividualRegistrations(final List<Player> players) {
    this(players, List.of());
  }

  /**
   * Creates empty registrations.
   *
   * @return registrations without any player.
   */
  public static IndividualRegistrations empty() {
    return new IndividualRegistrations(List.of());
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
   * Returns the number of registered players, the waiting list excluded.
   *
   * @return the number of players.
   */
  @Override
  public int size() {
    return players.size();
  }

  /**
   * Returns every registered player, in the order of the registrations, the waiting list excluded.
   *
   * @return an unmodifiable {@link List} of the players.
   */
  @Override
  public List<Player> allPlayers() {
    return players;
  }

  /**
   * Returns the number of players on the waiting list.
   *
   * @return the number of waiting players.
   */
  @Override
  public int waitingSize() {
    return waiting.size();
  }

  /**
   * Returns every player of the waiting list, in its order.
   *
   * @return an unmodifiable {@link List} of the waiting players.
   */
  @Override
  public List<Player> waitingPlayers() {
    return waiting;
  }

  /**
   * Returns the minimum number of players needed to play the given number of games: a player needs {@code games}
   * different partners, and a table needs {@link Regulation#PLAYERS_PER_TABLE} players.
   *
   * @param games the number of games of each participant.
   *
   * @return the minimum number of players.
   */
  @Override
  public int minimumSize(final int games) {
    return Math.max(Regulation.PLAYERS_PER_TABLE, games + 1);
  }

  /**
   * Returns the player with the given number.
   *
   * @param number the number of the player, starting at 1.
   *
   * @return the player.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no player has this number.
   */
  public Player get(final int number) {
    return players.get(RegistrationChecks.indexOf(number, players.size()));
  }

  /**
   * Returns the player at the given position of the waiting list.
   *
   * @param position the position on the waiting list, starting at 1.
   *
   * @return the waiting player.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list has no such
   *                         position.
   */
  public Player waitingAt(final int position) {
    return waiting.get(RegistrationChecks.indexOf(position, waiting.size()));
  }

  /**
   * Returns the players of a player: the player alone.
   *
   * @param number the number of the registered player, or its position on the waiting list, starting at 1.
   * @param isWaiting {@code true} for a position on the waiting list.
   *
   * @return an unmodifiable {@link List} of the players.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no player has this number.
   */
  @Override
  public List<Player> participant(final int number, final boolean isWaiting) {
    return List.of(isWaiting ? waitingAt(number) : get(number));
  }

  /**
   * Registers a new player made of players, at the end of the registered players or of the waiting list.
   *
   * @param players the player, alone in the list.
   * @param isWaiting {@code true} to add the player to the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#WRONG_TEAM_SIZE} if the list does not hold one player, or with
   *                         {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  @Override
  public IndividualRegistrations enrol(final List<Player> players, final boolean isWaiting) {
    final Player player = RegistrationChecks.single(players);
    return isWaiting ? addWaiting(player) : add(player);
  }

  /**
   * Replaces a player by another one made of players, which keeps its number or its position.
   *
   * @param number the number of the registered player, or its position on the waiting list, starting at 1.
   * @param players the player, alone in the list.
   * @param isWaiting {@code true} for a position on the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no player has this number, with
   *                         {@link ValidationError#WRONG_TEAM_SIZE} if the list does not hold one player, or with
   *                         {@link ValidationError#DUPLICATE_PLAYER} if a person would be registered twice.
   */
  @Override
  public IndividualRegistrations correct(final int number, final List<Player> players, final boolean isWaiting) {
    final Player player = RegistrationChecks.single(players);
    return isWaiting ? replaceWaiting(number, player) : replace(number, player);
  }

  /**
   * Registers a new player, which gets the next number.
   *
   * @param player the player to register.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  public IndividualRegistrations add(final Player player) {
    return new IndividualRegistrations(RegistrationChecks.inserted(players, players.size(), player), waiting);
  }

  /**
   * Adds a player at the end of the waiting list.
   *
   * @param player the waiting player.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  public IndividualRegistrations addWaiting(final Player player) {
    return new IndividualRegistrations(players, RegistrationChecks.inserted(waiting, waiting.size(), player));
  }

  /**
   * Replaces the player with the given number, e.g. to correct a name.
   *
   * @param number the number of the player to replace, starting at 1.
   * @param player the new player.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no player has this number, or with
   *                         {@link ValidationError#DUPLICATE_PLAYER} if a person would be registered twice.
   */
  public IndividualRegistrations replace(final int number, final Player player) {
    return new IndividualRegistrations(RegistrationChecks.replaced(players, number, player), waiting);
  }

  /**
   * Replaces the player at the given position of the waiting list, e.g. to correct a name.
   *
   * @param position the position on the waiting list, starting at 1.
   * @param player the new player.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list has no such
   *                         position, or with {@link ValidationError#DUPLICATE_PLAYER} if a person would be registered
   *                         twice.
   */
  public IndividualRegistrations replaceWaiting(final int position, final Player player) {
    return new IndividualRegistrations(players, RegistrationChecks.replaced(waiting, position, player));
  }

  /**
   * Removes the player with the given number; the following players are renumbered.
   *
   * @param number the number of the player to remove, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no player has this number.
   */
  @Override
  public IndividualRegistrations remove(final int number) {
    return new IndividualRegistrations(RegistrationChecks.removed(players, number), waiting);
  }

  /**
   * Removes a player from the waiting list; the following ones move up.
   *
   * @param position the position on the waiting list, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list has no such
   *                         position.
   */
  @Override
  public IndividualRegistrations removeWaiting(final int position) {
    return new IndividualRegistrations(players, RegistrationChecks.removed(waiting, position));
  }

  /**
   * Registers the first player of the waiting list, which gets the next number.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list is empty.
   */
  @Override
  public IndividualRegistrations promote() {
    return new IndividualRegistrations(RegistrationChecks.inserted(players, players.size(), waitingAt(1)),
                       RegistrationChecks.removed(waiting, 1));
  }

  /**
   * Moves the last registered player to the head of the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no player is registered.
   */
  @Override
  public IndividualRegistrations demote() {
    return new IndividualRegistrations(RegistrationChecks.removed(players, players.size()),
                       RegistrationChecks.inserted(waiting, 0, get(players.size())));
  }

  /**
   * Replaces a registered player by the first player of the waiting list, which takes its number; the replaced
   * player leaves the edition.
   *
   * @param number the number of the player to replace, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list is empty or if no
   *                         player has this number.
   */
  @Override
  public IndividualRegistrations replaceByFirstWaiting(final int number) {
    return new IndividualRegistrations(RegistrationChecks.replaced(players, number, waitingAt(1)),
                       RegistrationChecks.removed(waiting, 1));
  }

}
