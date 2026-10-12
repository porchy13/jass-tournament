package ch.varani.jass.persistence;

import java.io.Serial;
import java.util.Objects;

/**
 * Signals that a file is rejected (an edition file {@code .jass}, the directory file {@code directory.jassdir}, or a
 * text file to import), with the {@link FormatError} the user interface
 * translates.
 *
 * <p>The message contains the code of the error only, never the content of the file, so that it is safe for the
 * logs (no personal data). For the same reason, the exception that revealed the problem is not kept as cause: the
 * messages of the XML parsers and validators may quote the values of the file, i.e. names of participants.</p>
 *
 * <p>Immutable once thrown, safe for concurrent use.</p>
 */
public final class FileFormatException extends Exception {

  /** The version of the serialized form. */
  @Serial
  private static final long serialVersionUID = 1L;

  /** The reason why the file is rejected. */
  private final FormatError error;

  /**
   * Creates an exception for a rejected file.
   *
   * @param error the reason why the file is rejected.
   *
   * @throws NullPointerException if the error is {@code null}.
   */
  public FileFormatException(final FormatError error) {
    super(Objects.requireNonNull(error, "error").name());
    this.error = error;
  }

  /**
   * Returns the reason why the file is rejected.
   *
   * @return the error code.
   */
  public FormatError getError() {
    return error;
  }

}
