package ch.varani.jass.domain;

/**
 * The points of one deal written on a score sheet, as typed by the user: column A for the giver side (the side at the
 * odd place, which deals first, rule 6), column B for the receiver side.
 *
 * <p><b>Raw input only</b>: a value is {@code null} when it was not typed. The user types column A; column B is then
 * <i>derived</i>, {@link Regulation#POINTS_PER_DEAL} minus A (rule 4), until the user types it to copy the paper sheet
 * (a <i>manual</i> value). Symmetrically, a column A left empty while B is typed is derived from B. A deal where no
 * value is typed is <i>not played</i> (interrupted game, rule 2) and counts 0 for both sides.</p>
 *
 * <p>Each typed value is between 0 and {@link Regulation#POINTS_PER_DEAL} (rule 4: no side can get more than the
 * points of a deal). The sum of both columns may differ from {@link Regulation#POINTS_PER_DEAL} (arithmetic error of
 * the players, kept as written): such a deal is not {@link #isConsistent() consistent} and is highlighted, never
 * rejected.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param giver the points typed in column A, or {@code null}.
 * @param receiver the points typed in column B, or {@code null}.
 */
public record DealScore(Integer giver, Integer receiver) {

  /** A deal that is not played: no value typed. */
  public static final DealScore NOT_PLAYED = new DealScore(null, null);

  /**
   * Creates the points of a deal, checking the typed values.
   *
   * @param giver the points typed in column A, or {@code null}.
   * @param receiver the points typed in column B, or {@code null}.
   *
   * @throws DomainException with {@link ValidationError#INVALID_POINTS} if a typed value is lower than 0 or greater
   *                         than {@link Regulation#POINTS_PER_DEAL}.
   */
  public DealScore {
    if (!isValid(giver) || !isValid(receiver)) {
      throw new DomainException(ValidationError.INVALID_POINTS);
    }
  }

  /**
   * Creates the points of a deal where only column A is typed, column B being derived.
   *
   * @param giver the points of column A.
   *
   * @return the deal.
   *
   * @throws DomainException with {@link ValidationError#INVALID_POINTS} if the value is out of range.
   */
  public static DealScore of(final int giver) {
    return new DealScore(giver, null);
  }

  /**
   * Tells whether a typed value is valid.
   *
   * @param value the value, or {@code null} if not typed.
   *
   * @return {@code true} if the value is not typed or between 0 and {@link Regulation#POINTS_PER_DEAL}.
   */
  private static boolean isValid(final Integer value) {
    return value == null || value >= 0 && value <= Regulation.POINTS_PER_DEAL;
  }

  /**
   * Tells whether the deal was played, i.e. at least one value is typed.
   *
   * @return {@code true} if the deal was played.
   */
  public boolean isPlayed() {
    return giver != null || receiver != null;
  }

  /**
   * Tells whether column A is derived from column B.
   *
   * @return {@code true} if A is not typed and B is.
   */
  public boolean isGiverDerived() {
    return giver == null && receiver != null;
  }

  /**
   * Tells whether column B is derived from column A.
   *
   * @return {@code true} if B is not typed and A is.
   */
  public boolean isReceiverDerived() {
    return receiver == null && giver != null;
  }

  /**
   * Returns the points of the giver side: the typed value, the value derived from column B, or 0 if not played.
   *
   * @return the points of column A.
   */
  public int giverPoints() {
    return points(giver, receiver);
  }

  /**
   * Returns the points of the receiver side: the typed value, the value derived from column A, or 0 if not played.
   *
   * @return the points of column B.
   */
  public int receiverPoints() {
    return points(receiver, giver);
  }

  /**
   * Returns the points of a column.
   *
   * @param own the value typed in the column, or {@code null}.
   * @param other the value typed in the other column, or {@code null}.
   *
   * @return the typed value, else {@link Regulation#POINTS_PER_DEAL} minus the other value, else 0.
   */
  private static int points(final Integer own, final Integer other) {
    if (own != null) {
      return own;
    }
    return other == null ? 0 : Regulation.POINTS_PER_DEAL - other;
  }

  /**
   * Tells whether the two columns sum to the points of a deal (rule 4); a deal not played is consistent.
   *
   * @return {@code true} if the deal is not played or its columns sum to {@link Regulation#POINTS_PER_DEAL}.
   */
  public boolean isConsistent() {
    return !isPlayed() || giverPoints() + receiverPoints() == Regulation.POINTS_PER_DEAL;
  }

}
