package ch.varani.jass.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A supporting document attached to an entry of the accounting (an invoice, a receipt), identified by the SHA-256
 * digest of its content.
 *
 * <p>The edition holds the reference only; the content is stored in the edition file, as an entry named after
 * {@link #fileName()}, so that the file stays self-contained and the same document attached twice is stored once.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param sha256 the SHA-256 digest of the content, 64 hexadecimal digits in lower case.
 * @param type the kind of document.
 * @param name the name of the original file, shown to the user, not blank.
 * @param size the size of the content in bytes, between 1 and {@link #MAX_SIZE}.
 */
public record Attachment(String sha256, AttachmentType type, String name, long size) {

  /** The maximum size of a document, 20 MiB (Q7). */
  public static final int MAX_SIZE = 20 * 1024 * 1024;
  /** The form of a SHA-256 digest. */
  private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

  /**
   * Creates a reference to a document, checking it.
   *
   * @param sha256 the SHA-256 digest of the content.
   * @param type the kind of document.
   * @param name the name of the original file.
   * @param size the size of the content in bytes.
   *
   * @throws NullPointerException if a value is {@code null}.
   * @throws DomainException with {@link ValidationError#INVALID_ATTACHMENT} if the digest is not 64 hexadecimal digits
   *                         in lower case, the name is blank or the size is out of range, or with
   *                         {@link ValidationError#INVALID_CHARACTER} if the name contains a character that is not
   *                         allowed.
   */
  public Attachment {
    Objects.requireNonNull(sha256, "sha256");
    Objects.requireNonNull(type, "type");
    name = TextChecks.checkCharacters(Objects.requireNonNull(name, "name").strip());
    if (!DIGEST.matcher(sha256).matches() || name.isEmpty() || size < 1 || size > MAX_SIZE) {
      throw new DomainException(ValidationError.INVALID_ATTACHMENT);
    }
  }

  /**
   * Returns the name of the stored content: the digest followed by the extension of the kind of document.
   *
   * @return e.g. {@code 3a7b….pdf}.
   */
  public String fileName() {
    return sha256 + "." + type.extension();
  }

}
