package ch.varani.jass.update;

import java.io.IOException;
import java.io.Serial;
import java.util.Objects;

/**
 * Signals that the latest release cannot be checked or that its installer cannot be downloaded, with the
 * {@link UpdateError} the user interface translates.
 *
 * <p>The message contains the code of the error only. The exception of the network that revealed the problem, if
 * any, is kept as cause: it holds no data of the user.</p>
 *
 * <p>Immutable once thrown, safe for concurrent use.</p>
 */
public final class UpdateException extends IOException {

  /** The version of the serialized form. */
  @Serial
  private static final long serialVersionUID = 1L;

  /** The reason of the failure. */
  private final UpdateError error;

  /**
   * Creates an exception without cause.
   *
   * @param error the reason of the failure.
   *
   * @throws NullPointerException if the error is {@code null}.
   */
  public UpdateException(final UpdateError error) {
    this(error, null);
  }

  /**
   * Creates an exception caused by another one, e.g. a failure of the network.
   *
   * @param error the reason of the failure.
   * @param cause the exception that revealed the problem, or {@code null}.
   *
   * @throws NullPointerException if the error is {@code null}.
   */
  public UpdateException(final UpdateError error, final Throwable cause) {
    super(Objects.requireNonNull(error, "error").name(), cause);
    this.error = error;
  }

  /**
   * Returns the reason of the failure.
   *
   * @return the error code.
   */
  public UpdateError getError() {
    return error;
  }

}
