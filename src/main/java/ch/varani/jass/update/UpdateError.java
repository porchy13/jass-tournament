package ch.varani.jass.update;

/**
 * The reasons why an update cannot be downloaded. The user interface translates each of them into a localized
 * message; a failure of the check at start-up is never shown.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum UpdateError {

  /**
   * GitHub cannot be reached, answers with an error, or the connection breaks during the download; the published
   * files of the release do not have the expected form.
   */
  UNAVAILABLE,

  /**
   * The downloaded installer is larger than the maximum size, or its SHA-256 digest is not the one published with the
   * release: it is deleted.
   */
  DAMAGED

}
