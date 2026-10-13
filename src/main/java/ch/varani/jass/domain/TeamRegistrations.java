package ch.varani.jass.domain;

import java.util.List;
import java.util.stream.Stream;

/**
 * The registrations of the {@link TournamentMode#TEAM} mode: the registered teams, numbered from 1 in their
 * order of registration, and the waiting list, first in, first out, of the teams registered once the edition was
 * full.
 *
 * <p>Every modification returns a new instance and checks that no person is registered twice, the waiting list
 * included.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param teams the registered teams, in their order of registration.
 * @param waiting the waiting list, in its order of registration.
 */
public record TeamRegistrations(List<Team> teams, List<Team> waiting) implements Registrations {

  /**
   * Creates the registrations, copying the lists.
   *
   * @param teams the registered teams.
   * @param waiting the waiting list.
   *
   * @throws NullPointerException if a list or one of its elements is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is registered twice.
   */
  public TeamRegistrations {
    teams = List.copyOf(teams);
    waiting = List.copyOf(waiting);
    RegistrationChecks.checkNoDuplicate(Stream.concat(teams.stream(), waiting.stream())
                                              .flatMap(team -> team.players().stream()).toList());
  }

  /**
   * Creates the registrations without waiting list.
   *
   * @param teams the registered teams.
   *
   * @throws NullPointerException if the list or one of its elements is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is registered twice.
   */
  public TeamRegistrations(final List<Team> teams) {
    this(teams, List.of());
  }

