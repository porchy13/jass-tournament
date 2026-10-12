package ch.varani.jass.domain;

/**
 * The kind of an entry of the accounting of an edition.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum EntryType {

  /** Money received (e.g. sponsoring, sale of drinks); the registration fees are counted apart. */
  INCOME,

  /** Money spent (e.g. an invoice for the prizes or the room). */
  EXPENSE

}
