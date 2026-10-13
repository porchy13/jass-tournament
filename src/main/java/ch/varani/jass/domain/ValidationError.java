package ch.varani.jass.domain;

/**
 * The reasons why the domain rejects a value. The user interface translates each of them into a localized message.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum ValidationError {

  /** The name of an edition is blank. */
  BLANK_EDITION_NAME,

  /** The number of games of an edition is lower than 1. */
  INVALID_GAMES,

  /** The time given to play a game is not between 1 minute and {@link Edition#MAX_GAME_MINUTES} minutes. */
  INVALID_GAME_MINUTES,

  /**
   * The number of deals of a game is not a positive multiple of {@link Regulation#DEALS_PER_BLOCK}, so that the four
   * players at a table would not start the same number of deals.
   */
  INVALID_DEALS,

  /**
   * A name (of an edition or of a player) contains a control character, a lone surrogate or an unassigned code point,
   * which cannot be stored in the edition files.
   */
  INVALID_CHARACTER,

  /** The first name of a player is blank. */
  BLANK_FIRST_NAME,

  /**
   * A team does not have exactly {@link Regulation#PLAYERS_PER_TEAM} players, or a participant of the individual mode
   * is not one player.
   */
  WRONG_TEAM_SIZE,

  /** The same person is registered twice in an edition (same first and last names, accents and case ignored). */
  DUPLICATE_PLAYER,

  /** The maximum number of teams of an edition is lower than 1. */
  INVALID_MAX_TEAMS,

  /**
   * More participants are registered than the maximum of the edition allows ({@link Edition#capacity()}), e.g. when
   * the maximum is lowered once the schedule is computed.
   */
  TOO_MANY_REGISTRATIONS,

  /**
   * A participant cannot be replaced, nor a participant withdraw, at this stage: a result is entered for one of the
   * games of the participant (it is excluded, absent or has a score sheet), or the drawing of lots is done; or a
   * participant withdraws once the schedule is computed (it must then be replaced).
   */
  REPLACEMENT_REFUSED,

  /** No registration has the given number. */
  UNKNOWN_REGISTRATION,

  /** The operation does not match the tournament mode of the edition. */
  WRONG_MODE,

  /** The schedule does not match the number of participants or of games of the edition. */
  SCHEDULE_MISMATCH,

  /** Results: the points typed for a deal are lower than 0 or greater than {@link Regulation#POINTS_PER_DEAL}. */
  INVALID_POINTS,

  /** Results: two score sheets are for the same game. */
  DUPLICATE_SHEET,

  /**
   * Results: the results do not match the edition: results without schedule (e.g. the schedule is removed while
   * results are entered), a sheet for a game that is not in the schedule or whose number of deals differs from the
   * plan of the deals, an excluded or drawn participant that is not registered.
   */
  RESULTS_MISMATCH,

  /** Results: the drawing of lots is done before the results of every game are entered (rule 15). */
  RESULTS_INCOMPLETE,

  /** Results: a participant is twice in the order of a drawing of lots. */
  INVALID_TIE_BREAK,

  /**
   * Accounting: an amount is negative, too large or has more than two decimals, or the amount of an entry is zero.
   */
  INVALID_AMOUNT,

  /** Accounting: the category of an entry is blank. */
  BLANK_CATEGORY,

  /**
   * Accounting: a supporting document is not valid (digest, name or size), or is attached twice to the same entry.
   */
  INVALID_ATTACHMENT,

  /** Accounting: two entries have the same identifier. */
  DUPLICATE_ENTRY,

  /** Accounting: no entry has the given identifier. */
  UNKNOWN_ENTRY,

  /** Directory: an e-mail address does not have a valid form. */
  INVALID_EMAIL,

  /** Directory: a phone number cannot be read, or is not a valid number of its country (Switzerland by default). */
  INVALID_PHONE,

  /** Directory: no person has the given identifier. */
  UNKNOWN_PERSON,

  /** Directory: two persons have the same identifier. */
  DUPLICATE_PERSON_ID,

  /** Directory: a person who is not contactable has no date for the change of its contact status. */
  MISSING_STATUS_DATE,

  /** Directory: a person has two participations in the same edition. */
  DUPLICATE_PARTICIPATION,

  /** Directory import: the text is not a valid table (a quoted cell is not closed). */
  MALFORMED_TABLE,

  /** Directory import: the table has more rows than allowed. */
  TOO_MANY_ROWS

}