  /**
   * Creates empty registrations.
   *
   * @return registrations without any team.
   */
  public static TeamRegistrations empty() {
    return new TeamRegistrations(List.of());
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
   * Returns the number of registered teams, the waiting list excluded.
   *
   * @return the number of teams.
   */
  @Override
  public int size() {
    return teams.size();
  }

  /**
   * Returns every registered player, in the order of the registrations, the waiting list excluded.
   *
   * @return an unmodifiable {@link List} of the players.
   */
  @Override
  public List<Player> allPlayers() {
    return teams.stream().flatMap(team -> team.players().stream()).toList();
  }

  /**
   * Returns the number of teams on the waiting list.
   *
   * @return the number of waiting teams.
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
    return waiting.stream().flatMap(team -> team.players().stream()).toList();
  }

  /**
   * Returns the minimum number of teams needed to play the given number of games: a team needs {@code games}
   * different opponents.
   *
   * @param games the number of games of each participant.
   *
   * @return the minimum number of teams.
   */
  @Override
  public int minimumSize(final int games) {
    return games + 1;
  }

  /**
   * Returns the team with the given number.
   *
   * @param number the number of the team, starting at 1.
   *
   * @return the team.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no team has this number.
   */
  public Team get(final int number) {
    return teams.get(RegistrationChecks.indexOf(number, teams.size()));
  }

  /**
   * Returns the team at the given position of the waiting list.
   *
   * @param position the position on the waiting list, starting at 1.
   *
   * @return the waiting team.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list has no such
   *                         position.
   */
  public Team waitingAt(final int position) {
    return waiting.get(RegistrationChecks.indexOf(position, waiting.size()));
  }

  /**
   * Returns the players of a team: its two players.
   *
   * @param number the number of the registered team, or its position on the waiting list, starting at 1.
   * @param isWaiting {@code true} for a position on the waiting list.
   *
   * @return an unmodifiable {@link List} of the players.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no team has this number.
   */
  @Override
  public List<Player> participant(final int number, final boolean isWaiting) {
    return (isWaiting ? waitingAt(number) : get(number)).players();
  }

  /**
   * Registers a new team made of players, at the end of the registered teams or of the waiting list.
   *
   * @param players the two players of the team.
   * @param isWaiting {@code true} to add the team to the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#WRONG_TEAM_SIZE} if there are not two players, or with
   *                         {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  @Override
  public TeamRegistrations enrol(final List<Player> players, final boolean isWaiting) {
    final Team team = new Team(players);
    return isWaiting ? addWaiting(team) : add(team);
  }

  /**
   * Replaces a team by another one made of players, which keeps its number or its position.
   *
   * @param number the number of the registered team, or its position on the waiting list, starting at 1.
   * @param players the two players of the team.
   * @param isWaiting {@code true} for a position on the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no team has this number, with
   *                         {@link ValidationError#WRONG_TEAM_SIZE} if there are not two players, or with
   *                         {@link ValidationError#DUPLICATE_PLAYER} if a person would be registered twice.
   */
  @Override
  public TeamRegistrations correct(final int number, final List<Player> players, final boolean isWaiting) {
    final Team team = new Team(players);
    return isWaiting ? replaceWaiting(number, team) : replace(number, team);
  }

  /**
   * Registers a new team, which gets the next number.
   *
   * @param team the team to register.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  public TeamRegistrations add(final Team team) {
    return new TeamRegistrations(RegistrationChecks.inserted(teams, teams.size(), team), waiting);
  }

  /**
   * Adds a team at the end of the waiting list.
   *
   * @param team the waiting team.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  public TeamRegistrations addWaiting(final Team team) {
    return new TeamRegistrations(teams, RegistrationChecks.inserted(waiting, waiting.size(), team));
  }

  /**
   * Replaces the team with the given number, e.g. to correct a name.
   *
   * @param number the number of the team to replace, starting at 1.
   * @param team the new team.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no team has this number, or with
   *                         {@link ValidationError#DUPLICATE_PLAYER} if a person would be registered twice.
   */
  public TeamRegistrations replace(final int number, final Team team) {
    return new TeamRegistrations(RegistrationChecks.replaced(teams, number, team), waiting);
  }

  /**
   * Replaces the team at the given position of the waiting list, e.g. to correct a name.
   *
   * @param position the position on the waiting list, starting at 1.
   * @param team the new team.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list has no such
   *                         position, or with {@link ValidationError#DUPLICATE_PLAYER} if a person would be registered
   *                         twice.
   */
  public TeamRegistrations replaceWaiting(final int position, final Team team) {
    return new TeamRegistrations(teams, RegistrationChecks.replaced(waiting, position, team));
  }

  /**
   * Removes the team with the given number; the following teams are renumbered.
   *
   * @param number the number of the team to remove, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no team has this number.
   */
  @Override
  public TeamRegistrations remove(final int number) {
    return new TeamRegistrations(RegistrationChecks.removed(teams, number), waiting);
  }

  /**
   * Removes a team from the waiting list; the following ones move up.
   *
   * @param position the position on the waiting list, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list has no such
   *                         position.
   */
  @Override
  public TeamRegistrations removeWaiting(final int position) {
    return new TeamRegistrations(teams, RegistrationChecks.removed(waiting, position));
  }

  /**
   * Registers the first team of the waiting list, which gets the next number.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list is empty.
   */
  @Override
  public TeamRegistrations promote() {
    return new TeamRegistrations(RegistrationChecks.inserted(teams, teams.size(), waitingAt(1)),
                       RegistrationChecks.removed(waiting, 1));
  }

  /**
   * Moves the last registered team to the head of the waiting list.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no team is registered.
   */
  @Override
  public TeamRegistrations demote() {
    return new TeamRegistrations(RegistrationChecks.removed(teams, teams.size()),
                       RegistrationChecks.inserted(waiting, 0, get(teams.size())));
  }

  /**
   * Replaces a registered team by the first team of the waiting list, which takes its number; the replaced
   * team leaves the edition.
   *
   * @param number the number of the team to replace, starting at 1.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the waiting list is empty or if no
   *                         team has this number.
   */
  @Override
  public TeamRegistrations replaceByFirstWaiting(final int number) {
    return new TeamRegistrations(RegistrationChecks.replaced(teams, number, waitingAt(1)),
                       RegistrationChecks.removed(waiting, 1));
  }

}
