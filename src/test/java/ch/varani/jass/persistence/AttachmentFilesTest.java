package ch.varani.jass.persistence;

import ch.varani.jass.domain.AttachmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link AttachmentFiles}: kind of a document by its first bytes, SHA-256 digest.
 */
final class AttachmentFilesTest {

  /** The first bytes of a PNG image. */
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};

  /**
   * The kind of a document is recognised by its first bytes, never by its name; other contents are not documents.
   */
  @Test
  @DisplayName("A document is recognised by its first bytes")
  void typeTest() {
    assertAll(
        () -> assertEquals(Optional.of(AttachmentType.PDF), AttachmentFiles.typeOf(Fixtures.PDF)),
        () -> assertEquals(Optional.of(AttachmentType.PNG), AttachmentFiles.typeOf(PNG)),
        () -> assertEquals(Optional.of(AttachmentType.JPEG), AttachmentFiles.typeOf(Fixtures.JPEG)),
        () -> assertEquals(Optional.empty(), AttachmentFiles.typeOf(new byte[0])),
        () -> assertEquals(Optional.empty(), AttachmentFiles.typeOf(new byte[] {'%', 'P'})),
        () -> assertEquals(Optional.empty(), AttachmentFiles.typeOf("MZ executable".getBytes(StandardCharsets.UTF_8))));
  }

  /**
   * The digest is the SHA-256 of the content in lower-case hexadecimal; an unknown algorithm is a programming error.
   */
  @Test
  @DisplayName("The digest is the SHA-256 of the content")
  void digestTest() {
    assertAll(
        () -> assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                           AttachmentFiles.sha256("abc".getBytes(StandardCharsets.US_ASCII))),
        () -> assertThrows(IllegalStateException.class, () -> AttachmentFiles.digest("NO-SUCH-ALGORITHM")));
  }

}
