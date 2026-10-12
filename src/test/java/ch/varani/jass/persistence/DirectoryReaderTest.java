package ch.varani.jass.persistence;

import ch.varani.jass.directory.Directory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link DirectoryReader} and {@link DirectoryWriter}: round trip, content of the file, and rejection of
 * the damaged, unexpected or malicious files (decompression bomb, XXE, other encodings, newer versions).
 */
final class DirectoryReaderTest {

  /** A byte that breaks the compressed data. */
  private static final byte INVALID_BYTE = (byte) 0xFF;
  /** The size of the header of a GZIP stream: the compressed data starts after it. */
  private static final int GZIP_HEADER = 10;

  /**
   * A directory read back is equal to the written one.
   */
  @Test
  @DisplayName("A directory read back equals the written one")
  void roundTripTest() {
    assertAll(
        () -> assertEquals(Fixtures.directory(), read(Fixtures.directoryFile(Fixtures.directory()))),
        () -> assertEquals(Directory.empty(), read(Fixtures.directoryFile(Directory.empty()))));
  }

  /**
   * The file is a GZIP stream holding an indented XML document in UTF-8; the optional values are written only when
   * present; the special characters are escaped.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("The file is compressed XML; optional values are written only when present")
  void contentTest() throws IOException {
    final String xml;
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(
        Fixtures.directoryFile(Fixtures.directory())))) {
      xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertAll(
        () -> assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<directory xmlns=\""
                                        + DirectoryXml.NAMESPACE + "\" formatVersion=\"1\">\n  <person "), xml),
        () -> assertTrue(xml.contains("lastName=\"Fontaine &amp; Fils &lt;SA&gt;\""), xml),
        () -> assertTrue(xml.contains("phone=\"+41790000001\""), xml),
        () -> assertTrue(xml.contains("firstName=\"Léo\" lastName=\"Berger\" status=\"CONTACTABLE\">"), xml),
        () -> assertFalse(xml.contains("notes=\"\""), xml));
  }

  /**
   * A stream that cannot be written is reported as an {@link IOException}.
   */
  @Test
  @DisplayName("A stream that cannot be written is reported")
  void failingStreamTest() {
    final OutputStream failing = new OutputStream() {
      @Override
      public void write(final int b) throws IOException {
        throw new IOException("Disk full");
      }
    };
    assertAll(
        () -> assertThrows(IOException.class, () -> DirectoryWriter.writeXml(Fixtures.directory(), failing)),
        () -> assertThrows(IOException.class, () -> DirectoryWriter.write(Fixtures.directory(), failing)));
  }

  /**
   * A file that is not GZIP, or that is truncated or damaged, is rejected.
   */
  @Test
  @DisplayName("A file that is not GZIP, truncated or damaged is rejected")
  void invalidArchiveTest() {
    final byte[] file = Fixtures.directoryFile(Fixtures.directory());
    final byte[] damaged = file.clone();
    for (int i = GZIP_HEADER; i < damaged.length / 2; i++) {
      damaged[i] = INVALID_BYTE;
    }
    assertAll(
        () -> assertError(FormatError.INVALID_ARCHIVE, "Not GZIP".getBytes(StandardCharsets.UTF_8)),
        () -> assertError(FormatError.INVALID_ARCHIVE, Arrays.copyOf(file, file.length / 2)),
        () -> assertError(FormatError.INVALID_ARCHIVE, damaged));
  }

  /**
   * Decompression bomb: a content larger than the maximum once decompressed is rejected.
   */
  @Test
  @DisplayName("Decompression bomb: an oversized content is rejected")
  void tooLargeTest() {
    final byte[] huge = new byte[DirectoryReader.MAX_DIRECTORY_SIZE + 1];
    Arrays.fill(huge, (byte) ' ');
    assertError(FormatError.TOO_LARGE, Fixtures.gzip(huge));
  }

  /**
   * A newer format version is reported as such, before the schema.
   */
  @Test
  @DisplayName("A newer format version is reported")
  void unsupportedVersionTest() {
    final String xml = Fixtures.directoryXml(Fixtures.directory()).replace("formatVersion=\"1\"",
                                                                          "formatVersion=\"2\" extra=\"x\"");
    assertError(FormatError.UNSUPPORTED_VERSION, gzip(xml));
  }

