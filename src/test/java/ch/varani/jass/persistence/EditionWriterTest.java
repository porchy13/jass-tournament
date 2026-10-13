package ch.varani.jass.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link EditionWriter}: content of the archive and of the XML document, and write errors.
 */
final class EditionWriterTest {

  /** The number of entries of the archive of {@link Fixtures#ledgerEdition()}: the edition and two documents. */
  private static final int THREE_ENTRIES = 3;

  /**
   * The archive holds {@code edition.xml} only, deflated, an XML document in UTF-8 with the format version, the
   * identifier and the settings first.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("The archive holds edition.xml only, with the version, the identifier and the settings first")
  void archiveTest() throws IOException {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(
        Fixtures.file(Fixtures.scheduledTeamEdition())))) {
      final ZipEntry entry = zip.getNextEntry();
      final String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
      assertAll(
          () -> assertEquals(EditionXml.ENTRY, entry.getName()),
          () -> assertEquals(ZipEntry.DEFLATED, entry.getMethod()),
          () -> assertNull(zip.getNextEntry()),
          () -> assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<edition xmlns=\""
                                          + EditionXml.NAMESPACE + "\" formatVersion=\"2\" id=\"" + Fixtures.ID
                                          + "\">\n  <settings>\n    <name>"), xml));
    }
  }

  /**
   * The special characters of XML are escaped, the other characters are written as is.
   */
  @Test
  @DisplayName("The special characters of XML are escaped")
  void escapeTest() {
    final String xml = Fixtures.xml(Fixtures.teamEdition());
    assertAll(
        () -> assertTrue(xml.contains("Fontaine &amp; Fils &lt;SA&gt;"), xml),
        () -> assertTrue(xml.contains("d'Été &quot;♥&quot;"), xml),
        () -> assertTrue(xml.contains("« hiver »"), xml));
  }

  /**
   * The results hold the typed values only: a derived column, a deal not played and the absence of nobody are not
   * written.
   */
  @Test
  @DisplayName("The results hold the typed values only")
  void resultsTest() {
    final String xml = Fixtures.xml(Fixtures.scoredTeamEdition());
    assertAll(
        () -> assertTrue(xml.contains("""
            <sheet round="1" table="1">
                  <score giver="100"/>
                  <score giver="100" receiver="100"/>
                  <score receiver="0"/>
                  <score/>
            """), xml),
        () -> assertTrue(xml.contains("<sheet round=\"1\" table=\"2\" absence=\"RECEIVER\">"), xml),
        () -> assertTrue(xml.contains("<excluded number=\"5\"/>"), xml),
        () -> assertTrue(xml.contains("<tieBreak seed=\"" + Fixtures.SEED + "\">\n      <lot number=\"2\"/>"),
                         xml),
        () -> assertFalse(Fixtures.xml(Fixtures.scheduledTeamEdition()).contains("<results"), xml));
  }

  /**
   * The accounting holds the fee, the entries with their documents, the description only when not empty; a paid
   * player is marked; the documents are entries of the archive, without compression.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("The accounting and the documents are written")
  void ledgerTest() throws IOException {
    final String xml = Fixtures.xml(Fixtures.ledgerEdition());
    final List<String> entries = new ArrayList<>();
    final List<Integer> methods = new ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(
        Fixtures.fileWithAttachments(Fixtures.ledgerEdition())))) {
      for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        entries.add(entry.getName());
        zip.readAllBytes();
        methods.add(entry.getMethod());
      }
    }
    assertAll(
        () -> assertTrue(xml.contains("<ledger fee=\"30.00\">"), xml),
        () -> assertTrue(xml.contains("category=\"Sponsoring\" amount=\"200.00\">"), xml),
        () -> assertTrue(xml.contains("description=\"Vin « réserve »\" amount=\"120.50\">"), xml),
        () -> assertTrue(xml.contains("type=\"PDF\" name=\"facture.pdf\" size=\""), xml),
        () -> assertTrue(xml.contains("paid=\"true\""), xml),
        () -> assertFalse(Fixtures.xml(Fixtures.teamEdition()).contains("<ledger"), xml),
        () -> assertEquals(EditionXml.ENTRY, entries.getFirst()),
        () -> assertEquals(THREE_ENTRIES, entries.size()),
        () -> assertTrue(entries.subList(1, THREE_ENTRIES).stream()
                                .allMatch(name -> name.startsWith(EditionXml.ATTACHMENTS)), entries.toString()),
        () -> assertTrue(methods.stream().allMatch(method -> method == ZipEntry.DEFLATED)));
  }

  /**
   * An edition whose accounting refers to documents cannot be written without their contents.
   */
  @Test
  @DisplayName("An edition with documents needs their contents")
  void missingContentsTest() {
    final StoredEdition stored = new StoredEdition(Fixtures.ID, Fixtures.ledgerEdition());
    assertThrows(NoSuchFileException.class, () -> EditionWriter.write(stored, new ByteArrayOutputStream()));
  }

  /**
   * A stream that cannot be written is reported as an {@link IOException}.
   */
  @Test
  @DisplayName("A stream that cannot be written is reported")
  void failingStreamTest() {
    final StoredEdition stored = new StoredEdition(Fixtures.ID, Fixtures.teamEdition());
    final OutputStream failing = new OutputStream() {
      @Override
      public void write(final int b) throws IOException {
        throw new IOException("Disk full");
      }
    };
    assertAll(
        () -> assertThrows(IOException.class, () -> EditionWriter.writeXml(stored, failing)),
        () -> assertThrows(IOException.class, () -> EditionWriter.write(stored, failing)));
  }

}
