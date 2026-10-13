package ch.varani.jass.persistence;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.AttachmentType;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.DomainException;
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
import ch.varani.jass.domain.TournamentMode;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Reads an edition file ({@code .jass}) written by {@link EditionWriter}: completely ({@link #read(InputStream)}), or
 * its header only, to list the editions ({@link #readHeader(InputStream)}).
 *
 * <p><b>Complete reading</b>, step by step:</p>
 * <ol>
 *   <li>archive: the first entry must be {@code edition.xml} ({@link FormatError#MISSING_EDITION}), at most
 *       {@link #MAX_EDITION_SIZE} bytes once decompressed, counted while reading, whatever the archive declares
 *       ({@link FormatError#TOO_LARGE}); the other entries must be supporting documents named
 *       {@code attachments/<sha256>.<pdf|png|jpg>}, each once ({@link FormatError#UNEXPECTED_ENTRY}: no other name,
 *       e.g. {@code ../x}, is ever accepted nor used as a path), at most {@link #MAX_ATTACHMENTS} of them, each at most
 *       {@link Attachment#MAX_SIZE} bytes, counted while reading, the content not kept
 *       ({@link FormatError#TOO_LARGE}); their content is checked against their digest only when a document is
 *       read ({@link EditionStore#exportAttachment(UUID, Attachment, java.nio.file.Path)}); a damaged or truncated
 *       archive gives {@link FormatError#INVALID_ARCHIVE};</li>
 *   <li>root element: UTF-8 only, no document type declaration ({@link XmlCursor}), format version
 *       {@link EditionXml#FORMAT_VERSION} or {@link EditionXml#FORMAT_VERSION_1}
 *       ({@link FormatError#UNSUPPORTED_VERSION}), before the schema, so that a newer file is reported as such;</li>
 *   <li>validation against the schema {@code edition.xsd} ({@link XmlSchema},
 *       {@link FormatError#INVALID_XML});</li>
 *   <li>conversion into the domain model, which checks the values (names, number of deals, duplicate persons,
 *       schedule matching the registrations…); the numbers of the teams and players of the schedule must exist in
 *       the registrations, and every document referenced by the accounting must be in the archive
 *       ({@link FormatError#INVALID_CONTENT}).</li>
 * </ol>
 *
 * <p><b>Header reading</b>: the same first two steps, then the settings, the deals and the registrations, to count
 * them, and whether a schedule follows (for the conversion of the maximum of teams of version
 * {@link EditionXml#FORMAT_VERSION_1}); the schedule, the results, the accounting and the other entries are not read.
 * It is not validated against the schema, but its structure and its values are checked.</p>
 *
 * <p><b>Format migration</b>: version {@link EditionXml#FORMAT_VERSION_1} has neither maximum of teams nor waiting
 * list. Its maximum of teams is set to the number of teams registered when the schedule is computed (the
 * registration is closed), otherwise to {@link Edition#DEFAULT_MAX_TEAMS} or to the number of teams registered when
 * it is greater ({@link Edition#teamsOf(Registrations)}: players divided by two, rounded up, in individual mode); its
 * waiting list is empty. From version {@link EditionXml#FORMAT_VERSION}, the maximum of teams is required
 * ({@link FormatError#INVALID_CONTENT}).</p>
 *
 * <p>The streams are not closed. Utility class without state, safe for concurrent use.</p>
 */
public final class EditionReader {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /**
   * The maximum size of {@code edition.xml} once decompressed, 8 MiB: an edition of 20 teams takes a few kilobytes,
   * and the cap protects against decompression bombs.
   */
  public static final int MAX_EDITION_SIZE = 8 * 1024 * 1024;
  /** The maximum number of supporting documents of an edition file. */
  public static final int MAX_ATTACHMENTS = 1_000;
  /** The allowed names of the entries of the supporting documents. */
  private static final Pattern ATTACHMENT_ENTRY =
      Pattern.compile(Pattern.quote(EditionXml.ATTACHMENTS) + "[0-9a-f]{64}\\.(pdf|png|jpg)");
  /** The size of the buffer used to count the bytes of an entry. */
  private static final int BUFFER_SIZE = 64 * 1024;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private EditionReader() {
  }

  // *******************************************************************************************************************
  // Reading
  // *******************************************************************************************************************

  /**
   * Reads a complete edition file.
   *
   * @param in the stream of the archive, not closed.
   *
   * @return the edition and the identifier of its file.
   *
   * @throws FileFormatException if the file is not a valid edition file, with the reason.
   * @throws IOException if the stream cannot be read.
   */
  public static StoredEdition read(final InputStream in) throws FileFormatException, IOException {
    final ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8);
    final byte[] xml = readEntry(zip);
    final Set<String> attachments = readAttachmentEntries(zip);
    readRoot(XmlCursor.of(xml));
    XmlSchema.EDITION.validate(xml);
    final XmlCursor cursor = XmlCursor.of(xml);
    final Root root = readRoot(cursor);
    final Edition edition;
    try {
      edition = readEdition(cursor, root.firstVersion());
    } catch (DomainException _) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
    for (final Attachment attachment : edition.ledger().attachments()) {
      if (!attachments.contains(attachment.fileName())) {
        throw new FileFormatException(FormatError.INVALID_CONTENT);
      }
    }
    return new StoredEdition(root.id(), edition);
  }

  /**
   * Reads the header of an edition file: its identifier, its settings and the number of its registrations.
   *
   * @param in the stream of the archive, not closed.
   *
   * @return the header.
   *
   * @throws FileFormatException if the beginning of the file is not a valid edition file, with the reason.
   * @throws IOException if the stream cannot be read.
   */
  public static EditionHeader readHeader(final InputStream in) throws FileFormatException, IOException {
    final XmlCursor cursor = XmlCursor.of(readEntry(new ZipInputStream(in, StandardCharsets.UTF_8)));
    final Root root = readRoot(cursor);
    try {
      final Settings settings = readSettings(cursor);
      final DealPlan deals = readDeals(cursor);
      cursor.open(EditionXml.REGISTRATIONS);
      final Registrations registrations = readRegistrations(cursor, settings.mode() == TournamentMode.TEAM);
      final boolean scheduled = EditionXml.SCHEDULE.equals(cursor.nextSection());
      return EditionHeader.of(root.id(), settings.edition(deals, registrations, null, Results.EMPTY, Ledger.EMPTY,
                                                          maxTeams(settings, root.firstVersion(), registrations,
                                                                   scheduled)));
    } catch (DomainException _) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
  }

  /**
   * Reads the entry {@code edition.xml}, the first one of the archive, into memory.
   *
   * @param zip the stream of the archive, at its start.
   *
   * @return the bytes of {@code edition.xml}.
   *
   * @throws FileFormatException with {@link FormatError#MISSING_EDITION}, {@link FormatError#TOO_LARGE} or
   *                             {@link FormatError#INVALID_ARCHIVE}.
   * @throws IOException if the stream cannot be read.
   */
  private static byte[] readEntry(final ZipInputStream zip) throws FileFormatException, IOException {
    try {
      final ZipEntry first = zip.getNextEntry();
      if (first == null || !EditionXml.ENTRY.equals(first.getName())) {
        throw new FileFormatException(FormatError.MISSING_EDITION);
      }
      final byte[] xml = zip.readNBytes(MAX_EDITION_SIZE + 1);
      if (xml.length > MAX_EDITION_SIZE) {
        throw new FileFormatException(FormatError.TOO_LARGE);
      }
      return xml;
    } catch (ZipException | EOFException _) {
      throw new FileFormatException(FormatError.INVALID_ARCHIVE);
    }
  }

  /**
   * Reads the entries after {@code edition.xml}: checks their names, their number and their sizes, counted while
   * reading, without keeping their content.
   *
   * @param zip the stream of the archive, after {@code edition.xml}.
   *
   * @return the names of the documents ({@link Attachment#fileName()}).
   *
   * @throws FileFormatException with {@link FormatError#UNEXPECTED_ENTRY}, {@link FormatError#TOO_LARGE} or
   *                             {@link FormatError#INVALID_ARCHIVE}.
   * @throws IOException if the stream cannot be read.
   */
  private static Set<String> readAttachmentEntries(final ZipInputStream zip) throws FileFormatException, IOException {
    final Set<String> names = new HashSet<>();
    final byte[] buffer = new byte[BUFFER_SIZE];
    try {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        if (!ATTACHMENT_ENTRY.matcher(entry.getName()).matches()
            || !names.add(entry.getName().substring(EditionXml.ATTACHMENTS.length()))) {
          throw new FileFormatException(FormatError.UNEXPECTED_ENTRY);
        }
        long size = 0;
        for (int read = zip.read(buffer); read >= 0; read = zip.read(buffer)) {
          size += read;
        }
        if (names.size() > MAX_ATTACHMENTS || size > Attachment.MAX_SIZE) {
          throw new FileFormatException(FormatError.TOO_LARGE);
        }
      }
      return names;
    } catch (ZipException | EOFException _) {
      throw new FileFormatException(FormatError.INVALID_ARCHIVE);
    }
  }

  /**
   * Reads the root element: checks the format version and returns it with the identifier of the file.
   *
   * @param cursor the cursor, before the root element.
   *
   * @return the identifier of the file and its format version.
   *
   * @throws FileFormatException with {@link FormatError#UNSUPPORTED_VERSION} for another format version, with
   *                             {@link FormatError#INVALID_CONTENT} for an identifier that is not a UUID in its
   *                             canonical form, or with {@link FormatError#INVALID_XML}.
   */
  private static Root readRoot(final XmlCursor cursor) throws FileFormatException {
    cursor.open(EditionXml.EDITION);
    final String version = cursor.attribute(EditionXml.FORMAT_VERSION_ATTRIBUTE);
    final boolean firstVersion = EditionXml.FORMAT_VERSION_1.equals(version);
    if (!firstVersion && !EditionXml.FORMAT_VERSION.equals(version)) {
      throw new FileFormatException(FormatError.UNSUPPORTED_VERSION);
    }
    final String value = cursor.attribute(EditionXml.ID);
    try {
      final UUID id = UUID.fromString(value);
      if (id.toString().equals(value)) {
        return new Root(id, firstVersion);
      }
    } catch (IllegalArgumentException _) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
    throw new FileFormatException(FormatError.INVALID_CONTENT);
  }

  /**
   * Reads the settings: name, date, mode, number of games, time of a game ({@link Edition#DEFAULT_GAME_MINUTES}
   * when absent, e.g. in the files written before it existed) and maximum of teams ({@code null} when absent, e.g. in
   * the files of version {@link EditionXml#FORMAT_VERSION_1}).
   *
   * @param cursor the cursor, at the start of the root element.
   *
   * @return the settings.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} for a date, a mode or a number of games
   *                             that cannot be parsed, or with {@link FormatError#INVALID_XML}.
   */
  private static Settings readSettings(final XmlCursor cursor) throws FileFormatException {
    cursor.open(EditionXml.SETTINGS);
    final String name = cursor.text(EditionXml.NAME);
    final String date = cursor.text(EditionXml.DATE);
    final String mode = cursor.text(EditionXml.MODE);
    final int games = number(cursor.text(EditionXml.GAMES), Integer.MAX_VALUE);
    final String minutes = cursor.optionalText(EditionXml.GAME_MINUTES);
    final String maxTeams = minutes == null ? null : cursor.optionalText(EditionXml.MAX_TEAMS);
    if (maxTeams != null) {
      cursor.close();
    }
    final int gameMinutes = minutes == null ? Edition.DEFAULT_GAME_MINUTES
                                            : number(minutes, Edition.MAX_GAME_MINUTES);
    try {
      return new Settings(name, LocalDate.parse(date), TournamentMode.valueOf(mode), games, gameMinutes,
                          maxTeams == null ? null : number(maxTeams, Integer.MAX_VALUE));
    } catch (DateTimeParseException | IllegalArgumentException _) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
  }

  /**
   * Reads the edition after its root element: settings, plan of the deals, registrations, schedule, results and
   * accounting; converts a file of version {@link EditionXml#FORMAT_VERSION_1}.
   *
   * @param cursor the cursor, at the start of the root element.
   * @param firstVersion whether the file has the format version {@link EditionXml#FORMAT_VERSION_1}.
   *
   * @return the edition.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} (e.g. a maximum of teams missing from a
   *                             file of the current version) or {@link FormatError#INVALID_XML}.
   * @throws DomainException if the domain model rejects a value.
   */
  private static Edition readEdition(final XmlCursor cursor, final boolean firstVersion) throws FileFormatException {
    final Settings settings = readSettings(cursor);
    final DealPlan deals = readDeals(cursor);
    final boolean team = settings.mode() == TournamentMode.TEAM;
    cursor.open(EditionXml.REGISTRATIONS);
    final Registrations registrations = readRegistrations(cursor, team);
    Schedule schedule = null;
    Results results = Results.EMPTY;
    String section = cursor.nextSection();
    if (EditionXml.SCHEDULE.equals(section)) {
      schedule = team ? readTeamSchedule(cursor, settings.games(), registrations.size())
                      : readIndividualSchedule(cursor, settings.games(), registrations.size());
      section = cursor.nextSection();
    }
    if (EditionXml.RESULTS.equals(section)) {
      results = readResults(cursor);
      section = cursor.nextSection();
    }
    final Ledger ledger = EditionXml.LEDGER.equals(section) ? readLedger(cursor) : Ledger.EMPTY;
    return settings.edition(deals, registrations, schedule, results, ledger,
                            maxTeams(settings, firstVersion, registrations, schedule != null));
  }

  /**
   * Returns the maximum of teams of an edition: the one of the file, or the one given by the conversion of a file of
   * version {@link EditionXml#FORMAT_VERSION_1}: the number of teams registered when the schedule is computed,
   * otherwise {@link Edition#DEFAULT_MAX_TEAMS} or the number of teams registered when it is greater.
   *
   * @param settings the settings read.
   * @param firstVersion whether the file has the format version {@link EditionXml#FORMAT_VERSION_1}.
   * @param registrations the registrations read.
   * @param scheduled whether the file has a schedule.
   *
   * @return the maximum of teams.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} if a file of the current version has no
   *                             maximum of teams.
   */
  private static int maxTeams(final Settings settings, final boolean firstVersion, final Registrations registrations,
                              final boolean scheduled) throws FileFormatException {
    if (!firstVersion) {
      if (settings.maxTeams() == null) {
        throw new FileFormatException(FormatError.INVALID_CONTENT);
      }
      return settings.maxTeams();
    }
    final int registered = Edition.teamsOf(registrations);
    return scheduled ? registered : Math.max(Edition.DEFAULT_MAX_TEAMS, registered);
  }

  /**
   * Reads the results, up to the end of their element. The document follows the schema: the numbers, the points, the
   * absences and the seed are well-formed and in their order.
   *
   * @param cursor the cursor, at the start of the results.
   *
   * @return the results.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} for a number out of range, or with
   *                             {@link FormatError#INVALID_XML}.
   * @throws DomainException if the domain model rejects a value.
   */
  private static Results readResults(final XmlCursor cursor) throws FileFormatException {
    final List<ScoreSheet> sheets = new ArrayList<>();
    final List<Integer> excluded = new ArrayList<>();
    TieBreak tieBreak = null;
    for (String element = cursor.nextSection(); element != null; element = cursor.nextSection()) {
      switch (element) {
        case EditionXml.SHEET -> sheets.add(readSheet(cursor));
        case EditionXml.EXCLUDED -> {
          excluded.add(number(cursor.attribute(EditionXml.NUMBER), Integer.MAX_VALUE));
          cursor.close();
        }
        default -> tieBreak = readTieBreak(cursor);
      }
    }
    return new Results(sheets, excluded, tieBreak);
  }

  /**
   * Reads a score sheet, up to the end of its element.
   *
   * @param cursor the cursor, at the start of the sheet.
   *
   * @return the score sheet.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} for a number out of range, or with
   *                             {@link FormatError#INVALID_XML}.
   */
  private static ScoreSheet readSheet(final XmlCursor cursor) throws FileFormatException {
    final int round = number(cursor.attribute(EditionXml.ROUND), Integer.MAX_VALUE);
    final int table = number(cursor.attribute(EditionXml.TABLE), Integer.MAX_VALUE);
    final String absence = cursor.optionalAttribute(EditionXml.ABSENCE);
    final List<DealScore> deals = new ArrayList<>();
    while (cursor.openOrClose(EditionXml.SCORE)) {
      deals.add(new DealScore(points(cursor.optionalAttribute(EditionXml.GIVER)),
                              points(cursor.optionalAttribute(EditionXml.RECEIVER))));
      cursor.close();
    }
    return new ScoreSheet(round, table, deals, absence == null ? Absence.NONE : Absence.valueOf(absence));
  }

  /**
   * Reads the accounting, up to the end of its element. The document follows the schema: the amounts, the
   * identifiers, the dates, the types and the digests are well-formed.
   *
   * @param cursor the cursor, at the start of the accounting.
   *
   * @return the accounting.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML}.
   * @throws DomainException if the domain model rejects a value.
   */
  private static Ledger readLedger(final XmlCursor cursor) throws FileFormatException {
    final Money fee = Money.of(cursor.attribute(EditionXml.FEE));
    final List<LedgerEntry> entries = new ArrayList<>();
    while (cursor.openOrClose(EditionXml.ENTRY_ELEMENT)) {
      final UUID id = UUID.fromString(cursor.attribute(EditionXml.ID));
      final LocalDate date = LocalDate.parse(cursor.attribute(EditionXml.DATE));
      final EntryType type = EntryType.valueOf(cursor.attribute(EditionXml.TYPE));
      final String category = cursor.attribute(EditionXml.CATEGORY);
      final String description = cursor.optionalAttribute(EditionXml.DESCRIPTION);
      final Money amount = Money.of(cursor.attribute(EditionXml.AMOUNT));
      final List<Attachment> attachments = new ArrayList<>();
      while (cursor.openOrClose(EditionXml.ATTACHMENT)) {
        attachments.add(new Attachment(cursor.attribute(EditionXml.SHA256),
                                       AttachmentType.valueOf(cursor.attribute(EditionXml.TYPE)),
                                       cursor.attribute(EditionXml.NAME),
                                       Long.parseLong(cursor.attribute(EditionXml.SIZE))));
        cursor.close();
      }
      entries.add(new LedgerEntry(id, date, type, category, description == null ? "" : description, amount,
                                  attachments));
    }
    return new Ledger(fee, entries);
  }

  /**
   * Parses the points typed in a column of a deal; the schema guarantees an integer between 0 and 157.
   *
   * @param value the text of the attribute, or {@code null} if the column was not typed.
   *
   * @return the points, or {@code null}.
   */
  private static Integer points(final String value) {
    return value == null ? null : Integer.valueOf(value);
  }

  /**
   * Reads the drawing of lots, up to the end of its element.
   *
   * @param cursor the cursor, at the start of the drawing of lots.
   *
   * @return the drawing of lots.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} for a number out of range, or with
   *                             {@link FormatError#INVALID_XML}.
   */
  private static TieBreak readTieBreak(final XmlCursor cursor) throws FileFormatException {
    final long seed = Long.parseLong(cursor.attribute(EditionXml.SEED));
    final List<Integer> order = new ArrayList<>();
    while (cursor.openOrClose(EditionXml.LOT)) {
      order.add(number(cursor.attribute(EditionXml.NUMBER), Integer.MAX_VALUE));
      cursor.close();
    }
    return new TieBreak(seed, order);
  }

  /**
   * Reads the plan of the deals: the type of each deal, in their order. The types are valid, as the document follows
   * the schema.
   *
   * @param cursor the cursor, after the settings.
   *
   * @return the plan of the deals.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML}.
   * @throws DomainException if the domain model rejects the number of deals.
   */
  private static DealPlan readDeals(final XmlCursor cursor) throws FileFormatException {
    cursor.open(EditionXml.DEALS);
    final List<DealType> deals = new ArrayList<>();
    while (cursor.openOrClose(EditionXml.DEAL)) {
      deals.add(DealType.valueOf(cursor.attribute(EditionXml.TYPE)));
      cursor.close();
    }
    return new DealPlan(deals);
  }

  /**
   * Reads the registrations, up to their end: the teams (or the players in individual mode), then the waiting list,
   * when it is present.
   *
   * @param cursor the cursor, at the start of the registrations.
   * @param team whether the edition is in team mode.
   *
   * @return the registrations.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML} if a participant does not match the mode.
   * @throws DomainException if the domain model rejects a team or a player.
   */
  private static Registrations readRegistrations(final XmlCursor cursor, final boolean team)
      throws FileFormatException {
    final String participant = team ? EditionXml.TEAM : EditionXml.PLAYER;
    final List<List<Player>> registered = new ArrayList<>();
    final List<List<Player>> waiting = new ArrayList<>();
    for (String element = cursor.nextSection(); element != null; element = cursor.nextSection()) {
      if (EditionXml.WAITING.equals(element)) {
        while (cursor.openOrClose(participant)) {
          waiting.add(readParticipant(cursor, team));
        }
      } else if (participant.equals(element)) {
        registered.add(readParticipant(cursor, team));
      } else {
        throw new FileFormatException(FormatError.INVALID_XML);
      }
    }
    if (team) {
      return new TeamRegistrations(registered.stream().map(Team::new).toList(),
                                   waiting.stream().map(Team::new).toList());
    }
    return new IndividualRegistrations(registered.stream().map(List::getFirst).toList(),
                                       waiting.stream().map(List::getFirst).toList());
  }

  /**
   * Reads a participant, up to the end of its element: the two players of a team, or a player.
   *
   * @param cursor the cursor, at the start of the team or of the player.
   * @param team whether the participant is a team.
   *
   * @return the players of the participant.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML}.
   */
  private static List<Player> readParticipant(final XmlCursor cursor, final boolean team) throws FileFormatException {
    if (!team) {
      return List.of(readPlayer(cursor));
    }
    final List<Player> players = new ArrayList<>();
    while (cursor.openOrClose(EditionXml.PLAYER)) {
      players.add(readPlayer(cursor));
    }
    return players;
  }

  /**
   * Reads a player, up to the end of its element, with the identifier of its person of the directory when it is
   * linked to one (a UUID, as the document follows the schema) and whether its fee is paid (absent: not paid;
   * {@code 1} is read as not paid, the writer always writes {@code true}).
   *
   * @param cursor the cursor, at the start of the player.
   *
   * @return the player.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_XML}.
   * @throws DomainException if the domain model rejects the player.
   */
  private static Player readPlayer(final XmlCursor cursor) throws FileFormatException {
    final String person = cursor.optionalAttribute(EditionXml.PERSON);
    final Player player = new Player(cursor.attribute(EditionXml.FIRST_NAME), cursor.attribute(EditionXml.LAST_NAME),
                                     person == null ? null : UUID.fromString(person),
                                     Boolean.parseBoolean(cursor.optionalAttribute(EditionXml.PAID)));
    cursor.close();
    return player;
  }

  /**
   * Reads the rounds of a schedule of the team mode, up to the end of the schedule.
   *
   * @param cursor the cursor, at the start of the schedule.
   * @param games the number of games of the edition.
   * @param teams the number of teams of the registrations.
   *
   * @return the schedule.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} for a team number that is not in the
   *                             registrations, or with {@link FormatError#INVALID_XML}.
   */
  private static TeamSchedule readTeamSchedule(final XmlCursor cursor, final int games, final int teams)
      throws FileFormatException {
    final List<TeamRound> rounds = new ArrayList<>();
    while (cursor.openOrClose(EditionXml.ROUND)) {
      final int round = number(cursor.attribute(EditionXml.NUMBER), Integer.MAX_VALUE);
      final List<TeamGame> roundGames = new ArrayList<>();
      while (cursor.openOrClose(EditionXml.TEAM_GAME)) {
        roundGames.add(new TeamGame(number(cursor.attribute(EditionXml.TABLE), Integer.MAX_VALUE),
                                    number(cursor.attribute(EditionXml.GIVER), teams),
                                    number(cursor.attribute(EditionXml.RECEIVER), teams)));
        cursor.close();
      }
      rounds.add(new TeamRound(round, roundGames));
    }
    return new TeamSchedule(teams, games, rounds);
  }

  /**
   * Reads the rounds of a schedule of the individual mode, up to the end of the schedule.
   *
   * @param cursor the cursor, at the start of the schedule.
   * @param games the number of games of the edition.
   * @param players the number of players of the registrations.
   *
   * @return the schedule.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} for a player number that is not in the
   *                             registrations, or with {@link FormatError#INVALID_XML}.
   */
  private static IndividualSchedule readIndividualSchedule(final XmlCursor cursor, final int games, final int players)
      throws FileFormatException {
    final List<IndividualRound> rounds = new ArrayList<>();
    while (cursor.openOrClose(EditionXml.ROUND)) {
      final int round = number(cursor.attribute(EditionXml.NUMBER), Integer.MAX_VALUE);
      final List<IndividualGame> roundGames = new ArrayList<>();
      while (cursor.openOrClose(EditionXml.INDIVIDUAL_GAME)) {
        final int table = number(cursor.attribute(EditionXml.TABLE), Integer.MAX_VALUE);
        final Partners giver = readPartners(cursor, EditionXml.GIVER, players);
        final Partners receiver = readPartners(cursor, EditionXml.RECEIVER, players);
        roundGames.add(new IndividualGame(table, giver, receiver));
        cursor.close();
      }
      rounds.add(new IndividualRound(round, roundGames));
    }
    return new IndividualSchedule(players, games, rounds);
  }

  /**
   * Reads a team of the individual mode: the numbers of its two players.
   *
   * @param cursor the cursor, at the start of the game.
   * @param name the name of the element: giver or receiver.
   * @param players the number of players of the registrations.
   *
   * @return the two partners.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} for a player number that is not in the
   *                             registrations, or with {@link FormatError#INVALID_XML}.
   */
  private static Partners readPartners(final XmlCursor cursor, final String name, final int players)
      throws FileFormatException {
    cursor.open(name);
    final Partners partners = new Partners(number(cursor.attribute(EditionXml.FIRST), players),
                                           number(cursor.attribute(EditionXml.SECOND), players));
    cursor.close();
    return partners;
  }

  /**
   * Parses a number that must be between 1 and a maximum.
   *
   * @param value the text of the number.
   * @param max the greatest allowed value.
   *
   * @return the number.
   *
   * @throws FileFormatException with {@link FormatError#INVALID_CONTENT} if the text is not an integer, or if the
   *                             number is out of range.
   */
  static int number(final String value, final int max) throws FileFormatException {
    final int number;
    try {
      number = Integer.parseInt(value);
    } catch (NumberFormatException _) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
    if (number < 1 || number > max) {
      throw new FileFormatException(FormatError.INVALID_CONTENT);
    }
    return number;
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The root element of an edition file: the identifier of the file and its format version.
   *
   * <p>Immutable, safe for concurrent use.</p>
   *
   * @param id the identifier of the file.
   * @param firstVersion whether the file has the format version {@link EditionXml#FORMAT_VERSION_1}, to convert.
   */
  private record Root(UUID id, boolean firstVersion) {
  }

  /**
   * The settings of an edition, as read from the file, before their check by the domain model.
   *
   * <p>Immutable, safe for concurrent use.</p>
   *
   * @param name the name of the edition.
   * @param date the date of the edition.
   * @param mode the tournament mode.
   * @param games the number of games of each participant.
   * @param gameMinutes the time given to play a game, in minutes.
   * @param maxTeams the maximum number of teams, or {@code null} when the file has none.
   */
  private record Settings(String name, LocalDate date, TournamentMode mode, int games, int gameMinutes,
                          Integer maxTeams) {

    /**
     * Creates the edition of these settings.
     *
     * @param deals the plan of the deals.
     * @param registrations the registrations, of the mode of the settings.
     * @param schedule the schedule, or {@code null}.
     * @param results the results.
     * @param ledger the accounting.
     * @param teams the maximum number of teams.
     *
     * @return the edition.
     *
     * @throws DomainException if the domain model rejects a value.
     */
    Edition edition(final DealPlan deals, final Registrations registrations, final Schedule schedule,
                    final Results results, final Ledger ledger, final int teams) {
      return new Edition(name, date, games, deals, registrations, schedule, results, ledger, gameMinutes, teams);
    }

  }

}
