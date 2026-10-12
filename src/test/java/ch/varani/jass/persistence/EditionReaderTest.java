package ch.varani.jass.persistence;

import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.TournamentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link EditionReader}: round trip of the editions of both modes, header reading, and rejection of the
 * damaged, unexpected or malicious files (decompression bomb, path traversal, XXE, other encodings, newer versions).
 */
final class EditionReaderTest {

  /** The size of the local header of the first entry of an archive: the compressed data starts after it. */
  private static final int LOCAL_HEADER = 40;
  /** A byte that is never valid in UTF-8 and breaks the compressed data. */
  private static final byte INVALID_BYTE = (byte) 0xFF;
  /** The number of hexadecimal digits of a SHA-256 digest. */
  private static final int DIGEST_LENGTH = 64;
  /** The size of a document whose compressed data spans more than a few bytes. */
  private static final int LARGE_ENTRY = 100_000;
  /** The maximum of the tested numbers. */
  private static final int MAX = 3;
  /** A time of a game other than the default one, in minutes. */
  private static final int GAME_MINUTES = 90;

  /**
   * Provides the editions of the round trip tests.
   *
   * @return editions of both modes, with and without schedule.
   */
  static Stream<Arguments> editions() {
    return Stream.of(Arguments.of(Fixtures.teamEdition()), Arguments.of(Fixtures.scheduledTeamEdition()),
                     Arguments.of(Fixtures.individualEdition()), Arguments.of(Fixtures.scheduledIndividualEdition()),
                     Arguments.of(Edition.create("Vide", Fixtures.DATE, TournamentMode.TEAM)),
                     Arguments.of(Fixtures.scoredTeamEdition()),
                     Arguments.of(Fixtures.scheduledIndividualEdition().withResults(Results.EMPTY.withSheet(
                         Fixtures.scoredTeamEdition().results().sheets().getFirst()))),
                     Arguments.of(Fixtures.teamEdition().withDeals(new DealPlan(List.of(
                         DealType.IMPOSED_SPADES, DealType.FORCED_TRUMP, DealType.FORCED_TRUMP, DealType.KREUZ)))),
                     Arguments.of(Fixtures.teamEdition().withGameMinutes(GAME_MINUTES)));
  }

  /**
   * A file written before the time of a game existed is read with the default time.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F18: a file without the time of a game is read with 60 minutes")
  void defaultGameMinutesTest() throws Exception {
    final Edition edition = Fixtures.teamEdition().withGameMinutes(GAME_MINUTES);
    final String xml = Fixtures.xml(edition).replace("<gameMinutes>" + GAME_MINUTES + "</gameMinutes>", "");
    assertEquals(Edition.DEFAULT_GAME_MINUTES, read(Fixtures.archive(xml)).edition().gameMinutes());
  }

  /**
   * An edition read back is equal to the written one, with its identifier.
   *
   * @param edition the edition.
   *
   * @throws Exception never.
   */
  @ParameterizedTest
  @MethodSource("editions")
  @DisplayName("An edition read back equals the written one")
  void roundTripTest(final Edition edition) throws Exception {
    assertEquals(new StoredEdition(Fixtures.ID, edition), read(Fixtures.file(edition)));
  }

  /**
   * The header gives the identifier and the settings of the edition.
   *
   * @param edition the edition.
   *
   * @throws Exception never.
   */
  @ParameterizedTest
  @MethodSource("editions")
  @DisplayName("The header gives the identifier and the settings")
  void headerTest(final Edition edition) throws Exception {
    assertEquals(new EditionHeader(Fixtures.ID, edition.name(), edition.date(), edition.mode(), edition.games()),
                 EditionReader.readHeader(new ByteArrayInputStream(Fixtures.file(edition))));
  }

