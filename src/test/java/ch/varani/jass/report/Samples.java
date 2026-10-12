package ch.varani.jass.report;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TournamentMode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fictitious editions, options and helpers shared by the tests of the documents (no real participant,
 * {@code AGENTS.md} §5).
 */
final class Samples {

  /** The language of the documents of the tests, as in Switzerland. */
  static final Locale FRENCH = Locale.forLanguageTag("fr-CH");
  /** The options of the documents without logo, in French. */
  static final DocumentOptions OPTIONS = new DocumentOptions(FRENCH, null);
  /** The date of the fictitious editions. */
  static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** The line of the edition in the documents in French. */
  static final String EDITION_LINE = "Match aux cartes 2026 – 20 novembre 2026";
  /** The number of teams of the team edition. */
  static final int TEAMS = 5;
  /** The size of the generated logo, in pixels. */
  private static final int LOGO_SIZE = 40;
  /** The number of the third team. */
  private static final int THREE = 3;
  /** The number of the fourth team. */
  private static final int FOUR = 4;

  /**
   * Prevents the instantiation of this utility class.
   */
  private Samples() {
  }

  /**
   * Returns a team edition of five teams and two rounds; team 5 sits out the first round, team 1 the second.
   *
   * @return the edition.
   */
  static Edition teamEdition() {
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 1; i <= TEAMS; i++) {
      teams = teams.add(new Team(new Player("Anne" + i, "Fontaine"), new Player("Bruno" + i, "Rochat")));
    }
    return Edition.create("Match aux cartes 2026", DATE, TournamentMode.TEAM)
                  .withGames(1)
                  .withRegistrations(teams)
                  .withSchedule(new TeamSchedule(TEAMS, 1, List.of(
                      new TeamRound(1, List.of(new TeamGame(1, 1, 2), new TeamGame(2, THREE, FOUR))),
                      new TeamRound(2, List.of(new TeamGame(1, TEAMS, 2))))));
  }

  /**
   * Returns an individual edition of four players and one game: players 1 and 2 against players 3 and 4.
   *
   * @return the edition.
   */
  static Edition individualEdition() {
    final List<Player> players = new ArrayList<>();
    for (int i = 1; i <= FOUR; i++) {
      players.add(new Player("Zoé" + i, "Müller"));
    }
    return Edition.create("Match individuel", DATE, TournamentMode.INDIVIDUAL)
                  .withGames(1)
                  .withRegistrations(new IndividualRegistrations(players))
                  .withSchedule(new IndividualSchedule(FOUR, 1, List.of(new IndividualRound(1, List.of(
                      new IndividualGame(1, new Partners(1, 2), new Partners(THREE, FOUR)))))));
  }

  /**
   * Returns a small image, as a logo.
   *
   * @param format the format of the image: {@code png} or {@code jpg}.
   *
   * @return the content of the image.
   */
  static byte[] logo(final String format) {
    final BufferedImage image = new BufferedImage(LOGO_SIZE, LOGO_SIZE / 2, BufferedImage.TYPE_INT_RGB);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, format, out);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /**
   * Generates a document into memory.
   *
   * @param document writes the document.
   *
   * @return the bytes of the PDF.
   *
   * @throws IOException if the document cannot be written.
   */
  static byte[] pdf(final Generator document) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    document.write(out);
    return out.toByteArray();
  }

  /**
   * Extracts the text of a PDF document.
   *
   * @param pdf the bytes of the PDF.
   *
   * @return the text of all its pages.
   *
   * @throws IOException if the PDF cannot be read.
   */
  static String text(final byte[] pdf) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return stripper().getText(document);
    }
  }

  /**
   * Creates an extractor of the text of a PDF whose lines end with {@code \n} on every system, so that the expected
   * texts do not depend on the line separator of the system ({@code \r\n} on Windows).
   *
   * @return the extractor.
   */
  static PDFTextStripper stripper() {
    final PDFTextStripper stripper = new PDFTextStripper();
    stripper.setLineSeparator("\n");
    return stripper;
  }

  /**
   * Writes a document into a stream.
   */
  @FunctionalInterface
  interface Generator {

    /**
     * Writes the document.
     *
     * @param out the stream.
     *
     * @throws IOException if the document cannot be written.
     */
    void write(java.io.OutputStream out) throws IOException;

  }

}
