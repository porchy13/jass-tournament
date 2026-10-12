package ch.varani.jass.scoring;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.TieBreak;
import ch.varani.jass.domain.ValidationError;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * The scoring of an edition: the result of each game, the ranking and the drawing of lots, computed from the
 * results entered (rules 2, 14 to 17 and 19). Nothing computed here is stored.
 *
 * <p><b>Result of a game</b> ({@link #result(int, int)}): a side <i>forfeits</i> when it is marked absent on the score
 * sheet (rule 19) or when one of its participants is excluded for cheating (rule 17, in every game of the excluded
 * participant, played or not). If one side forfeits, it gets 0 points and the other side half of a game of the plan
 * ({@link ch.varani.jass.domain.DealPlan#forfeitPoints()}, 1'256 for the regulation); if both forfeit, both get 0.
 * Otherwise, the points come from the score sheet ({@link SheetTotals}, rule 16 correction included); a game without
 * sheet and without forfeit has no result yet.</p>
 *
 * <p><b>Ranking</b> ({@link #ranking()}, rule 14): every registered participant that is not excluded (rule 17: the
 * excluded participants are removed from the ranking) gets the points of its side in each of its games with a result;
 * the participants are sorted by total, the highest first. Participants with the same total share the same rank,
 * unless the drawing of lots stored in the results covers all of them: they are then ranked in the order of the draw
 * (rule 15). The participants of a shared rank are listed by number.</p>
 *
 * <p><b>Drawing of lots</b> ({@link #drawLots(long)}, rule 15): only once every game has a result. The groups of tied
 * participants are taken in the order of the ranking, the members of each group sorted by number, and each group is
 * shuffled ({@link Collections#shuffle(List, Random)}) with one {@link Random} generator created from the seed, whose
 * algorithm is specified by the JDK: the same seed and the same ties always give the same order. The seed comes from
 * {@link SecureRandom} and is recorded with the outcome.</p>
 *
 * <p>Immutable once created, safe for concurrent use.</p>
 */
public final class Scoring {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The source of the seeds of the drawings of lots (rule 15), created once; thread-safe. */
  private static final SecureRandom SEEDS = new SecureRandom();

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The edition. */
  private final Edition edition;
  /** The games of the schedule, empty while there is no schedule. */
  private final List<Pairing> pairings;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the scoring of an edition.
   *
   * @param edition the edition, with or without schedule.
   */
  public Scoring(final Edition edition) {
    this.edition = edition;
    this.pairings = edition.isScheduled() ? edition.schedule().pairings() : List.of();
  }

  // *******************************************************************************************************************
  // Games
  // *******************************************************************************************************************

  /**
   * Computes the totals of a score sheet of the edition.
   *
   * @param sheet the score sheet.
   *
   * @return its subtotals, totals and rule 16 correction.
   */
  public SheetTotals totals(final ScoreSheet sheet) {
    return SheetTotals.of(sheet, edition.deals());
  }

  /**
   * Returns the result of a game.
   *
   * @param round the number of the round.
   * @param table the number of the table.
   *
   * @return the result, or nothing if the game is not in the schedule or has no result yet.
   */
  public Optional<GameResult> result(final int round, final int table) {
    return pairings.stream()
                   .filter(pairing -> pairing.round() == round && pairing.table() == table)
                   .findFirst()
                   .flatMap(this::resultOf);
  }

  /**
   * Returns the results of the games that have one, in the order of the schedule.
   *
   * @return the results.
   */
  public List<GameResult> results() {
    return pairings.stream().map(this::resultOf).flatMap(Optional::stream).toList();
  }

  /**
   * Returns the points of a participant in a round.
   *
   * @param participant the number of the participant.
   * @param round the number of the round.
   *
   * @return the points of its side in its game of the round, or nothing if it sits out or the game has no result.
   */
  public Optional<Integer> points(final int participant, final int round) {
    return results().stream()
                    .filter(game -> game.pairing().round() == round && game.involves(participant))
                    .findFirst()
                    .map(game -> game.pointsOf(participant));
  }

  /**
   * Tells whether every game of the schedule has a result, i.e. the ranking is final.
   *
   * @return {@code true} if the edition is scheduled and every game has a result.
   */
  public boolean isComplete() {
    return !pairings.isEmpty() && results().size() == pairings.size();
  }

  /**
   * Computes the result of a game: forfeit, points of its score sheet, or nothing.
   *
   * @param pairing the game.
   *
   * @return the result, or nothing if the game has no sheet and no forfeit.
   */
  private Optional<GameResult> resultOf(final Pairing pairing) {
    final Results results = edition.results();
    final Optional<ScoreSheet> sheet = results.sheet(pairing.round(), pairing.table());
    final Absence absence = sheet.map(ScoreSheet::absence).orElse(Absence.NONE);
    final boolean giverOut = absence == Absence.GIVER || absence == Absence.BOTH
                             || pairing.giver().stream().anyMatch(results.excluded()::contains);
    final boolean receiverOut = absence == Absence.RECEIVER || absence == Absence.BOTH
                                || pairing.receiver().stream().anyMatch(results.excluded()::contains);
    if (giverOut || receiverOut) {
      final int forfeit = edition.deals().forfeitPoints();
      return Optional.of(new GameResult(pairing, giverOut ? 0 : forfeit, receiverOut ? 0 : forfeit, true));
    }
    return sheet.map(this::totals)
                .map(totals -> new GameResult(pairing, totals.giverPoints(), totals.receiverPoints(), false));
  }

  // *******************************************************************************************************************
  // Ranking
  // *******************************************************************************************************************

  /**
   * Computes the ranking, with the drawing of lots stored in the results.
   *
   * @return the ranking.
   */
  public Ranking ranking() {
    return rank(edition.results().tieBreak());
  }

  /**
   * Computes the ranking with a given drawing of lots.
   *
   * <p>Algorithm: the totals of the participants that are not excluded are computed; the participants are sorted by
   * total (highest first), then by number; each group of equal totals gets the rank following the previous groups;
   * a group of several participants all covered by the draw is then ordered by the draw, each member with its own
   * rank.</p>
   *
   * @param tieBreak the drawing of lots, or {@code null} to leave the ties unresolved.
   *
   * @return the ranking.
   */
  private Ranking rank(final TieBreak tieBreak) {
    final List<GameResult> results = results();
    final List<RankingEntry> totals = IntStream.rangeClosed(1, edition.registrations().size())
        .filter(participant -> !edition.results().excluded().contains(participant))
        .mapToObj(participant -> total(participant, results))
        .sorted(Comparator.comparingInt(RankingEntry::points).reversed()
                          .thenComparingInt(RankingEntry::participant))
        .toList();
    final List<RankingEntry> entries = new ArrayList<>();
    for (final List<RankingEntry> group : groups(totals)) {
      final int rank = entries.size() + 1;
      if (group.size() > 1 && tieBreak != null
          && group.stream().allMatch(entry -> tieBreak.positionOf(entry.participant()) >= 0)) {
        final List<RankingEntry> drawn = group.stream()
            .sorted(Comparator.comparingInt(entry -> tieBreak.positionOf(entry.participant())))
            .toList();
        for (int i = 0; i < drawn.size(); i++) {
          entries.add(withRank(drawn.get(i), rank + i));
        }
      } else {
        group.forEach(entry -> entries.add(withRank(entry, rank)));
      }
    }
    return new Ranking(entries, isComplete());
  }

  /**
   * Computes the total of a participant over its games with a result.
   *
   * @param participant the number of the participant.
   * @param results the results of the games.
   *
   * @return the line of the participant, without rank yet.
   */
  private static RankingEntry total(final int participant, final List<GameResult> results) {
    final List<GameResult> own = results.stream().filter(result -> result.involves(participant)).toList();
    return new RankingEntry(0, participant, own.stream().mapToInt(result -> result.pointsOf(participant)).sum(),
                            own.size());
  }

  /**
   * Splits the sorted lines into groups of equal totals, in their order.
   *
   * @param sorted the lines, sorted by total.
   *
   * @return the groups.
   */
  private static List<List<RankingEntry>> groups(final List<RankingEntry> sorted) {
    final List<List<RankingEntry>> groups = new ArrayList<>();
    for (final RankingEntry entry : sorted) {
      if (groups.isEmpty() || groups.getLast().getFirst().points() != entry.points()) {
        groups.add(new ArrayList<>());
      }
      groups.getLast().add(entry);
    }
    return groups;
  }

  /**
   * Returns a line with its rank.
   *
   * @param entry the line, without rank.
   * @param rank the rank.
   *
   * @return the line with the rank.
   */
  private static RankingEntry withRank(final RankingEntry entry, final int rank) {
    return new RankingEntry(rank, entry.participant(), entry.points(), entry.games());
  }

  // *******************************************************************************************************************
  // Drawing of lots
  // *******************************************************************************************************************

  /**
   * Rule 15: draws lots to break the ties of the final ranking, with a seed from a cryptographically strong
   * generator.
   *
   * @return the outcome of the draw, to store in the results ({@link Results#withTieBreak(TieBreak)}); its order is
   *         empty if there is no tie.
   *
   * @throws DomainException with {@link ValidationError#RESULTS_INCOMPLETE} if a game has no result yet.
   */
  public TieBreak drawLots() {
    return drawLots(SEEDS.nextLong());
  }

  /**
   * Rule 15: draws lots to break the ties of the final ranking, with a given seed (reproducible).
   *
   * @param seed the seed of the draw.
   *
   * @return the outcome of the draw; its order is empty if there is no tie.
   *
   * @throws DomainException with {@link ValidationError#RESULTS_INCOMPLETE} if a game has no result yet.
   */
  public TieBreak drawLots(final long seed) {
    if (!isComplete()) {
      throw new DomainException(ValidationError.RESULTS_INCOMPLETE);
    }
    final Random random = new Random(seed);
    final List<Integer> order = new ArrayList<>();
    for (final List<RankingEntry> group : groups(rank(null).entries())) {
      if (group.size() > 1) {
        final List<Integer> members = new ArrayList<>(group.stream().map(RankingEntry::participant).toList());
        Collections.shuffle(members, random);
        order.addAll(members);
      }
    }
    return new TieBreak(seed, order);
  }

}
