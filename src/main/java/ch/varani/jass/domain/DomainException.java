package ch.varani.jass.domain;

import java.io.Serial;

/**
 * Thrown when the domain rejects a value, e.g. a blank name or a player registered twice.
 *
 * <p>The exception carries a {@link ValidationError}, translated by the user interface. Its message is the name of
 * the error only: it never contains the names of the participants, so that it can be logged safely.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public final class DomainException extends RuntimeException {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The serial version of the class. */
  @Serial
  private static final long serialVersionUID = 1L;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The reason of the rejection. */
  private final ValidationError error;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates an exception.
   *
   * @param error the reason of the rejection.
   */
  public DomainException(final ValidationError error) {
    super(error.name());
    this.error = error;
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the reason of the rejection.
   *
   * @return the reason of the rejection.
   */
  public ValidationError getError() {
    return error;
  }

}
