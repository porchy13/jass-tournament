package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.scoring.Ranking;
import ch.varani.jass.scoring.RankingEntry;
import ch.varani.jass.scoring.Scoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The content of the external display (F18), without the toolkit: the rounds and the places of the participants in a
 * round, the countdown of a game, the pages that alternate when the lines do not fit, and the lines of the final
 * ranking revealed so far.
 *
 * <p><b>Places</b>: at table {@code k}, the giver side sits at place {@code 2k − 1} and the receiver side at place
 * {@code 2k} (rule 6); in individual mode each player has the place of its side; a participant without game in the
 * round sits out (place 0).</p>
 *
 * <p><b>Countdown</b>: before the start signal, the whole time of a game remains; afterwards, the time of a game minus
 * the time elapsed since the signal, never below zero.</p>
 *
 * <p><b>Pages</b> (Q22): when the lines do not fit, they are cut into pages of equal size (the last one shorter), and
 * the page shown changes every {@value #PAGE_SECONDS} seconds, computed from the clock so that every refresh shows the
 * same page during these seconds.</p>
 *
 * <p><b>Final ranking</b>: revealed from the last line to the first; the lines revealed are shown in the order of the
 * ranking, the best first.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class DisplayModel {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** How long a page of lines stays shown before the next one, in seconds. */
  static final long PAGE_SECONDS = 10;
  /** The seconds of a minute. */
  private static final long MINUTE = 60;

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The place of a participant in a round.
   *
   * @param participant the number of the team, or of the player in individual mode.
   * @param table the number of its table, 0 when it sits out.
   * @param place its place, 0 when it sits out.
   */
  record PlaceLine(int participant, int table, int place) {
  }

  /**
   * A page of lines.
   *
   * @param <T> the type of the lines.
   * @param lines the lines of the page.
   * @param number the number of the page, from 1.
   * @param count the number of pages.
   */
  record Page<T>(List<T> lines, int number, int count) {

    /**
     * Creates a page, copying its lines.
     *
     * @param lines the lines of the page.
     * @param number the number of the page.
     * @param count the number of pages.
     */
    Page {
      lines = List.copyOf(lines);
    }

  }

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private DisplayModel() {
  }

  // *******************************************************************************************************************
  // Rounds and places
  // *******************************************************************************************************************

  /**
   * Returns the numbers of the rounds of the schedule.
   *
   * @param edition the edition.
   *
   * @return the rounds, in their order, empty without schedule.
   */
  static List<Integer> rounds(final Edition edition) {
    if (!edition.isScheduled()) {
      return List.of();
    }
    return edition.schedule().pairings().stream().map(Pairing::round).distinct().toList();
  }

  /**
   * Returns the round in progress: the first round with a game without result, or the last round when every game has
   * a result.
   *
   * @param edition the edition.
   *
   * @return the number of the round, 1 without schedule.
   */
  static int currentRound(final Edition edition) {
    final List<Integer> rounds = rounds(edition);
    if (rounds.isEmpty()) {
      return 1;
    }
    final Scoring scoring = new Scoring(edition);
    return edition.schedule().pairings().stream()
                  .filter(game -> scoring.result(game.round(), game.table()).isEmpty())
                  .map(Pairing::round)
                  .findFirst()
                  .orElse(rounds.getLast());
  }

  /**
   * Returns the place of every participant in a round, by number.
   *
   * @param edition the scheduled edition.
   * @param round the number of the round.
   *
   * @return one line per registered participant.
   */
  static List<PlaceLine> places(final Edition edition, final int round) {
    final List<Pairing> games = edition.schedule().pairings().stream().filter(game -> game.round() == round)
                                       .toList();
    final List<PlaceLine> lines = new ArrayList<>();
    for (int participant = 1; participant <= edition.registrations().size(); participant++) {
      lines.add(placeOf(games, participant));
    }
    return lines;
  }

  /**
   * Finds the place of a participant among the games of a round.
   *
   * @param games the games of the round.
   * @param participant the number of the participant.
   *
   * @return its line: table and place, or zeros when it sits out.
   */
  private static PlaceLine placeOf(final List<Pairing> games, final int participant) {
    for (final Pairing game : games) {
      if (game.giver().contains(participant)) {
        return new PlaceLine(participant, game.table(), 2 * game.table() - 1);
      }
      if (game.receiver().contains(participant)) {
        return new PlaceLine(participant, game.table(), 2 * game.table());
      }
    }
    return new PlaceLine(participant, 0, 0);
  }

  // *******************************************************************************************************************
  // Countdown
  // *******************************************************************************************************************

  /**
   * Computes the time remaining in the game.
   *
   * @param start the start signal, or {@code null} before it.
   * @param minutes the time given to play a game, in minutes.
   * @param now the current instant.
   *
   * @return the time remaining, zero once it is over.
   */
  static Duration remaining(final Instant start, final int minutes, final Instant now) {
    final Duration game = Duration.ofMinutes(minutes);
    if (start == null) {
      return game;
    }
    final Duration left = game.minus(Duration.between(start, now));
    return left.isNegative() ? Duration.ZERO : left;
  }

  /**
   * Writes a time as minutes and seconds, the minutes going beyond 59, e.g. {@code 75:00}.
   *
   * @param time the time.
   *
   * @return its text.
   */
  static String clock(final Duration time) {
    final long seconds = time.toSeconds();
    return String.format(Locale.ROOT, "%02d:%02d", seconds / MINUTE, seconds % MINUTE);
  }

  // *******************************************************************************************************************
  // Pages
  // *******************************************************************************************************************

  /**
   * Returns the page of lines shown at an instant.
   *
   * @param <T> the type of the lines.
   * @param lines all the lines.
   * @param size the number of lines that fit, at least 1.
   * @param now the current instant.
   *
   * @return the page: all the lines when they fit, else the page of the moment.
   */
  static <T> Page<T> page(final List<T> lines, final int size, final Instant now) {
    if (lines.size() <= size) {
      return new Page<>(lines, 1, 1);
    }
    final int count = (lines.size() + size - 1) / size;
    final int index = (int) (now.getEpochSecond() / PAGE_SECONDS % count);
    return new Page<>(lines.subList(index * size, Math.min(lines.size(), (index + 1) * size)), index + 1, count);
  }

  // *******************************************************************************************************************
  // Final ranking
  // *******************************************************************************************************************

  /**
   * Tells whether the final ranking can be revealed: every game has a result and no participant shares a rank (the
   * ties are broken by drawing lots, rule 15).
   *
   * @param ranking the ranking.
   *
   * @return {@code true} if the ranking is final.
   */
  static boolean isFinal(final Ranking ranking) {
    return ranking.complete() && !ranking.hasTies();
  }

  /**
   * Returns the lines of the ranking revealed so far, from the last one.
   *
   * @param ranking the ranking.
   * @param count the number of lines revealed; more than the lines means all of them.
   *
   * @return the last {@code count} lines, the best first.
   */
  static List<RankingEntry> revealed(final Ranking ranking, final int count) {
    final List<RankingEntry> entries = ranking.entries();
    return entries.subList(Math.max(0, entries.size() - count), entries.size());
  }

}
