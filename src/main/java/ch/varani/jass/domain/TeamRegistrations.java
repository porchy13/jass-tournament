package ch.varani.jass.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The registrations of the {@link TournamentMode#TEAM} mode: the registered teams, numbered from 1 in their
 * order of registration.
 *
 * <p>Every modification returns a new instance and checks that no person is registered twice.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param teams the registered teams, in their order of registration.
 */
public record TeamRegistrations(List<Team> teams) implements Registrations {

  /**
   * Creates the registrations, copying the list of teams.
   *
   * @param teams the registered teams.
   *
   * @throws NullPointerException if the list or one of its elements is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is registered twice.
   */
  public TeamRegistrations {
    teams = List.copyOf(teams);
    RegistrationChecks.checkNoDuplicate(teams.stream().flatMap(team -> team.players().stream()).toList());
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
   * Returns the number of registered teams.
   *
   * @return the number of teams.
   */
  @Override
  public int size() {
    return teams.size();
  }

  /**
   * Returns every registered player, in the order of the registrations.
   *
   * @return an unmodifiable {@link List} of the players.
   */
  @Override
  public List<Player> allPlayers() {
    return teams.stream().flatMap(team -> team.players().stream()).toList();
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
   * Registers a new team, which gets the next number.
   *
   * @param team the team to register.
   *
   * @return the new registrations.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  public TeamRegistrations add(final Team team) {
    final List<Team> copy = new ArrayList<>(teams);
    copy.add(team);
    return new TeamRegistrations(copy);
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
    final List<Team> copy = new ArrayList<>(teams);
    copy.set(RegistrationChecks.indexOf(number, teams.size()), team);
    return new TeamRegistrations(copy);
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
  public TeamRegistrations remove(final int number) {
    final List<Team> copy = new ArrayList<>(teams);
    copy.remove(RegistrationChecks.indexOf(number, teams.size()));
    return new TeamRegistrations(copy);
  }

}