  /**
   * Provides documents that are not valid XML or do not follow the schema.
   *
   * @return the documents.
   */
  static Stream<Arguments> invalidDocuments() {
    final String xml = Fixtures.directoryXml(Fixtures.directory());
    return Stream.of(
        Arguments.of("XXE", xml.replace("?>", "?>\n<!DOCTYPE directory [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>")
                               .replace("firstName=\"Anne\"", "firstName=\"&xxe;\"")),
        Arguments.of("other encoding", xml.replace("UTF-8", "ISO-8859-1")),
        Arguments.of("not well-formed", xml.replace("</directory>", "")),
        Arguments.of("other root", xml.replace("<directory ", "<edition ")),
        Arguments.of("missing version", xml.replace(" formatVersion=\"1\"", "")),
        Arguments.of("unknown element", xml.replace("</directory>", "<extra/></directory>")),
        Arguments.of("unknown status", xml.replace("\"DECEASED\"", "\"MISSING\"")),
        Arguments.of("invalid identifier", xml.replaceFirst("id=\"[^\"]*\"", "id=\"1\"")),
        Arguments.of("date with time zone", xml.replaceFirst("statusDate=\"([^\"]*)\"", "statusDate=\"$1Z\"")),
        Arguments.of("other namespace", xml.replace(DirectoryXml.NAMESPACE, "urn:other")));
  }

  /**
   * A document that is not well-formed, not in UTF-8, with a document type declaration, or that does not follow the
   * schema is rejected.
   *
   * @param description the description of the defect.
   * @param xml the document.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidDocuments")
  @DisplayName("A document that does not follow the schema is rejected")
  void invalidXmlTest(final String description, final String xml) {
    assertError(FormatError.INVALID_XML, gzip(xml));
  }

  /**
   * Provides documents that follow the schema but whose content is not valid.
   *
   * @return the documents.
   */
  static Stream<Arguments> invalidContents() {
    final String xml = Fixtures.directoryXml(Fixtures.directory());
    return Stream.of(
        Arguments.of("blank first name", xml.replace("firstName=\"Léo\"", "firstName=\" \"")),
        Arguments.of("invalid e-mail", xml.replace("anne@example.ch", "anne")),
        Arguments.of("invalid phone", xml.replace("+41790000001", "+41 12")),
        Arguments.of("control character", xml.replace("firstName=\"Léo\"", "firstName=\"L&#x7F;\"")),
        Arguments.of("deceased without date", xml.replaceFirst(" statusDate=\"[^\"]*\"", "")),
        Arguments.of("same identifier twice", xml.replaceAll("<person id=\"[^\"]*\"",
                                                             "<person id=\"" + Fixtures.ID + "\"")),
        Arguments.of("two participations in an edition",
                     xml.replaceFirst("(<participation [^>]*/>)", "$1$1")));
  }

  /**
   * A document whose values the directory rejects is rejected.
   *
   * @param description the description of the defect.
   * @param xml the document.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidContents")
  @DisplayName("A document with invalid values is rejected")
  void invalidContentTest(final String description, final String xml) {
    assertError(FormatError.INVALID_CONTENT, gzip(xml));
  }

  /**
   * Reads a directory file from bytes.
   *
   * @param file the bytes of the compressed file.
   *
   * @return the directory.
   *
   * @throws FileFormatException if the file is rejected.
   * @throws IOException never.
   */
  private static Directory read(final byte[] file) throws FileFormatException, IOException {
    return DirectoryReader.read(new ByteArrayInputStream(file));
  }

  /**
   * Asserts that the reading of a file is rejected with an error.
   *
   * @param error the expected error.
   * @param file the bytes of the compressed file.
   */
  private static void assertError(final FormatError error, final byte[] file) {
    assertEquals(error, assertThrows(FileFormatException.class, () -> read(file)).getError());
  }

  /**
   * Compresses a document as a directory file.
   *
   * @param xml the document.
   *
   * @return the bytes of the compressed file.
   */
  private static byte[] gzip(final String xml) {
    return Fixtures.gzip(xml.getBytes(StandardCharsets.UTF_8));
  }

}
