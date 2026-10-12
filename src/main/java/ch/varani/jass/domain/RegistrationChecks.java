package ch.varani.jass.domain;

import java.util.List;

/**
 * The checks shared by the implementations of {@link Registrations}: duplicate persons and registration numbers.
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

}
