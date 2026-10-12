package ch.varani.jass.domain;

/**
 * The two partners of a team formed by the solver for one game of the {@link TournamentMode#INDIVIDUAL} mode.
 *
 * <p>The players are identified by their number in the registrations ({@link IndividualRegistrations#get(int)}). The
 * order of the two players does not matter: the constructor sorts them, so that {@link #first()} is the smaller
 * number and two teams of the same players are equal.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param first the smaller number of the two players.
 * @param second the greater number of the two players.
 */
public record Partners(int first, int second) {

  /**
   * Creates a team of two partners, sorting their numbers.
   *
   * @param first the number of one player.
   * @param second the number of the other player.
   */
  public Partners {
    if (first > second) {
      final int swapped = first;
      first = second;
      second = swapped;
    }
  }

}
