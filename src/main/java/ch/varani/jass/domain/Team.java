package ch.varani.jass.domain;

import java.util.List;

/**
 * A team of the team mode: {@link Regulation#PLAYERS_PER_TEAM} different players.
 *
 * <p>The number of the team is not part of the team: it is its position in the registrations
 * ({@link TeamRegistrations}).</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param players the players of the team.
 */
public record Team(List<Player> players) {

  /**
   * Creates a team, copying the list of players.
   *
   * @param players the players of the team.
   *
   * @throws NullPointerException if the list or one of its players is {@code null}.
   * @throws DomainException with {@link ValidationError#WRONG_TEAM_SIZE} if the team does not have exactly
   *                         {@link Regulation#PLAYERS_PER_TEAM} players, or with
   *                         {@link ValidationError#DUPLICATE_PLAYER} if both players are the same person.
   */
  public Team {
    players = List.copyOf(players);
    if (players.size() != Regulation.PLAYERS_PER_TEAM) {
      throw new DomainException(ValidationError.WRONG_TEAM_SIZE);
    }
    if (players.get(0).isSamePersonAs(players.get(1))) {
      throw new DomainException(ValidationError.DUPLICATE_PLAYER);
    }
  }

  /**
   * Creates a team of two players.
   *
   * @param first the first player.
   * @param second the second player.
   *
   * @throws NullPointerException if a player is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if both players are the same person.
   */
  public Team(final Player first, final Player second) {
    this(List.of(first, second));
  }

}
