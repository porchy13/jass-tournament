package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The places per participant (§8 of the development plan): one row per team (or player), with its names and its
 * place in every round, a dash when it sits out the round.
 *
 * <p>The place of the giver side of table {@code k} is {@code 2k − 1}, the one of the receiver side {@code 2k}
 * (rule 6). A4 portrait pages, each with the logo, the title and the edition, numbered.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class PlacesDocument {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The width of the column of the numbers. */
  private static final float NUMBER = 12 * PdfCanvas.MM;
  /** The width of the column of a round. */
  private static final float ROUND = 18 * PdfCanvas.MM;
  /** The text of a round sat out. */
  private static final String NONE = "–";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private PlacesDocument() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Writes the places of the participants of an edition as a PDF document.
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
    final List<Integer> rounds = pairings.stream().map(Pairing::round).distinct().toList();
    final Texts texts = new Texts(options.locale());
    final Participants participants = new Participants(edition, texts);
    final String title = texts.get("places.title");
    final float[] widths = new float[2 + rounds.size()];
    widths[0] = NUMBER;
    final List<String> columns = new ArrayList<>(List.of(texts.get("column.number"), texts.get("column.names")));
    for (int i = 0; i < rounds.size(); i++) {
      widths[2 + i] = ROUND;
      columns.add(texts.get("round", rounds.get(i)));
    }
    try (PdfCanvas canvas = new PdfCanvas(options)) {
      final PagedTable table = new PagedTable(canvas, PdfCanvas.PORTRAIT, texts, title, edition, widths,
                                              columns);
      table.titles();
      for (int number = 1; number <= participants.count(); number++) {
        final List<String> cells = new ArrayList<>(List.of(Integer.toString(number),
                                                           participants.joinedNames(number)));
        for (final int round : rounds) {
          cells.add(Documents.place(pairings, round, number).map(String::valueOf).orElse(NONE));
        }
        table.row(cells);
      }
      table.finish();
      canvas.save(Documents.title(title, edition), out);
    }
  }

}
