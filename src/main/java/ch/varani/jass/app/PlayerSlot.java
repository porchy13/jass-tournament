package ch.varani.jass.app;

import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.Player;

/**
 * A player being registered (F5, F14): its names as typed, and the person of the directory it is linked to, when
 * the user picked one among the proposals of the directory.
 *
 * <p>Typing other names than those of the linked person removes the link: the player is then a new person, added to
 * the directory on registration. The class holds no JavaFX object: it is tested without the toolkit.</p>
 *
 * <p>Not thread-safe: confined to the JavaFX Application Thread.</p>
 */
final class PlayerSlot {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The first name, as typed. */
  private String firstName = "";
  /** The last name, as typed. */
  private String lastName = "";
  /** The linked person of the directory, or {@code null}. */
  private Person person;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates an empty slot: no names, no link to the directory.
   */
  PlayerSlot() {
    // Nothing more to initialize: the attributes have their initial values.
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the first name.
   *
   * @return the first name, as typed.
   */
  String firstName() {
    return firstName;
  }

  /**
   * Returns the last name.
   *
   * @return the last name, as typed.
   */
  String lastName() {
    return lastName;
  }

  /**
   * Returns the linked person of the directory.
   *
   * @return the person, or {@code null} if the player is not linked.
   */
  Person person() {
    return person;
  }

  /**
   * Changes the names; the link to a person is removed if the names are not hers any more.
   *
   * @param first the first name, as typed.
   * @param last the last name, as typed.
   */
  void setNames(final String first, final String last) {
    firstName = first;
    lastName = last;
    if (person != null && !(first.equals(person.firstName()) && last.equals(person.lastName()))) {
      person = null;
    }
  }

  /**
   * Links the player to a person of the directory, taking her names.
   *
   * @param linked the person.
   */
  void link(final Person linked) {
    person = linked;
    firstName = linked.firstName();
    lastName = linked.lastName();
  }

  /**
   * Fills the slot with a registered player: linked to its person when it is still in the directory.
   *
   * @param player the player.
   * @param linked its person of the directory, or {@code null}.
   */
  void show(final Player player, final Person linked) {
    if (linked == null) {
      person = null;
      firstName = player.firstName();
      lastName = player.lastName();
    } else {
      link(linked);
    }
  }

  /**
   * Empties the slot.
   */
  void clear() {
    firstName = "";
    lastName = "";
    person = null;
  }

  /**
   * Tells whether no name is typed.
   *
   * @return {@code true} if both names are blank.
   */
  boolean isBlank() {
    return firstName.isBlank() && lastName.isBlank();
  }

  /**
   * Returns the text searched in the directory: the names as typed.
   *
   * @return the first and last names.
   */
  String query() {
    return firstName + " " + lastName;
  }

}
