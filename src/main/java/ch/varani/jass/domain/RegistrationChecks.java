package ch.varani.jass.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The checks and list operations shared by the implementations of {@link Registrations}: duplicate persons,
 * registration numbers, copies of the lists with an element inserted, removed or replaced.
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class RegistrationChecks {

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private RegistrationChecks() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Checks that no person is registered twice.
   *
   * @param players the registered players.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PLAYER} if two players are the same person.
   */
  static void checkNoDuplicate(final List<Player> players) {
    for (int i = 0; i < players.size(); i++) {
      for (int j = i + 1; j < players.size(); j++) {
        if (players.get(i).isSamePersonAs(players.get(j))) {
          throw new DomainException(ValidationError.DUPLICATE_PLAYER);
        }
      }
    }
  }

  /**
   * Converts a registration number into an index of a list, checking that it exists.
   *
   * @param number the registration number, starting at 1.
   * @param size the number of registrations.
   *
   * @return the index, {@code number - 1}.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no registration has this number.
   */
  static int indexOf(final int number, final int size) {
    if (number < 1 || number > size) {
      throw new DomainException(ValidationError.UNKNOWN_REGISTRATION);
    }
    return number - 1;
  }

  /**
   * Returns the player of a participant of the individual mode.
   *
   * @param players the players of the participant.
   *
   * @return the only player.
   *
   * @throws DomainException with {@link ValidationError#WRONG_TEAM_SIZE} if the list does not hold one player.
   */
  static Player single(final List<Player> players) {
    if (players.size() != 1) {
      throw new DomainException(ValidationError.WRONG_TEAM_SIZE);
    }
    return players.getFirst();
  }

  /**
   * Returns a copy of a list with an element inserted.
   *
   * @param <T> the type of the elements.
   * @param list the list.
   * @param index the index of the new element, from 0 to the size of the list.
   * @param element the new element.
   *
   * @return the new list.
   */
  static <T> List<T> inserted(final List<T> list, final int index, final T element) {
    final List<T> copy = new ArrayList<>(list);
    copy.add(index, element);
    return copy;
  }

  /**
   * Returns a copy of a list without one of its elements.
   *
   * @param <T> the type of the elements.
   * @param list the list.
   * @param number the number of the element to remove, starting at 1.
   *
   * @return the new list.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the list has no such element.
   */
  static <T> List<T> removed(final List<T> list, final int number) {
    final List<T> copy = new ArrayList<>(list);
    copy.remove(indexOf(number, list.size()));
    return copy;
  }

  /**
   * Returns a copy of a list with one of its elements replaced.
   *
   * @param <T> the type of the elements.
   * @param list the list.
   * @param number the number of the element to replace, starting at 1.
   * @param element the new element.
   *
   * @return the new list.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if the list has no such element.
   */
  static <T> List<T> replaced(final List<T> list, final int number, final T element) {
    final List<T> copy = new ArrayList<>(list);
    copy.set(indexOf(number, list.size()), element);
    return copy;
  }

}
