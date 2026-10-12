package ch.varani.jass.domain;

/**
 * The checks of the texts typed by the user (names of the editions, of the players and of the persons of the
 * directory, notes): they must contain only
 * characters that can be stored in the edition files and printed on the documents.
 *
 * <p>A text is rejected when it contains a control character (e.g. {@code U+0001}, which XML 1.0 cannot represent),
 * a surrogate that is not part of a pair, or an unassigned code point (e.g. the non-characters {@code U+FFFE} and
 * {@code U+FFFF}). Letters with accents, symbols, emoji and their joiners are accepted.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class TextChecks {

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private TextChecks() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Checks that a text contains only allowed characters.
   *
   * @param text the text to check.
   *
   * @return the text, unchanged.
   *
   * @throws DomainException with {@link ValidationError#INVALID_CHARACTER} if the text contains a character that is
   *                         not allowed.
   */
  public static String checkCharacters(final String text) {
    if (!text.codePoints().allMatch(TextChecks::isAllowed)) {
      throw new DomainException(ValidationError.INVALID_CHARACTER);
    }
    return text;
  }

  /**
   * Tells whether a code point is allowed, i.e. is not a control character, a lone surrogate or an unassigned code
   * point.
   *
   * @param codePoint the code point.
   *
   * @return {@code true} if the code point is allowed.
   */
  static boolean isAllowed(final int codePoint) {
    return switch (Character.getType(codePoint)) {
      case Character.CONTROL, Character.SURROGATE, Character.UNASSIGNED -> false;
      default -> true;
    };
  }

}