  /**
   * The header is read without looking at the rest of the archive.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The header ignores the entries after edition.xml")
  void headerIgnoresOtherEntriesTest() throws Exception {
    final byte[] file = Fixtures.archive(Map.of(EditionXml.ENTRY, xmlBytes(Fixtures.teamEdition()),
                                                "other.txt", new byte[1]));
    assertEquals(Fixtures.ID, EditionReader.readHeader(new ByteArrayInputStream(file)).id());
  }

  /**
   * A file that is not an archive, an empty archive, or an archive starting with another entry is not an edition.
   */
  @Test
  @DisplayName("A file without edition.xml first is not an edition")
  void missingEditionTest() {
    final byte[] xml = xmlBytes(Fixtures.teamEdition());
    assertAll(
        () -> assertError(FormatError.MISSING_EDITION, "Not a ZIP archive".getBytes(StandardCharsets.UTF_8)),
        () -> assertError(FormatError.MISSING_EDITION, Fixtures.archive(Map.of())),
        () -> assertError(FormatError.MISSING_EDITION, Fixtures.archive(Map.of("other.xml", xml))));
  }

  /**
   * Path traversal: an entry after {@code edition.xml}, whatever its name, is rejected.
   */
  @Test
  @DisplayName("Any other entry, such as a path traversal, is rejected")
  void unexpectedEntryTest() {
    final byte[] xml = xmlBytes(Fixtures.teamEdition());
    assertAll(
        () -> assertError(FormatError.UNEXPECTED_ENTRY, Fixtures.archive(Map.of(EditionXml.ENTRY, xml,
                                                                                "../evil.sh", new byte[1]))),
        () -> assertError(FormatError.UNEXPECTED_ENTRY, Fixtures.archive(Map.of(EditionXml.ENTRY, xml,
                                                                                "/etc/passwd", new byte[1]))));
  }

  /**
   * Decompression bomb: an {@code edition.xml} larger than the maximum once decompressed is rejected, whatever the
   * size declared by the archive.
   */
  @Test
  @DisplayName("Decompression bomb: an oversized edition.xml is rejected")
  void entryTooLargeTest() {
    final byte[] huge = new byte[EditionReader.MAX_EDITION_SIZE + 1];
    Arrays.fill(huge, (byte) ' ');
    final byte[] file = Fixtures.archive(Map.of(EditionXml.ENTRY, huge));
    assertAll(
        () -> assertEquals(FormatError.TOO_LARGE,
                           assertThrows(FileFormatException.class,
                                        () -> EditionReader.readHeader(new ByteArrayInputStream(file))).getError()),
        () -> assertError(FormatError.TOO_LARGE, file));
  }

  /**
   * A truncated or damaged archive is rejected.
   */
  @Test
  @DisplayName("A truncated or damaged archive is rejected")
  void invalidArchiveTest() {
    final byte[] file = Fixtures.file(Fixtures.scheduledTeamEdition());
    final byte[] truncated = Arrays.copyOf(file, file.length / 2);
    final byte[] damaged = file.clone();
    for (int i = LOCAL_HEADER; i < damaged.length / 2; i++) {
      damaged[i] = INVALID_BYTE;
    }
    assertAll(
        () -> assertError(FormatError.INVALID_ARCHIVE, truncated),
        () -> assertError(FormatError.INVALID_ARCHIVE, damaged));
  }

  /**
   * A newer format version is reported as such, before the schema.
   */
  @Test
  @DisplayName("A newer format version is reported")
  void unsupportedVersionTest() {
    final String xml = Fixtures.xml(Fixtures.teamEdition()).replace("formatVersion=\"1\"", "formatVersion=\"2\"");
    assertError(FormatError.UNSUPPORTED_VERSION, Fixtures.archive(xml));
  }

  /**
   * XXE: a document type declaration is rejected, so that no external entity is ever loaded.
   */
  @Test
  @DisplayName("XXE: a document type declaration is rejected")
  void documentTypeTest() {
    final String xml = Fixtures.xml(Fixtures.teamEdition())
        .replace("?>", "?>\n<!DOCTYPE edition [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>")
        .replace("<name>", "<name>&xxe;");
    assertError(FormatError.INVALID_XML, Fixtures.archive(xml));
  }

  /**
   * A document in another encoding than UTF-8, or with bytes that are not UTF-8, is rejected.
   */
  @Test
  @DisplayName("Only UTF-8 is accepted")
  void encodingTest() {
    final String latin = Fixtures.xml(Fixtures.teamEdition()).replace("UTF-8", "ISO-8859-1");
    final byte[] invalid = xmlBytes(Fixtures.teamEdition());
    final int name = new String(invalid, StandardCharsets.ISO_8859_1).indexOf("<name>") + "<name>".length();
    invalid[name] = INVALID_BYTE;
    assertAll(
        () -> assertError(FormatError.INVALID_XML, Fixtures.archive(latin)),
        () -> assertError(FormatError.INVALID_XML, Fixtures.archive(Map.of(EditionXml.ENTRY, invalid))),
        () -> assertError(FormatError.INVALID_XML, Fixtures.archive("<?xml version=")));
  }

