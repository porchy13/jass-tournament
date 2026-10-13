package ch.varani.jass.persistence;

/**
 * The names of the format of the edition files ({@code .jass}), shared by {@link EditionWriter} and
 * {@link EditionReader}: the ZIP entry, the XML namespace, the format version, the elements and the attributes.
 *
 * <p>The structure of {@code edition.xml} is defined by the XML schema {@code edition.xsd}.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class EditionXml {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The name of the ZIP entry holding the edition, the first entry of the archive. */
  static final String ENTRY = "edition.xml";
  /** The folder of the entries holding the supporting documents, named {@code <sha256>.<extension>}. */
  static final String ATTACHMENTS = "attachments/";
  /** The XML namespace of the elements of {@code edition.xml}. */
  static final String NAMESPACE = "urn:ch:varani:jass:edition";
  /** The current version of the format, written in {@link #FORMAT_VERSION_ATTRIBUTE}. */
  static final String FORMAT_VERSION = "2";
  /**
   * The first version of the format (application 1.0.0 to 1.0.5), without maximum of teams nor waiting list; read
   * and converted by {@link EditionReader}.
   */
  static final String FORMAT_VERSION_1 = "1";

  /** The root element. */
  static final String EDITION = "edition";
  /** The attribute of the root element giving the version of the format. */
  static final String FORMAT_VERSION_ATTRIBUTE = "formatVersion";
  /** The attribute of the root element giving the identifier of the file. */
  static final String ID = "id";
  /** The element of the settings: name, date, mode, games. */
  static final String SETTINGS = "settings";
  /** The element of the name of the edition. */
  static final String NAME = "name";
  /** The element of the date of the edition. */
  static final String DATE = "date";
  /** The element of the tournament mode. */
  static final String MODE = "mode";
  /** The element of the number of games of each participant. */
  static final String GAMES = "games";
  /** The element of the time given to play a game, in minutes; optional, 60 when absent. */
  static final String GAME_MINUTES = "gameMinutes";
  /** The maximum number of teams of an edition (format version 2). */
  static final String MAX_TEAMS = "maxTeams";
  /** The element of the plan of the deals of every game. */
  static final String DEALS = "deals";
  /** The element of a deal of the plan. */
  static final String DEAL = "deal";
  /** The attribute of the type of a deal. */
  static final String TYPE = "type";
  /** The element of the registrations. */
  static final String REGISTRATIONS = "registrations";
  /** The element of a team of the registrations. */
  static final String TEAM = "team";
  /** The element of a player of the registrations. */
  static final String PLAYER = "player";
  /** The waiting list of the registrations (format version 2). */
  static final String WAITING = "waiting";
  /** The attribute of the first name of a player. */
  static final String FIRST_NAME = "firstName";
  /** The attribute of the last name of a player. */
  static final String LAST_NAME = "lastName";
  /** The attribute of the identifier of the person of the directory to whom a player is linked. */
  static final String PERSON = "person";
  /** The optional attribute telling that a player has paid its registration fee. */
  static final String PAID = "paid";
  /** The element of the schedule. */
  static final String SCHEDULE = "schedule";
  /** The element of a round of the schedule. */
  static final String ROUND = "round";
  /** The attribute of the number of a round. */
  static final String NUMBER = "number";
  /** The element of a game of the team mode. */
  static final String TEAM_GAME = "teamGame";
  /** The element of a game of the individual mode. */
  static final String INDIVIDUAL_GAME = "individualGame";
  /** The attribute of the number of the table of a game. */
  static final String TABLE = "table";
  /** The attribute (team mode) or element (individual mode) of the giver of a game. */
  static final String GIVER = "giver";
  /** The attribute (team mode) or element (individual mode) of the receiver of a game. */
  static final String RECEIVER = "receiver";
  /** The element of the results. */
  static final String RESULTS = "results";
  /** The element of a score sheet. */
  static final String SHEET = "sheet";
  /** The optional attribute of the absent sides of a score sheet. */
  static final String ABSENCE = "absence";
  /** The element of the points of a deal of a score sheet. */
  static final String SCORE = "score";
  /** The element of an excluded participant. */
  static final String EXCLUDED = "excluded";
  /** The element of the drawing of lots. */
  static final String TIE_BREAK = "tieBreak";
  /** The attribute of the seed of the drawing of lots. */
  static final String SEED = "seed";
  /** The element of a participant in the order of the drawing of lots. */
  static final String LOT = "lot";
  /** The element of the accounting. */
  static final String LEDGER = "ledger";
  /** The attribute of the registration fee of a player. */
  static final String FEE = "fee";
  /** The element of an entry of the accounting. */
  static final String ENTRY_ELEMENT = "entry";
  /** The attribute of the category of an entry. */
  static final String CATEGORY = "category";
  /** The optional attribute of the description of an entry. */
  static final String DESCRIPTION = "description";
  /** The attribute of the amount of an entry. */
  static final String AMOUNT = "amount";
  /** The element of a supporting document of an entry. */
  static final String ATTACHMENT = "attachment";
  /** The attribute of the SHA-256 digest of a supporting document. */
  static final String SHA256 = "sha256";
  /** The attribute of the size of a supporting document. */
  static final String SIZE = "size";
  /** The attribute of the first partner of a team of the individual mode. */
  static final String FIRST = "first";
  /** The attribute of the second partner of a team of the individual mode. */
  static final String SECOND = "second";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private EditionXml() {
  }

}
