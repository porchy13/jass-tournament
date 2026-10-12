package ch.varani.jass.domain;

import java.util.Comparator;
import java.util.List;

/**
 * The schedule of a tournament, computed once the registration is closed: the rounds, and in each round the games with
 * their table and places. Its type gives the tournament mode: {@link TeamSchedule} or {@link IndividualSchedule}.
 *
 * <p>A schedule refers to the participants by their number in the registrations, so that renaming a player keeps it
 * valid; {@link Edition} checks that it matches the mode, the number of participants and the number of games of the
 * edition.</p>
 *
 * <p>Every implementation is immutable and safe for concurrent use.</p>
 */
public sealed interface Schedule permits TeamSchedule, IndividualSchedule {

  /**
   * Returns the tournament mode of this schedule.
   *
   * @return {@link TournamentMode#TEAM} or {@link TournamentMode#INDIVIDUAL}.
   */
  TournamentMode mode();

  /**
   * Returns the number of participants of the tournament: teams in team mode, players in individual mode.
   *
   * @return the number of participants.
   */
  int participants();

  /**
   * Returns the number of games each participant plays.
   *
   * @return the number of games.
   */
  int games();

  /**
   * Returns the games of the schedule, each with its round, its table and the participants of its two sides, sorted
   * by round then table.
   *
   * @return the games.
   */
  List<Pairing> pairings();

  /**
   * The order of the games: by round, then by table.
   */
  Comparator<Pairing> PAIRING_ORDER = Comparator.comparingInt(Pairing::round).thenComparingInt(Pairing::table);

}
