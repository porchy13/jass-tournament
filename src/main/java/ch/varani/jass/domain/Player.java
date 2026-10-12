package ch.varani.jass.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A player registered in an edition.
 *
 * <p>The names are cleaned at creation: leading and trailing spaces are removed and inner sequences of spaces are
 * reduced to one space. The first name is mandatory; the last name may be empty, because some players only give
 * their first name.</p>
 *
 * <p>The player records whether its registration fee is paid ({@link Ledger#fee()}).</p>
 *
 * <p>A player may refer to a person of the directory by its identifier; the edition keeps a copy of the names, so
 * that it stays readable, printable and rankable without the directory, and never holds the contact details of the
 * person.</p>
 *
 * <p>Two players are the <i>same person</i> ({@link #isSamePersonAs(Player)}) when their names are equal, accents,
 * case and spaces ignored ({@link #hasSameNameAs(Player)}), or when they refer to the same person of the directory;
 * an edition cannot register the same person twice.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param firstName the first name, not blank.
 * @param lastName the last name, possibly empty.
 * @param personId the identifier of the person of the directory, or {@code null} if the player is not linked to it.
 * @param paid whether the player has paid its registration fee.
 */
public record Player(String firstName, String lastName, UUID personId, boolean paid) {

  /** A sequence of white spaces. */
  private static final Pattern SPACES = Pattern.compile("\\s+");
  /** The combining diacritical marks (accents) of a string decomposed in Unicode NFD form. */
  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}");

  /**
   * Creates a player, cleaning its names.
   *
   * @param firstName the first name.
   * @param lastName the last name, possibly empty.
   * @param personId the identifier of the person of the directory, or {@code null}.
   * @param paid whether the player has paid its registration fee.
   *
   * @throws NullPointerException if a name is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_FIRST_NAME} if the first name is blank, or with
   *                         {@link ValidationError#INVALID_CHARACTER} if a name contains a control character or another
   *                         character that is not allowed.
   */
  public Player {
    firstName = TextChecks.checkCharacters(clean(Objects.requireNonNull(firstName, "firstName")));
    lastName = TextChecks.checkCharacters(clean(Objects.requireNonNull(lastName, "lastName")));
    if (firstName.isEmpty()) {
      throw new DomainException(ValidationError.BLANK_FIRST_NAME);
    }
  }

  /**
   * Creates a player who is not linked to a person of the directory, cleaning its names.
   *
   * @param firstName the first name.
   * @param lastName the last name, possibly empty.
   *
   * @throws NullPointerException if a name is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_FIRST_NAME} if the first name is blank, or with
   *                         {@link ValidationError#INVALID_CHARACTER} if a name contains a character that is not
   *                         allowed.
   */
  public Player(final String firstName, final String lastName) {
    this(firstName, lastName, null, false);
  }

  /**
   * Creates a player who has not paid its registration fee yet, cleaning its names.
   *
   * @param firstName the first name.
   * @param lastName the last name, possibly empty.
   * @param personId the identifier of the person of the directory, or {@code null}.
   *
   * @throws NullPointerException if a name is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_FIRST_NAME} if the first name is blank, or with
   *                         {@link ValidationError#INVALID_CHARACTER} if a name contains a character that is not
   *                         allowed.
   */
  public Player(final String firstName, final String lastName, final UUID personId) {
    this(firstName, lastName, personId, false);
  }

  /**
   * Returns a copy of this player linked to a person of the directory.
   *
   * @param newPersonId the identifier of the person, or {@code null} to remove the link.
   *
   * @return the new player.
   */
  public Player withPersonId(final UUID newPersonId) {
    return new Player(firstName, lastName, newPersonId, paid);
  }

  /**
   * Returns a copy of this player with its registration fee paid or not.
   *
   * @param isPaid whether the fee is paid.
   *
   * @return the new player.
   */
  public Player withPaid(final boolean isPaid) {
    return new Player(firstName, lastName, personId, isPaid);
  }

  /**
   * Returns the full name of the player, the first name followed by the last name when there is one.
   *
   * @return the full name.
   */
  public String fullName() {
    return lastName.isEmpty() ? firstName : firstName + " " + lastName;
  }

  /**
   * Tells whether this player and another one are the same person: they have the same names once accents, case and
   * spaces are ignored, or they refer to the same person of the directory.
   *
   * @param other the other player.
   *
   * @return {@code true} if both players are the same person.
   */
  public boolean isSamePersonAs(final Player other) {
    return hasSameNameAs(other) || personId != null && personId.equals(other.personId);
  }

  /**
   * Tells whether this player and another one have the same names once accents, case and spaces are ignored.
   *
   * @param other the other player.
   *
   * @return {@code true} if both players have the same names.
   */
  public boolean hasSameNameAs(final Player other) {
    return comparisonKey(fullName()).equals(comparisonKey(other.fullName()));
  }

  /**
   * Computes the comparison form of a text, to compare or search names: the text decomposed in Unicode NFD form,
   * without diacritical marks, in lower case, without leading and trailing spaces, its inner sequences of spaces
   * reduced to one space.
   *
   * @param text the text.
   *
   * @return the comparison form of the text.
   *
   * @throws NullPointerException if the text is {@code null}.
   */
  public static String comparisonKey(final String text) {
    final String decomposed = Normalizer.normalize(clean(text), Normalizer.Form.NFD);
    return DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
  }

  /**
   * Removes the leading and trailing spaces of a name and reduces its inner sequences of spaces to one space.
   *
   * @param name the name to clean.
   *
   * @return the cleaned name.
   */
  private static String clean(final String name) {
    return SPACES.matcher(name.strip()).replaceAll(" ");
  }

}