  /**
   * A document without XML declaration is accepted, as UTF-8.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("A document without XML declaration is read as UTF-8")
  void noDeclarationTest() throws Exception {
    final String xml = Fixtures.xml(Fixtures.teamEdition());
    assertEquals(Fixtures.teamEdition(), read(Fixtures.archive(xml.substring(xml.indexOf("?>") + 2))).edition());
  }

  /**
   * Provides documents that do not follow the schema.
   *
   * @return the documents.
   */
  static Stream<Arguments> invalidDocuments() {
    final String team = Fixtures.xml(Fixtures.scheduledTeamEdition());
    final String individual = Fixtures.xml(Fixtures.scheduledIndividualEdition());
    final String scored = Fixtures.xml(Fixtures.scoredTeamEdition());
    final String ledger = Fixtures.xml(Fixtures.ledgerEdition());
    return Stream.of(
        Arguments.of("fee without centimes", ledger.replace("fee=\"30.00\"", "fee=\"30\"")),
        Arguments.of("amount with a comma", ledger.replace("amount=\"200.00\"", "amount=\"200,00\"")),
        Arguments.of("paid not a boolean", ledger.replace("paid=\"true\"", "paid=\"maybe\"")),
        Arguments.of("unknown entry type", ledger.replace("\"INCOME\"", "\"GIFT\"")),
        Arguments.of("unknown document type", ledger.replace("\"JPEG\"", "\"GIF\"")),
        Arguments.of("invalid digest", ledger.replaceFirst("sha256=\"[0-9a-f]{64}\"", "sha256=\"abc\"")),
        Arguments.of("entry without category", ledger.replace(" category=\"Sponsoring\"", "")),
        Arguments.of("not well-formed", team.replace("</edition>", "")),
        Arguments.of("unknown element", team.replace("</settings>", "</settings><extra/>")),
        Arguments.of("unknown attribute", team.replace("<teamGame ", "<teamGame color=\"red\" ")),
        Arguments.of("missing attribute", team.replace("lastName=\"Müller\"", "")),
        Arguments.of("three players in a team", team.replaceFirst("(<player [^>]*/>)", "$1$1")),
        Arguments.of("negative number", team.replace("giver=\"1\"", "giver=\"-1\"")),
        Arguments.of("unknown mode", team.replace(">TEAM<", ">PAIRS<")),
        Arguments.of("invalid person", team.replaceFirst("person=\"[^\"]*\"", "person=\"Zoé\"")),
        Arguments.of("points over 157", scored.replace("giver=\"100\"", "giver=\"158\"")),
        Arguments.of("negative points", scored.replace("receiver=\"0\"", "receiver=\"-1\"")),
        Arguments.of("unknown absence", scored.replace("\"RECEIVER\"", "\"NOBODY\"")),
        Arguments.of("tie break without seed", scored.replace(" seed=\"" + Fixtures.SEED + "\"", "")),
        Arguments.of("sheet without deals", scored.replaceFirst("(?s)(<sheet round=\"1\" table=\"2\"[^>]*>).*?</sheet>",
                                                                "$1</sheet>")),
        Arguments.of("unknown deal", team.replace("type=\"KREUZ\"", "type=\"OBENABE\"")),
        Arguments.of("no deals", team.replaceFirst("(?s)<deals>.*</deals>", "")),
        Arguments.of("empty deals", team.replaceFirst("(?s)<deals>.*</deals>", "<deals></deals>")),
        Arguments.of("other namespace", team.replace(EditionXml.NAMESPACE, "urn:other")),
        Arguments.of("text in a list", individual.replace("<registrations>", "<registrations>text")),
        Arguments.of("team game in individual mode",
                     individual.replace("<individualGame table=\"1\">", "<teamGame table=\"1\" giver=\"1\" "
                                        + "receiver=\"2\"/><individualGame table=\"1\">")),
        Arguments.of("teams in individual mode", individual.replace(">INDIVIDUAL<", ">TEAM<")),
        Arguments.of("players in team mode", team.replace(">TEAM<", ">INDIVIDUAL<")));
  }

