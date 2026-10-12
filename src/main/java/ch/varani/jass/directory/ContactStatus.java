package ch.varani.jass.directory;

/**
 * Whether a person of the directory may be contacted, as told by the person or their relatives to the organisers and
 * set by the user. There is no recording of consent.
 *
 * <p>The constants are ordered from the least to the most restrictive; a merge of two persons keeps the most
 * restrictive status ({@link Directory#merge(java.util.UUID, java.util.UUID)}).</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum ContactStatus {

  /** The person may be contacted, and is proposed during the registration. */
  CONTACTABLE,

  /**
   * The person asked not to be contacted any more: excluded from every contact list and copy, still proposed during
   * the registration.
   */
  DO_NOT_CONTACT,

  /** The person died: excluded from every contact list and copy, and no longer proposed during the registration. */
  DECEASED

}
