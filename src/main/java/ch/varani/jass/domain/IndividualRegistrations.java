package ch.varani.jass.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The registrations of the {@link TournamentMode#INDIVIDUAL} mode: the registered players, numbered from 1 in their
 * order of registration.
 *
 * <p>Every modification returns a new instance and checks that no person is registered twice.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param players the registered players, in their order of registration.
 */
public record IndividualRegistrations(List<Player> players) implements Registrations {

  /**
   * Creates the registrations, copying the list of players.
   *
   * @param players the registered players.
   *
   * @throws NullPointerException if the list or one of its elements is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is registered twice.
   */
  public IndividualRegistrations {
    players = List.copyOf(players);
    RegistrationChecks.checkNoDuplicate(players);
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
   * Returns the number of registered players.
   *
   * @return the number of players.
   */
  @Override
  public int size() {
    return players.size();
  }

  /**
   * Returns every registered player, in the order of the registrations.
   *
   * @return an unmodifiable {@link List} of the players.
   */
  @Override
  public List<Player> allPlayers() {
    return players;
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
   * Registers a new player, which gets the next number.
   *
   * @param player the player to register.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  public IndividualRegistrations add(final Player player) {
    final List<Player> copy = new ArrayList<>(players);
    copy.add(player);
    return new IndividualRegistrations(copy);
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
    final List<Player> copy = new ArrayList<>(players);
    copy.set(RegistrationChecks.indexOf(number, players.size()), player);
    return new IndividualRegistrations(copy);
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
  public IndividualRegistrations remove(final int number) {
    final List<Player> copy = new ArrayList<>(players);
    copy.remove(RegistrationChecks.indexOf(number, players.size()));
    return new IndividualRegistrations(copy);
  }

}