  /**
   * A document that is not well-formed or does not follow the schema is rejected.
   *
   * @param description the description of the defect.
   * @param xml the document.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidDocuments")
  @DisplayName("A document that does not follow the schema is rejected")
  void invalidXmlTest(final String description, final String xml) {
    assertError(FormatError.INVALID_XML, Fixtures.archive(xml));
  }

  /**
   * Provides documents that follow the schema but whose content is not valid.
   *
   * @return the documents.
   */
  static Stream<Arguments> invalidContents() {
    final String team = Fixtures.xml(Fixtures.scheduledTeamEdition());
    final String individual = Fixtures.xml(Fixtures.scheduledIndividualEdition());
    final String scored = Fixtures.xml(Fixtures.scoredTeamEdition());
    final String ledger = Fixtures.xml(Fixtures.ledgerEdition()).replaceAll("<attachment [^>]*/>", "");
    return Stream.of(
        Arguments.of("zero amount", ledger.replace("amount=\"200.00\"", "amount=\"0.00\"")),
        Arguments.of("blank category", ledger.replace("category=\"Sponsoring\"", "category=\" \"")),
        Arguments.of("same entry twice", ledger.replace(Fixtures.fixedId("expense").toString(),
                                                        Fixtures.fixedId("income").toString())),
        Arguments.of("blank name", team.replaceFirst("<name>[^<]*</name>", "<name> </name>")),
        Arguments.of("same person twice",
                     team.replace("firstName=\"AZoé\" lastName=\"Müller\"",
                                  "firstName=\"AAnne\" lastName=\"Fontaine &amp; Fils &lt;SA&gt;\"")),
        Arguments.of("control character", team.replace("AZoé", "A&#x7F;")),
        Arguments.of("same person of the directory twice",
                     team.replace(UUID.nameUUIDFromBytes("B".getBytes(StandardCharsets.UTF_8)).toString(),
                                  UUID.nameUUIDFromBytes("A".getBytes(StandardCharsets.UTF_8)).toString())),
        Arguments.of("sheet for an unknown game", scored.replace("<sheet round=\"1\" table=\"2\"",
                                                                 "<sheet round=\"3\" table=\"2\"")),
        Arguments.of("two sheets for a game", scored.replace("<sheet round=\"1\" table=\"2\"",
                                                             "<sheet round=\"1\" table=\"1\"")),
        Arguments.of("sheet with another number of deals", scored.replaceFirst("<score/>", "")),
        Arguments.of("unknown excluded team", scored.replace("<excluded number=\"5\"", "<excluded number=\"6\"")),
        Arguments.of("unknown lot", scored.replace("<lot number=\"3\"", "<lot number=\"9\"")),
        Arguments.of("lot drawn twice", scored.replace("<lot number=\"3\"", "<lot number=\"2\"")),
        Arguments.of("results without schedule", scored.replaceFirst("(?s)<schedule>.*</schedule>", "")),
        Arguments.of("deals not a multiple of 4", team.replaceFirst("<deal type=\"KREUZ\"/>", "")),
        Arguments.of("unknown team", team.replace("giver=\"5\"", "giver=\"6\"")),
        Arguments.of("unknown player", individual.replace("second=\"5\"", "second=\"6\"")));
  }

