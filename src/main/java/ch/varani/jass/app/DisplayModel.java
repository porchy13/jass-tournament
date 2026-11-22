package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.scoring.Ranking;
import ch.varani.jass.scoring.RankingEntry;
import ch.varani.jass.scoring.Scoring;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * The content of the external display (F18), without the toolkit: the rounds and the tables of a round with the places
 * of their participants, the countdown of a game, the pages that alternate when the lines do not fit, and the lines of
 * the final ranking revealed so far.
 *
 * <p><b>Tables</b>: one per game of the round, in the order of the tables; at table {@code k}, the giver side sits at
 * place {@code 2k − 1} and the receiver side at place {@code 2k} (rule 6), the giver first; in individual mode each
 * player has the place of its side. The participants without game in the round sit out (table and place 0), by
 * number, all together.</p>
 *
 * <p><b>Rows</b>: the tables are shown by rows of as many tables as fit in the width; the participants sitting out
 * come after, in rows of their own across the whole width, each holding as many of them as fit in its columns and in
 * the height of the window. The rows are packed into pages by their heights: a row goes to the next page when it no
 * longer fits.</p>
 *
 * <p><b>Countdown</b>: before the start signal, the whole time of a game remains; afterwards, the time of a game minus
 * the time elapsed since the signal, never below zero.</p>
 *
 * <p><b>Pages</b> (Q22): when the lines do not fit, they are cut into pages of equal size (the last one shorter), and
 * the page shown changes every {@value #PAGE_SECONDS} seconds from an origin (the epoch for the places of a round, the
 * start of the rotation for the final ranking), computed from the clock so that every refresh shows the same page
 * during these seconds. The part of the page left before the next one, shown by a bar that empties, is computed from
 * the same clock and origin, in milliseconds, so that the bar and the change of page always match.</p>
 *
 * <p><b>Final ranking</b>: revealed from the last line to the first; the lines revealed are shown in the order of the
 * ranking, the best first. While they are being revealed, the pages do not alternate: the first page stays, with the
 * line revealed last at its top; the rotation of the pages starts when the user asks for it, once everything is
 * revealed.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class DisplayModel {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** How long a page of lines stays shown before the next one, in seconds. */
  static final long PAGE_SECONDS = 10;
  /** How long a page of lines stays shown before the next one, in milliseconds. */
  private static final long PAGE_MILLIS = PAGE_SECONDS * 1000;
  /** The seconds of a minute. */
  private static final long MINUTE = 60;

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The place of a participant at a table.
   *
   * @param participant the number of the team, or of the player in individual mode.
   * @param place its place, 0 when it sits out.
   */
  record Seat(int participant, int place) {
  }

  /**
   * A table of a round with its participants, or a group of participants sitting out.
   *
   * @param table the number of the table, 0 for participants sitting out.
   * @param seats the participants, by place.
   */
  record Table(int table, List<Seat> seats) {

    /**
     * Creates a table, copying its seats.
     *
     * @param table the number of the table.
     * @param seats the participants.
     */
    Table {
      seats = List.copyOf(seats);
    }

  }

  /**
   * A page of lines.
   *
   * @param <T> the type of the lines.
   * @param lines the lines of the page.
   * @param number the number of the page, from 1.
   * @param count the number of pages.
   * @param turning whether the pages alternate: there are several and they are not held on the first one.
   */
  record Page<T>(List<T> lines, int number, int count, boolean turning) {

    /**
     * Creates a page, copying its lines.
     *
     * @param lines the lines of the page.
     * @param number the number of the page.
     * @param count the number of pages.
     * @param turning whether the pages alternate.
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
   * Returns the tables of a round with their participants, then the participants sitting out, all together.
   *
   * @param edition the scheduled edition.
   * @param round the number of a round of its schedule.
   *
   * @return the tables, in their order, then the participants sitting out (table 0), if any.
   */
  static List<Table> tables(final Edition edition, final int round) {
    final List<Pairing> games = edition.schedule().pairings().stream().filter(game -> game.round() == round)
                                       .sorted(Comparator.comparingInt(Pairing::table)).toList();
    final List<Table> tables = new ArrayList<>();
    final Set<Integer> playing = new HashSet<>();
    for (final Pairing game : games) {
      final List<Seat> seats = new ArrayList<>();
      game.giver().forEach(participant -> seats.add(new Seat(participant, 2 * game.table() - 1)));
      game.receiver().forEach(participant -> seats.add(new Seat(participant, 2 * game.table())));
      seats.forEach(seat -> playing.add(seat.participant()));
      tables.add(new Table(game.table(), seats));
    }
    final List<Seat> out = new ArrayList<>();
    for (int participant = 1; participant <= edition.registrations().size(); participant++) {
      if (!playing.contains(participant)) {
        out.add(new Seat(participant, 0));
      }
    }
    if (!out.isEmpty()) {
      tables.add(new Table(0, out));
    }
    return tables;
  }

  /**
   * Arranges the tables in rows of the display: the tables of the round by as many as fit in a row, then the
   * participants sitting out in rows of their own, each holding at most {@code columns × lines} of them.
   *
   * @param tables the tables of a round, then the participants sitting out ({@link #tables}).
   * @param columns the number of tables that fit in a row, at least 1.
   * @param lines the number of participants sitting out that fit in a column of their row, at least 1.
   *
   * @return the rows, in their order; a row of participants sitting out holds a single table 0.
   */
  static List<List<Table>> rows(final List<Table> tables, final int columns, final int lines) {
    final List<Table> playing = tables.stream().filter(table -> table.table() != 0).toList();
    final List<List<Table>> rows = new ArrayList<>();
    for (int from = 0; from < playing.size(); from += columns) {
      rows.add(playing.subList(from, Math.min(playing.size(), from + columns)));
    }
    final int size = columns * lines;
    for (final Table out : tables.stream().filter(table -> table.table() == 0).toList()) {
      final List<Seat> seats = out.seats();
      for (int from = 0; from < seats.size(); from += size) {
        rows.add(List.of(new Table(0, seats.subList(from, Math.min(seats.size(), from + size)))));
      }
    }
    return rows;
  }

  /**
   * Packs rows into pages by their heights: the rows go on the current page while they fit, separated by a gap; a row
   * that no longer fits starts the next page, and a row higher than the whole height takes a page of its own.
   *
   * @param <T> the type of the rows.
   * @param rows the rows, in their order.
   * @param height the height of a row.
   * @param available the height of a page.
   * @param gap the space between two rows.
   *
   * @return the pages, each with its rows in their order; none without row.
   */
  static <T> List<List<T>> pages(final List<T> rows, final ToDoubleFunction<T> height, final double available,
                                 final double gap) {
    final List<List<T>> pages = new ArrayList<>();
    final List<T> page = new ArrayList<>();
    double used = 0;
    for (final T row : rows) {
      final double needed = height.applyAsDouble(row);
      if (!page.isEmpty() && used + gap + needed > available) {
        pages.add(List.copyOf(page));
        page.clear();
      }
      used = page.isEmpty() ? needed : used + gap + needed;
      page.add(row);
    }
    if (!page.isEmpty()) {
      pages.add(List.copyOf(page));
    }
    return pages;
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
   * Returns the page of lines shown at an instant, the pages alternating from an origin.
   *
   * @param <T> the type of the lines.
   * @param lines all the lines.
   * @param size the number of lines that fit, at least 1.
   * @param origin the instant when the first page was shown first.
   * @param now the current instant.
   *
   * @return the page: all the lines when they fit, else the page of the moment.
   */
  static <T> Page<T> page(final List<T> lines, final int size, final Instant origin, final Instant now) {
    if (lines.size() <= size) {
      return new Page<>(lines, 1, 1, false);
    }
    final int count = pages(lines, size);
    final int index = Math.floorMod(Math.floorDiv(elapsed(origin, now), PAGE_MILLIS), count);
    return new Page<>(lines.subList(index * size, Math.min(lines.size(), (index + 1) * size)), index + 1, count,
                      true);
  }

  /**
   * Returns the first page of lines, held: the pages do not alternate.
   *
   * @param <T> the type of the lines.
   * @param lines all the lines.
   * @param size the number of lines that fit, at least 1.
   *
   * @return the first page, with the number of pages.
   */
  static <T> Page<T> firstPage(final List<T> lines, final int size) {
    return new Page<>(lines.subList(0, Math.min(lines.size(), size)), 1, Math.max(1, pages(lines, size)), false);
  }

  /**
   * Returns the part of the page shown left before the next page.
   *
   * @param origin the instant when the first page was shown first.
   * @param now the current instant.
   *
   * @return the part left, 1 right after a change of page, close to 0 just before the next one.
   */
  static double left(final Instant origin, final Instant now) {
    return 1 - (double) Math.floorMod(elapsed(origin, now), PAGE_MILLIS) / PAGE_MILLIS;
  }

  /**
   * Counts the pages needed by lines.
   *
   * @param lines the lines.
   * @param size the number of lines that fit, at least 1.
   *
   * @return the number of pages, rounded up, 0 without line.
   */
  private static int pages(final List<?> lines, final int size) {
    return (lines.size() + size - 1) / size;
  }

  /**
   * Returns the time elapsed from an origin.
   *
   * @param origin the origin.
   * @param now the current instant.
   *
   * @return the time, in milliseconds.
   */
  private static long elapsed(final Instant origin, final Instant now) {
    return Duration.between(origin, now).toMillis();
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
