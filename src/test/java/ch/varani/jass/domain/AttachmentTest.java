package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link Attachment} and {@link AttachmentType}: digest, name, size and stored name of a document.
 */
final class AttachmentTest {

  /** A digest of 64 hexadecimal digits. */
  static final String DIGEST = "0123456789abcdef".repeat(4);
  /** The size of a fictitious document. */
  private static final long SIZE = 1_234L;

  /**
   * The stored name is the digest followed by the extension of the kind of document; the name is stripped.
   */
  @Test
  @DisplayName("A document is stored under its digest and the extension of its kind")
  void fileNameTest() {
    assertAll(
        () -> assertEquals(DIGEST + ".pdf", new Attachment(DIGEST, AttachmentType.PDF, "a.pdf", SIZE).fileName()),
        () -> assertEquals(DIGEST + ".png", new Attachment(DIGEST, AttachmentType.PNG, "a", SIZE).fileName()),
        () -> assertEquals(DIGEST + ".jpg", new Attachment(DIGEST, AttachmentType.JPEG, "a", SIZE).fileName()),
        () -> assertEquals("Facture.pdf", new Attachment(DIGEST, AttachmentType.PDF, " Facture.pdf ", SIZE).name()));
  }

  /**
   * A digest that is not 64 hexadecimal digits in lower case, a blank name or a size out of range is rejected.
   */
  @Test
  @DisplayName("An invalid digest, name or size is rejected")
  void invalidTest() {
    final long tooLarge = Attachment.MAX_SIZE + 1L;
    assertAll(
        () -> assertInvalid(() -> new Attachment(DIGEST.toUpperCase(Locale.ROOT), AttachmentType.PDF, "a",
                                                 SIZE)),
        () -> assertInvalid(() -> new Attachment(DIGEST.substring(1), AttachmentType.PDF, "a", SIZE)),
        () -> assertInvalid(() -> new Attachment(DIGEST, AttachmentType.PDF, " ", SIZE)),
        () -> assertInvalid(() -> new Attachment(DIGEST, AttachmentType.PDF, "a", 0)),
        () -> assertInvalid(() -> new Attachment(DIGEST, AttachmentType.PDF, "a", tooLarge)),
        () -> assertEquals(Attachment.MAX_SIZE,
                           new Attachment(DIGEST, AttachmentType.PDF, "a", Attachment.MAX_SIZE).size()),
        () -> assertEquals(ValidationError.INVALID_CHARACTER,
                           assertThrows(DomainException.class,
                                        () -> new Attachment(DIGEST, AttachmentType.PDF, "a\u0007", SIZE)).getError()),
        () -> assertThrows(NullPointerException.class, () -> new Attachment(null, AttachmentType.PDF, "a", SIZE)),
        () -> assertThrows(NullPointerException.class, () -> new Attachment(DIGEST, null, "a", SIZE)),
        () -> assertThrows(NullPointerException.class, () -> new Attachment(DIGEST, AttachmentType.PDF, null, SIZE)));
  }

  /**
   * Asserts that the creation of a document is rejected as invalid.
   *
   * @param creation the creation.
   */
  private static void assertInvalid(final Executable creation) {
    assertEquals(ValidationError.INVALID_ATTACHMENT, assertThrows(DomainException.class, creation).getError());
  }

}