  /**
   * A document whose values the domain model rejects, or whose numbers are out of range, is rejected.
   *
   * @param description the description of the defect.
   * @param xml the document.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidContents")
  @DisplayName("A document with invalid values is rejected")
  void invalidContentTest(final String description, final String xml) {
    assertError(FormatError.INVALID_CONTENT, Fixtures.archive(xml));
  }

  /**
   * Provides headers that are not valid: the header is not validated against the schema, so its reading checks the
   * structure and the values itself.
   *
   * @return the documents and the expected error.
   */
  static Stream<Arguments> invalidHeaders() {
    final String xml = Fixtures.xml(Fixtures.teamEdition());
    return Stream.of(
        Arguments.of(FormatError.INVALID_XML, xml.replace(" id=\"" + Fixtures.ID + "\"", "")),
        Arguments.of(FormatError.INVALID_XML, xml.replace("<settings>", "<other>")),
        Arguments.of(FormatError.INVALID_XML, xml.replaceFirst("<name>[^<]*</name>", "")),
        Arguments.of(FormatError.INVALID_XML, xml.replace("<games>4</games>", "<games>4</games><extra/>")),
        Arguments.of(FormatError.INVALID_XML, xml.replace("<name>", "<name><b/>")),
        Arguments.of(FormatError.INVALID_XML, xml.replace("<gameMinutes>", "<gameMinutes><b/>")),
        Arguments.of(FormatError.INVALID_XML, xml.replace("</gameMinutes>", "</gameMinutes><extra/>")),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replaceFirst("<gameMinutes>\\d+", "<gameMinutes>0")),
        Arguments.of(FormatError.INVALID_XML,
                     xml.replaceFirst("(?s)<settings>.*</settings>", "<settings></settings>")),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replace(Fixtures.ID.toString(), "not-a-uuid")),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replace(Fixtures.ID.toString(),
                                                              Fixtures.ID.toString().toUpperCase(Locale.ROOT))),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replace(">2026-11-20<", ">20.11.2026<")),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replace(">TEAM<", ">PAIRS<")),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replace("<games>4", "<games>four")),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replace("<games>4", "<games>0")),
        Arguments.of(FormatError.INVALID_CONTENT, xml.replaceFirst("<name>[^<]*</name>", "<name> </name>")));
  }

  /**
   * A header with a wrong structure or wrong values is rejected.
   *
   * @param error the expected error.
   * @param xml the document.
   */
  @ParameterizedTest(name = "{0}: {index}")
  @MethodSource("invalidHeaders")
  @DisplayName("A header with a wrong structure or values is rejected")
  void invalidHeaderTest(final FormatError error, final String xml) {
    final byte[] file = Fixtures.archive(xml);
    assertEquals(error, assertThrows(FileFormatException.class,
                                     () -> EditionReader.readHeader(new ByteArrayInputStream(file))).getError());
  }

  /**
   * A number must be an integer between 1 and the maximum.
   */
  @Test
  @DisplayName("A number must be an integer between 1 and the maximum")
  void numberTest() {
    assertAll(
        () -> assertEquals(MAX, EditionReader.number("3", MAX)),
        () -> assertEquals(FormatError.INVALID_CONTENT,
                           assertThrows(FileFormatException.class, () -> EditionReader.number("4", MAX)).getError()),
        () -> assertEquals(FormatError.INVALID_CONTENT,
                           assertThrows(FileFormatException.class, () -> EditionReader.number("0", MAX)).getError()),
        () -> assertEquals(FormatError.INVALID_CONTENT,
                           assertThrows(FileFormatException.class,
                                        () -> EditionReader.number("99999999999", MAX)).getError()));
  }

  /**
   * Reads a complete edition file from bytes.
   *
   * @param file the bytes of the archive.
   *
   * @return the stored edition.
   *
   * @throws FileFormatException if the file is rejected.
   * @throws IOException never.
   */
  private static StoredEdition read(final byte[] file) throws FileFormatException, IOException {
    return EditionReader.read(new ByteArrayInputStream(file));
  }

  /**
   * Asserts that the complete reading of a file is rejected with an error.
   *
   * @param error the expected error.
   * @param file the bytes of the archive.
   */
  private static void assertError(final FormatError error, final byte[] file) {
    assertEquals(error, assertThrows(FileFormatException.class, () -> read(file)).getError());
  }

  /**
   * Returns the bytes of the document {@code edition.xml} of an edition.
   *
   * @param edition the edition.
   *
   * @return the bytes, in UTF-8.
   */
  private static byte[] xmlBytes(final Edition edition) {
    return Fixtures.xml(edition).getBytes(StandardCharsets.UTF_8);
  }

  /**
   * An edition with an accounting and its documents is read back, the documents being checked as entries only.
   */
  @Test
  @DisplayName("An edition with its accounting and documents is read back")
  void ledgerRoundTripTest() {
    final Edition edition = Fixtures.ledgerEdition();
    final byte[] file = Fixtures.fileWithAttachments(edition);
    assertAll(
        () -> assertEquals(new StoredEdition(Fixtures.ID, edition), read(file)),
        () -> assertEquals(edition.name(), EditionReader.readHeader(new ByteArrayInputStream(file)).name()));
  }

  /**
   * A document referenced by the accounting but missing from the archive is rejected.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("A document missing from the archive is rejected")
  void missingAttachmentTest() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    EditionWriter.write(new StoredEdition(Fixtures.ID, Fixtures.ledgerEdition()), out, List.of(),
                        (name, entry) -> entry.write(1));
    assertError(FormatError.INVALID_CONTENT, out.toByteArray());
  }

  /**
   * Path traversal and unknown kinds: an entry is accepted only as a document named by a digest and a known
   * extension, each once.
   *
   * @param name the name of the entry.
   */
  @ParameterizedTest
  @ValueSource(strings = {"attachments/../evil.pdf", "attachments/ABC.pdf", "attachments/x.pdf", "other/a.pdf",
                          "attachments/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.exe",
                          "attachments/0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.PDF"})
  @DisplayName("An entry that is not a document named by its digest is rejected")
  void unexpectedAttachmentTest(final String name) {
    assertError(FormatError.UNEXPECTED_ENTRY,
                Fixtures.archive(Map.of(EditionXml.ENTRY, xmlBytes(Fixtures.teamEdition()), name, new byte[1])));
  }

  /**
   * A document stored twice under the same name is rejected.
   */
  @Test
  @DisplayName("A document stored twice is rejected")
  void duplicateAttachmentTest() {
    final String first = EditionXml.ATTACHMENTS + "a".repeat(DIGEST_LENGTH) + ".pdf";
    final String second = EditionXml.ATTACHMENTS + "b".repeat(DIGEST_LENGTH) + ".pdf";
    final byte[] file = Fixtures.archive(Map.of(EditionXml.ENTRY, xmlBytes(Fixtures.teamEdition()),
                                                first, new byte[1], second, new byte[1]));
    final String text = new String(file, StandardCharsets.ISO_8859_1).replace(second, first);
    assertError(FormatError.UNEXPECTED_ENTRY, text.getBytes(StandardCharsets.ISO_8859_1));
  }

  /**
   * Too many documents, or a document larger than the maximum once decompressed, are rejected.
   */
  @Test
  @DisplayName("Too many documents, or a too large document, are rejected")
  void tooManyAttachmentsTest() {
    final Map<String, byte[]> many = new HashMap<>();
    many.put(EditionXml.ENTRY, xmlBytes(Fixtures.teamEdition()));
    for (int i = 0; i <= EditionReader.MAX_ATTACHMENTS; i++) {
      many.put(EditionXml.ATTACHMENTS + String.format(Locale.ROOT, "%064x", i) + ".png", new byte[1]);
    }
    final byte[] large = new byte[Attachment.MAX_SIZE + 1];
    assertAll(
        () -> assertError(FormatError.TOO_LARGE, Fixtures.archive(many)),
        () -> assertError(FormatError.TOO_LARGE, Fixtures.archive(Map.of(
            EditionXml.ENTRY, xmlBytes(Fixtures.teamEdition()),
            EditionXml.ATTACHMENTS + "a".repeat(DIGEST_LENGTH) + ".pdf", large))));
  }

  /**
   * An archive damaged among its documents is rejected.
   */
  @Test
  @DisplayName("An archive damaged among its documents is rejected")
  void damagedAttachmentsTest() {
    final byte[] file = Fixtures.archive(Map.of(EditionXml.ENTRY, xmlBytes(Fixtures.teamEdition()),
                                                EditionXml.ATTACHMENTS + "a".repeat(DIGEST_LENGTH) + ".pdf",
                                                new byte[LARGE_ENTRY]));
    final int start = new String(file, StandardCharsets.ISO_8859_1).indexOf(EditionXml.ATTACHMENTS);
    assertError(FormatError.INVALID_ARCHIVE, Arrays.copyOf(file, start + EditionXml.ATTACHMENTS.length()
                                                                 + DIGEST_LENGTH + LOCAL_HEADER));
  }

}
