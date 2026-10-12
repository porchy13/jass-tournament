package ch.varani.jass.persistence;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Registrations;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.Schedule;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TieBreak;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes an edition file ({@code .jass}): a ZIP archive whose first entry, {@code edition.xml}, holds the edition in
 * XML (format version {@link EditionXml#FORMAT_VERSION}, schema {@code edition.xsd}), followed by the supporting
 * documents of the accounting.
 *
 * <p>The sequence is: start of the ZIP entry {@code edition.xml} (deflated), then the XML document, encoded in UTF-8
 * and indented, in the order of the schema: the root element with the format version and the identifier of the file,
 * the settings (name, date, mode, games: the header read alone by
 * {@link EditionReader#readHeader(java.io.InputStream)}), the plan of the deals, the registrations (teams or players),
 * the schedule when there is one, the results once one is entered, and the accounting once a fee or an entry is
 * entered; then end of the entry, the supporting documents as entries {@code attachments/<sha256>.<extension>}
 * without compression, and end of the archive.</p>
 *
 * <p>The writer writes to a stream; writing a file atomically is the job of {@link EditionStore}. The text values
 * are always valid XML characters, as the domain rejects the other ones.</p>
 *
 * <p>Instances are confined to one writing, from one thread; the static methods are safe for concurrent use.</p>
 */
public final class EditionWriter {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The depth of the sections: settings, deals, registrations, schedule. */
  private static final int SECTION = 1;
  /** The depth of the items of a section: a setting, a deal, a team, a player, a round. */
  private static final int ITEM = 2;
  /** The depth of the details of an item: a player of a team, a game of a round. */
  private static final int DETAIL = 3;
  /** The depth of the teams of a game of the individual mode. */
  private static final int PARTNERS = 4;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The writer of the indented document. */
  private final XmlOutput xml;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a writer of one document.
   *
   * @param xml the writer of the indented document.
   */
  private EditionWriter(final XmlOutput xml) {
    this.xml = xml;
  }

  // *******************************************************************************************************************
  // Writing
  // *******************************************************************************************************************

  /**
   * Writes an edition file without supporting document into a stream. The stream is not closed.
   *
   * @param stored the edition and the identifier of its file.
   * @param out the stream to which the archive is written.
   *
   * @throws NoSuchFileException if the accounting of the edition refers to a supporting document.
   * @throws IOException if the stream cannot be written.
   */
  public static void write(final StoredEdition stored, final OutputStream out) throws IOException {
    write(stored, out, stored.edition().ledger().attachments().stream().map(Attachment::fileName).toList(),
          (name, target) -> {
            throw new NoSuchFileException(name);
          });
  }

  /**
   * Writes an edition file into a stream, with supporting documents. The stream is not closed.
   *
   * <p>After {@code edition.xml} (deflated), each document is an entry {@code attachments/<name>}, in the order of the
   * names, written without compression (PDF, PNG and JPEG are already compressed).</p>
   *
   * @param stored the edition and the identifier of its file.
   * @param out the stream to which the archive is written.
   * @param attachments the names of the documents to write ({@link Attachment#fileName()}), usually those of the
   *                    accounting of the edition; a name given twice is written once.
   * @param contents copies the content of a document.
   *
   * @throws IOException if the stream cannot be written, or if the content of a document cannot be found.
   */
  public static void write(final StoredEdition stored, final OutputStream out, final Collection<String> attachments,
                           final Contents contents) throws IOException {
    final ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
    zip.putNextEntry(new ZipEntry(EditionXml.ENTRY));
    writeXml(stored, zip);
    zip.closeEntry();
    zip.setLevel(Deflater.NO_COMPRESSION);
    for (final String name : new TreeSet<>(attachments)) {
      zip.putNextEntry(new ZipEntry(EditionXml.ATTACHMENTS + name));
      contents.copy(name, zip);
      zip.closeEntry();
    }
    zip.finish();
  }

  /**
   * Writes the XML document {@code edition.xml} into a stream, which is not closed.
   *
   * @param stored the edition and the identifier of its file.
   * @param out the stream to which the document is written.
   *
   * @throws IOException if the stream cannot be written.
   */
  static void writeXml(final StoredEdition stored, final OutputStream out) throws IOException {
    try {
      final XmlOutput xml = XmlOutput.start(out, EditionXml.NAMESPACE, EditionXml.EDITION);
      new EditionWriter(xml).writeDocument(stored);
      xml.end();
    } catch (final XMLStreamException e) {
      throw new IOException("The edition cannot be written.", e);
    }
  }

  /**
   * Writes the content of the root element: the format version and the identifier, the settings, the plan of the
   * deals, the registrations, the schedule, the results and the accounting.
   *
   * @param stored the edition and the identifier of its file.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeDocument(final StoredEdition stored) throws XMLStreamException {
    final Edition edition = stored.edition();
    xml.attribute(EditionXml.FORMAT_VERSION_ATTRIBUTE, EditionXml.FORMAT_VERSION);
    xml.attribute(EditionXml.ID, stored.id().toString());
    writeSettings(edition);
    writeDeals(edition.deals());
    writeRegistrations(edition.registrations());
    if (edition.schedule() != null) {
      writeSchedule(edition.schedule());
    }
    if (edition.hasResults()) {
      writeResults(edition.results());
    }
    if (!edition.ledger().isEmpty()) {
      writeLedger(edition.ledger());
    }
  }

  /**
   * Writes the settings of the edition: name, date (ISO 8601), mode, number of games and time of a game.
   *
   * @param edition the edition.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeSettings(final Edition edition) throws XMLStreamException {
    xml.open(SECTION, EditionXml.SETTINGS);
    xml.text(ITEM, EditionXml.NAME, edition.name());
    xml.text(ITEM, EditionXml.DATE, edition.date().toString());
    xml.text(ITEM, EditionXml.MODE, edition.mode().name());
    xml.text(ITEM, EditionXml.GAMES, Integer.toString(edition.games()));
    xml.text(ITEM, EditionXml.GAME_MINUTES, Integer.toString(edition.gameMinutes()));
    xml.close(SECTION);
  }

  /**
   * Writes the plan of the deals: the type of each deal, in their order.
   *
   * @param deals the plan of the deals of the edition.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeDeals(final DealPlan deals) throws XMLStreamException {
    xml.open(SECTION, EditionXml.DEALS);
    for (final DealType deal : deals.deals()) {
      xml.empty(ITEM, EditionXml.DEAL);
      xml.attribute(EditionXml.TYPE, deal.name());
    }
    xml.close(SECTION);
  }

  /**
   * Writes the registrations, in their order: the teams and their two players, or the players.
   *
   * @param registrations the registrations of the edition.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeRegistrations(final Registrations registrations) throws XMLStreamException {
    xml.open(SECTION, EditionXml.REGISTRATIONS);
    switch (registrations) {
      case TeamRegistrations(final List<Team> teams) -> {
        for (final Team team : teams) {
          xml.open(ITEM, EditionXml.TEAM);
          for (final Player player : team.players()) {
            writePlayer(DETAIL, player);
          }
          xml.close(ITEM);
        }
      }
      case IndividualRegistrations(final List<Player> players) -> {
        for (final Player player : players) {
          writePlayer(ITEM, player);
        }
      }
    }
    xml.close(SECTION);
  }

  /**
   * Writes a player as an empty element with its first and last names, the identifier of its person of the directory
   * when it is linked to one, and whether its registration fee is paid when it is.
   *
   * @param depth the depth of the element, for the indentation.
   * @param player the player.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writePlayer(final int depth, final Player player) throws XMLStreamException {
    xml.empty(depth, EditionXml.PLAYER);
    xml.attribute(EditionXml.FIRST_NAME, player.firstName());
    xml.attribute(EditionXml.LAST_NAME, player.lastName());
    if (player.personId() != null) {
      xml.attribute(EditionXml.PERSON, player.personId().toString());
    }
    if (player.paid()) {
      xml.attribute(EditionXml.PAID, Boolean.TRUE.toString());
    }
  }

  /**
   * Writes the schedule: its rounds and their games, of the team or of the individual mode.
   *
   * @param schedule the schedule of the edition.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeSchedule(final Schedule schedule) throws XMLStreamException {
    xml.open(SECTION, EditionXml.SCHEDULE);
    switch (schedule) {
      case final TeamSchedule teams -> {
        for (final TeamRound round : teams.rounds()) {
          openRound(round.number());
          for (final TeamGame game : round.games()) {
            writeTeamGame(game);
          }
          xml.close(ITEM);
        }
      }
      case final IndividualSchedule players -> {
        for (final IndividualRound round : players.rounds()) {
          openRound(round.number());
          for (final IndividualGame game : round.games()) {
            writeIndividualGame(game);
          }
          xml.close(ITEM);
        }
      }
    }
    xml.close(SECTION);
  }

  /**
   * Writes the results: the score sheets with the typed values only (absent attributes for the values not typed, and
   * the absence only when a side is absent), the excluded participants and the drawing of lots.
   *
   * @param results the results of the edition.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeResults(final Results results) throws XMLStreamException {
    xml.open(SECTION, EditionXml.RESULTS);
    for (final ScoreSheet sheet : results.sheets()) {
      xml.open(ITEM, EditionXml.SHEET);
      xml.attribute(EditionXml.ROUND, Integer.toString(sheet.round()));
      xml.attribute(EditionXml.TABLE, Integer.toString(sheet.table()));
      if (sheet.absence() != Absence.NONE) {
        xml.attribute(EditionXml.ABSENCE, sheet.absence().name());
      }
      for (final DealScore score : sheet.deals()) {
        xml.empty(DETAIL, EditionXml.SCORE);
        writePoints(EditionXml.GIVER, score.giver());
        writePoints(EditionXml.RECEIVER, score.receiver());
      }
      xml.close(ITEM);
    }
    for (final int participant : results.excluded()) {
      xml.empty(ITEM, EditionXml.EXCLUDED);
      xml.attribute(EditionXml.NUMBER, Integer.toString(participant));
    }
    final TieBreak tieBreak = results.tieBreak();
    if (tieBreak != null) {
      xml.open(ITEM, EditionXml.TIE_BREAK);
      xml.attribute(EditionXml.SEED, Long.toString(tieBreak.seed()));
      for (final int participant : tieBreak.order()) {
        xml.empty(DETAIL, EditionXml.LOT);
        xml.attribute(EditionXml.NUMBER, Integer.toString(participant));
      }
      xml.close(ITEM);
    }
    xml.close(SECTION);
  }

  /**
   * Writes the accounting: the registration fee, and the entries with the references of their supporting documents
   * (the description only when it is not empty).
   *
   * @param ledger the accounting of the edition.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeLedger(final Ledger ledger) throws XMLStreamException {
    xml.open(SECTION, EditionXml.LEDGER);
    xml.attribute(EditionXml.FEE, ledger.fee().value().toPlainString());
    for (final LedgerEntry entry : ledger.entries()) {
      xml.open(ITEM, EditionXml.ENTRY_ELEMENT);
      xml.attribute(EditionXml.ID, entry.id().toString());
      xml.attribute(EditionXml.DATE, entry.date().toString());
      xml.attribute(EditionXml.TYPE, entry.type().name());
      xml.attribute(EditionXml.CATEGORY, entry.category());
      if (!entry.description().isEmpty()) {
        xml.attribute(EditionXml.DESCRIPTION, entry.description());
      }
      xml.attribute(EditionXml.AMOUNT, entry.amount().value().toPlainString());
      for (final Attachment attachment : entry.attachments()) {
        xml.empty(DETAIL, EditionXml.ATTACHMENT);
        xml.attribute(EditionXml.SHA256, attachment.sha256());
        xml.attribute(EditionXml.TYPE, attachment.type().name());
        xml.attribute(EditionXml.NAME, attachment.name());
        xml.attribute(EditionXml.SIZE, Long.toString(attachment.size()));
      }
      xml.close(ITEM);
    }
    xml.close(SECTION);
  }

  /**
   * Writes the points typed in a column of a deal, if any.
   *
   * @param name the name of the attribute: giver or receiver.
   * @param points the points typed, or {@code null} if not typed.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writePoints(final String name, final Integer points) throws XMLStreamException {
    if (points != null) {
      xml.attribute(name, points.toString());
    }
  }

  /**
   * Opens the element of a round.
   *
   * @param number the number of the round.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void openRound(final int number) throws XMLStreamException {
    xml.open(ITEM, EditionXml.ROUND);
    xml.attribute(EditionXml.NUMBER, Integer.toString(number));
  }

  /**
   * Writes a game of the team mode as an empty element with its table, giver and receiver.
   *
   * @param game the game.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeTeamGame(final TeamGame game) throws XMLStreamException {
    xml.empty(DETAIL, EditionXml.TEAM_GAME);
    xml.attribute(EditionXml.TABLE, Integer.toString(game.table()));
    xml.attribute(EditionXml.GIVER, Integer.toString(game.giver()));
    xml.attribute(EditionXml.RECEIVER, Integer.toString(game.receiver()));
  }

  /**
   * Writes a game of the individual mode: its table, and its giver and receiver teams as empty elements.
   *
   * @param game the game.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writeIndividualGame(final IndividualGame game) throws XMLStreamException {
    xml.open(DETAIL, EditionXml.INDIVIDUAL_GAME);
    xml.attribute(EditionXml.TABLE, Integer.toString(game.table()));
    writePartners(EditionXml.GIVER, game.giver());
    writePartners(EditionXml.RECEIVER, game.receiver());
    xml.close(DETAIL);
  }

  /**
   * Writes a team of the individual mode as an empty element with the numbers of its two players.
   *
   * @param name the name of the element: giver or receiver.
   * @param partners the two players.
   *
   * @throws XMLStreamException if the document cannot be written.
   */
  private void writePartners(final String name, final Partners partners) throws XMLStreamException {
    xml.empty(PARTNERS, name);
    xml.attribute(EditionXml.FIRST, Integer.toString(partners.first()));
    xml.attribute(EditionXml.SECOND, Integer.toString(partners.second()));
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * Copies the content of a supporting document into the archive being written.
   */
  @FunctionalInterface
  public interface Contents {

    /**
     * Copies the content of a document into a stream, which is not closed.
     *
     * @param name the name of the document ({@link Attachment#fileName()}).
     * @param out the stream of the entry of the archive.
     *
     * @throws IOException if the content cannot be found or copied.
     */
    void copy(String name, OutputStream out) throws IOException;

  }

}
