package ch.varani.jass.domain;

/**
 * The kinds of supporting documents that can be attached to an entry of the accounting (Q7): PDF, PNG and JPEG.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum AttachmentType {

  /** A PDF document. */
  PDF("pdf"),

  /** A PNG image. */
  PNG("png"),

  /** A JPEG image (e.g. a photo of a receipt). */
  JPEG("jpg");

  /** The extension of the files of this kind, without dot. */
  private final String extension;

  /**
   * Creates a kind of document.
   *
   * @param extension the extension of the files, without dot.
   */
  AttachmentType(final String extension) {
    this.extension = extension;
  }

  /**
   * Returns the extension of the files of this kind.
   *
   * @return the extension, without dot, in lower case.
   */
  public String extension() {
    return extension;
  }

}
