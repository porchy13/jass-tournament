package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The schedule per round (§8 of the development plan): for each round, one row per table with the places, the side
 * that deals first (rule 6) and the side that receives, then the participants sitting out the round (rule 1).
 *
 * <p>A4 landscape pages, each with the logo, the title and the edition (name and date), numbered. Each round starts a
 * page, so that the table of a round can be posted on its own. When the tables of the largest round do not fit on a
 * page, every round is split the same way: the pages hold the same tables in each round (e.g. tables 1 to 14, then
 * 15 to 28…), as many on each page as possible while keeping the parts of a round of even size; the heading of each
 * part names its tables, and the participants sitting out are listed under the last part.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class ScheduleDocument {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The width of the column of the tables. */
  private static final float TABLE = 16 * PdfCanvas.MM;
  /** The width of the column of the places. */
  private static final float PLACES = 18 * PdfCanvas.MM;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private ScheduleDocument() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Writes the schedule of an edition as a PDF document.
   *
   * @param edition the edition, with its schedule.
   * @param options the options of the documents.
   * @param out the stream to which the document is written, not closed.
   *
   * @throws IllegalStateException if the edition has no schedule.
   * @throws IOException if the document cannot be written.
   */
  public static void write(final Edition edition, final DocumentOptions options, final OutputStream out)
      throws IOException {
    final List<Pairing> pairings = Documents.pairings(edition);
    final Texts texts = new Texts(options.locale());
    final Participants participants = new Participants(edition, texts);
    final String title = texts.get("schedule.title");
    final Map<Integer, List<Pairing>> rounds = pairings.stream().collect(
        Collectors.groupingBy(Pairing::round, TreeMap::new, Collectors.toList()));
    try (PdfCanvas canvas = new PdfCanvas(options)) {
      final PagedTable table = new PagedTable(canvas, PdfCanvas.LANDSCAPE, texts, title, edition,
                                              new float[] {TABLE, PLACES, 0, 0},
                                              List.of(texts.get("column.table"), texts.get("column.places"),
                                                      texts.get("column.giver"), texts.get("column.receiver")));
      final int most = rounds.values().stream().mapToInt(List::size).max().orElse(0);
      final int parts = Math.max(1, Math.ceilDiv(most, table.capacity(1)));
      final int perPage = Math.max(1, Math.ceilDiv(most, parts));
      boolean first = true;
      for (final Map.Entry<Integer, List<Pairing>> round : rounds.entrySet()) {
        final List<Pairing> games = round.getValue();
        for (int from = 0; from < games.size(); from += perPage) {
          if (!first) {
            table.newPage();
          }
          first = false;
          final List<Pairing> part = games.subList(from, Math.min(games.size(), from + perPage));
          table.section(parts == 1 ? texts.get("round", round.getKey())
                                   : texts.get("round.tables", round.getKey(), part.getFirst().table(),
                                               part.getLast().table()));
          for (final Pairing game : part) {
            table.row(List.of(Integer.toString(game.table()), (2 * game.table() - 1) + " – " + 2 * game.table(),
                              participants.sideLine(game.giver()), participants.sideLine(game.receiver())));
          }
        }
        final String resting = sittingOut(participants, games);
        if (!resting.isEmpty()) {
          table.note(texts.get("sitting.out", resting));
        }
      }
      table.finish();
      canvas.save(Documents.title(title, edition), out);
    }
  }

  /**
   * Lists the participants that do not play in a round.
   *
   * @param participants the participants of the edition.
   * @param games the games of the round.
   *
   * @return their labels, separated by commas, or an empty text if every participant plays.
   */
  private static String sittingOut(final Participants participants, final List<Pairing> games) {
    return IntStream.rangeClosed(1, participants.count())
                    .filter(number -> games.stream().noneMatch(game -> game.giver().contains(number)
                                                                       || game.receiver().contains(number)))
                    .mapToObj(participants::label)
                    .collect(Collectors.joining(", "));
  }

}
