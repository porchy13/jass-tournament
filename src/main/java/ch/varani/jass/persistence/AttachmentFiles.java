package ch.varani.jass.persistence;

import ch.varani.jass.domain.AttachmentType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The checks of the supporting documents of the accounting: their kind, recognised by the first bytes of their content
 * (magic bytes), never by the extension of their name; and their SHA-256 digest, which names them in the edition
 * files and detects any damage.
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class AttachmentFiles {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The algorithm of the digests. */
  static final String ALGORITHM = "SHA-256";
  /** The first bytes of a PDF document: {@code %PDF-}. */
  private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
  /** The first bytes of a PNG image (PNG signature). */
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  /** The first bytes of a JPEG image (start of image marker, then the start of another marker). */
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private AttachmentFiles() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Recognises the kind of a document from its first bytes.
   *
   * @param content the content of the document.
   *
   * @return the kind, or nothing if the content is not a PDF, PNG or JPEG document.
   */
  static Optional<AttachmentType> typeOf(final byte[] content) {
    if (startsWith(content, PDF)) {
      return Optional.of(AttachmentType.PDF);
    }
    if (startsWith(content, PNG)) {
      return Optional.of(AttachmentType.PNG);
    }
    return startsWith(content, JPEG) ? Optional.of(AttachmentType.JPEG) : Optional.empty();
  }

  /**
   * Tells whether a content starts with given bytes.
   *
   * @param content the content.
   * @param prefix the expected first bytes.
   *
   * @return {@code true} if the content starts with the prefix.
   */
  private static boolean startsWith(final byte[] content, final byte[] prefix) {
    return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
  }

  /**
   * Computes the SHA-256 digest of a content.
   *
   * @param content the content.
   *
   * @return the digest, 64 hexadecimal digits in lower case.
   */
  static String sha256(final byte[] content) {
    return HexFormat.of().formatHex(digest(ALGORITHM).digest(content));
  }

  /**
   * Returns a message digest of the JDK.
   *
   * @param algorithm the name of the algorithm.
   *
   * @return a new message digest.
   *
   * @throws IllegalStateException if the algorithm is not available (SHA-256 is required in every JDK).
   */
  static MessageDigest digest(final String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("The digest algorithm " + algorithm + " is not available.", e);
    }
  }

}
