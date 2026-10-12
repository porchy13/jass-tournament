package ch.varani.jass.persistence;

/**
 * The reasons why a file is rejected: a file of the application (an edition file {@code .jass}, or the directory file
 * {@code directory.jassdir}), or a text file to import. The user interface translates each of them into a
 * localized message.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum FormatError {

  /**
   * The file is not an archive of the expected kind (ZIP for an edition, GZIP for the directory), or the archive is
   * damaged or truncated.
   */
  INVALID_ARCHIVE,

  /** The archive does not start with the {@code edition.xml} entry: it is not an edition file. */
  MISSING_EDITION,

  /** The archive contains an entry that an edition file never contains (e.g. a path such as {@code ../x}). */
  UNEXPECTED_ENTRY,

  /**
   * The {@code edition.xml} entry, or the content of the directory, exceeds the maximum size once decompressed, or a
   * text file to import exceeds its maximum size.
   */
  TOO_LARGE,

  /**
   * The XML document ({@code edition.xml}, or the content of the directory) is not well-formed XML encoded in UTF-8,
   * contains a document type declaration, or does not follow the XML schema of its file.
   */
  INVALID_XML,

  /** A file to attach to an entry of the accounting is not a PDF, PNG or JPEG document, or is empty. */
  UNSUPPORTED_ATTACHMENT,

  /**
   * A supporting document stored in an edition file does not match its digest: the file has been damaged or
   * modified.
   */
  DAMAGED_ATTACHMENT,

  /** The image chosen as logo of the application is not a PNG or JPEG image that can be read. */
  UNSUPPORTED_IMAGE,

  /** A text file to import (e.g. a CSV file) is not encoded in UTF-8. */
  INVALID_ENCODING,

  /** The file was written in a format version that this version of the application does not know. */
  UNSUPPORTED_VERSION,

  /**
   * The content is well-formed but not valid: a value that the domain model rejects, a number out of range, or an
   * identifier that does not match the file, two persons with the same identifier…
   */
  INVALID_CONTENT

}
