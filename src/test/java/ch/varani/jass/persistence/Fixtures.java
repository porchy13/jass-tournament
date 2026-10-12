package ch.varani.jass.persistence;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.EmailAddress;
import ch.varani.jass.directory.Participation;
import ch.varani.jass.directory.Person;
import ch.varani.jass.directory.PhoneNumber;
import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.AttachmentType;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.EntryType;
import ch.varani.jass.domain.IndividualGame;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.IndividualRound;
import ch.varani.jass.domain.IndividualSchedule;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Money;
import ch.varani.jass.domain.Partners;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TieBreak;
import ch.varani.jass.domain.TournamentMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Fictitious editions, directories and their files shared by the persistence tests (no real participant,
 * {@code AGENTS.md} §5).
 */
final class Fixtures {

  /** The identifier of the fictitious edition files. */
  static final UUID ID = UUID.fromString("0b6c3f1e-6a3d-4c2b-9d7e-2f1a5b8c9d01");
  /** The date of the fictitious editions. */
  static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** The number of teams and of players of the fictitious editions. */
  static final int PARTICIPANTS = 5;
  /** The number of the third participant. */
  private static final int THIRD = 3;
  /** The number of the fourth participant. */
  private static final int FOURTH = 4;
  /** The number of the fifth participant, the last one. */
  private static final int FIFTH = PARTICIPANTS;
  /** The content of a fictitious PDF document. */
  static final byte[] PDF = "%PDF-1.7 fictitious invoice".getBytes(StandardCharsets.US_ASCII);
  /** The content of a fictitious JPEG image. */
  static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 1};
  /** Points typed in a deal of a fictitious score sheet. */
  static final int POINTS = 100;
  /** The seed of the fictitious drawing of lots, negative to test the sign. */
  static final long SEED = -7_000_000_000L;

  /**
   * Prevents the instantiation of this utility class.
   */
  private Fixtures() {
  }

  /**
   * Returns five fictitious players, with accents, an empty last name, symbols and an emoji; the first one is linked
   * to a person of the directory.
   *
   * @param prefix a prefix of the first names, to create different persons.
   *
   * @return the players.
   */
  static List<Player> players(final String prefix) {
    final UUID person = UUID.nameUUIDFromBytes(prefix.getBytes(StandardCharsets.UTF_8));
    return List.of(new Player(prefix + "Zoé", "Müller", person), new Player(prefix + "Anne", "Fontaine & Fils <SA>"),
                   new Player(prefix + "Jean-Claude", ""), new Player(prefix + "Ève", "d'Été \"♥\""),
                   new Player(prefix + "Léo", "🃏"));
  }

  /**
   * Returns a team edition with five teams, without schedule.
   *
   * @return the edition.
   */
  static Edition teamEdition() {
    final List<Player> first = players("A");
    final List<Player> second = players("B");
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 0; i < PARTICIPANTS; i++) {
      teams = teams.add(new Team(first.get(i), second.get(i)));
    }
    return Edition.create("Match aux cartes 2026 « hiver »", DATE, TournamentMode.TEAM).withRegistrations(teams);
  }

  /**
   * Returns a team edition with five teams and a schedule of two rounds.
   *
   * @return the edition.
   */
  static Edition scheduledTeamEdition() {
    final Edition edition = teamEdition();
    return edition.withSchedule(new TeamSchedule(PARTICIPANTS, edition.games(), List.of(
        new TeamRound(1, List.of(new TeamGame(1, 1, 2), new TeamGame(2, THIRD, FOURTH))),
        new TeamRound(2, List.of(new TeamGame(1, FIFTH, 1))))));
  }

  /**
   * Returns a team edition with a schedule and results: a sheet with typed, derived, manual and unplayed deals, a
   * sheet of an absent side, an excluded team and a drawing of lots with a negative seed.
   *
   * @return the edition.
   */
  static Edition scoredTeamEdition() {
    final ScoreSheet played = ScoreSheet.empty(1, 1, DealPlan.REGULATION)
                                        .withDeal(1, DealScore.of(POINTS))
                                        .withDeal(2, new DealScore(POINTS, POINTS))
                                        .withDeal(THIRD, new DealScore(null, 0));
    final ScoreSheet absent = ScoreSheet.empty(1, 2, DealPlan.REGULATION).withAbsence(Absence.RECEIVER);
    return scheduledTeamEdition().withResults(Results.EMPTY.withSheet(played).withSheet(absent)
                                                           .withExclusion(FIFTH, true)
                                                           .withTieBreak(new TieBreak(SEED, List.of(2, THIRD))));
  }

  /**
   * Returns a reference to a fictitious document.
   *
   * @param content the content of the document.
   * @param type the kind of document.
   * @param name the name of the original file.
   *
   * @return the reference.
   */
  static Attachment attachment(final byte[] content, final AttachmentType type, final String name) {
    return new Attachment(AttachmentFiles.sha256(content), type, name, content.length);
  }

  /**
   * Returns a team edition with an accounting: a fee, a paid player, an income without description and an expense
   * with two documents (a PDF and a JPEG).
   *
   * @return the edition.
   */
  static Edition ledgerEdition() {
    final Edition edition = teamEdition();
    final Team first = edition.teamRegistrations().get(1);
    final LedgerEntry income = new LedgerEntry(fixedId("income"), DATE, EntryType.INCOME, "Sponsoring", "",
                                               Money.of("200"), List.of());
    final LedgerEntry expense = new LedgerEntry(fixedId("expense"), DATE, EntryType.EXPENSE, "Prix",
                                                "Vin « réserve »", Money.of("120.50"),
                                                List.of(attachment(PDF, AttachmentType.PDF, "facture.pdf"),
                                                        attachment(JPEG, AttachmentType.JPEG, "ticket.jpg")));
    return edition.withRegistrations(edition.teamRegistrations().replace(
                      1, new Team(first.players().get(0).withPaid(true), first.players().get(1))))
                  .withLedger(Ledger.EMPTY.withFee(Money.of("30")).add(income).add(expense));
  }

  /**
   * Returns an edition file with the contents of its documents.
   *
   * @param edition the edition, whose documents are {@link #PDF} or {@link #JPEG}.
   *
   * @return the bytes of the archive.
   */
  static byte[] fileWithAttachments(final Edition edition) {
    final Map<String, byte[]> contents = Map.of(attachment(PDF, AttachmentType.PDF, "x").fileName(), PDF,
                                                attachment(JPEG, AttachmentType.JPEG, "x").fileName(), JPEG);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      EditionWriter.write(new StoredEdition(ID, edition), out,
                          edition.ledger().attachments().stream().map(Attachment::fileName).toList(),
                          (name, entry) -> entry.write(contents.get(name)));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /**
   * Returns an individual edition with five players, without schedule.
   *
   * @return the edition.
   */
  static Edition individualEdition() {
    return Edition.create("Match individuel", DATE.plusDays(1), TournamentMode.INDIVIDUAL)
                  .withRegistrations(new IndividualRegistrations(players("")));
  }

  /**
   * Returns an individual edition with five players and a schedule of two rounds.
   *
   * @return the edition.
   */
  static Edition scheduledIndividualEdition() {
    final Edition edition = individualEdition();
    return edition.withSchedule(new IndividualSchedule(PARTICIPANTS, edition.games(), List.of(
        new IndividualRound(1, List.of(new IndividualGame(1, new Partners(1, 2), new Partners(THIRD, FOURTH)))),
        new IndividualRound(2, List.of(new IndividualGame(1, new Partners(FIFTH, 1), new Partners(2, THIRD)))))));
  }

  /**
   * Returns the document {@code edition.xml} of an edition, as written by {@link EditionWriter}.
   *
   * @param edition the edition.
   *
   * @return the XML document.
   */
  static String xml(final Edition edition) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      EditionWriter.writeXml(new StoredEdition(ID, edition), out);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  /**
   * Returns an edition file written by {@link EditionWriter}.
   *
   * @param edition the edition.
   *
   * @return the bytes of the archive.
   */
  static byte[] file(final Edition edition) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      EditionWriter.write(new StoredEdition(ID, edition), out);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /**
   * Returns an archive holding a document as {@code edition.xml}.
   *
   * @param xml the document.
   *
   * @return the bytes of the archive.
   */
  static byte[] archive(final String xml) {
    return archive(Map.of(EditionXml.ENTRY, xml.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Returns an archive holding the given entries, {@code edition.xml} first when present.
   *
   * @param entries the names and contents of the entries.
   *
   * @return the bytes of the archive.
   */
  static byte[] archive(final Map<String, byte[]> entries) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      if (entries.containsKey(EditionXml.ENTRY)) {
        zip.putNextEntry(new ZipEntry(EditionXml.ENTRY));
        zip.write(entries.get(EditionXml.ENTRY));
      }
      for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
        if (!entry.getKey().equals(EditionXml.ENTRY)) {
          zip.putNextEntry(new ZipEntry(entry.getKey()));
          zip.write(entry.getValue());
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /**
   * Returns a directory of three fictitious persons: one with every value (contact details, notes with XML special
   * characters, participations with and without team mate), one deceased, one with the minimum.
   *
   * @return the directory.
   */
  static Directory directory() {
    final Person anne = new Person(fixedId("anne"), "Anne", "Fontaine & Fils <SA>", null, null, "",
                                   ContactStatus.CONTACTABLE, null, List.of())
                              .withEmail(new EmailAddress("anne@example.ch"))
                              .withPhone(new PhoneNumber("079 000 00 01"))
                              .withNotes("Capitaine \"♥\" 🃏");
    final Person zoe = new Person(fixedId("zoe"), "Zoé", "", null, null, "", ContactStatus.DECEASED,
                                  DATE.minusDays(1), List.of());
    final Person leo = new Person(fixedId("leo"), "Léo", "Berger", null, null, "", ContactStatus.CONTACTABLE, null,
                                  List.of());
    return new Directory(List.of(
        anne.withParticipations(List.of(new Participation(ID, "Match « hiver »", DATE, zoe.id()),
                                        new Participation(fixedId("individual"), "Individuel", DATE.minusYears(1),
                                                          null))),
        zoe.withParticipations(List.of(new Participation(ID, "Match « hiver »", DATE, anne.id()))),
        leo));
  }

  /**
   * Returns a fixed identifier, so that the fixtures are equal from one call to the next.
   *
   * @param name the name of the identified object.
   *
   * @return the identifier.
   */
  static UUID fixedId(final String name) {
    return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Returns a directory file written by {@link DirectoryWriter}.
   *
   * @param directory the directory.
   *
   * @return the bytes of the compressed file.
   */
  static byte[] directoryFile(final Directory directory) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      DirectoryWriter.write(directory, out);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

  /**
   * Returns the XML document of a directory, uncompressed, as written by {@link DirectoryWriter}.
   *
   * @param directory the directory.
   *
   * @return the XML document.
   */
  static String directoryXml(final Directory directory) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      DirectoryWriter.writeXml(directory, out);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  /**
   * Compresses bytes with GZIP, as the directory file.
   *
   * @param content the bytes.
   *
   * @return the compressed bytes.
   */
  static byte[] gzip(final byte[] content) {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(content);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return out.toByteArray();
  }

}
