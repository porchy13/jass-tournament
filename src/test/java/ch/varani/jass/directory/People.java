package ch.varani.jass.directory;

import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

/**
 * Fictitious persons and identifiers shared by the tests of the directory (no real person, {@code AGENTS.md} §5).
 */
final class People {

  /** The identifier of a fictitious edition. */
  static final UUID EDITION = UUID.fromString("0b6c3f1e-6a3d-4c2b-9d7e-2f1a5b8c9d01");
  /** The identifier of another fictitious edition. */
  static final UUID OTHER_EDITION = UUID.fromString("1c7d4a2f-7b4e-4d3c-8e8f-3a2b6c9dae12");
  /** The identifier of a fictitious person. */
  static final UUID OTHER = UUID.fromString("5d0c8a52-2f5e-4b47-9d3c-1a2b3c4d5e6f");
  /** The date of the fictitious edition. */
  static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);

  /**
   * Prevents the instantiation of this utility class.
   */
  private People() {
  }

  /**
   * Returns a new fictitious person, without contact details.
   *
   * @param firstName the first name.
   * @param lastName the last name.
   *
   * @return the person.
   */
  static Person person(final String firstName, final String lastName) {
    return Person.create(firstName, lastName);
  }

  /**
   * Returns a new fictitious person with an e-mail address and a phone number.
   *
   * @param firstName the first name.
   * @param lastName the last name.
   * @param email the e-mail address.
   * @param phone the phone number.
   *
   * @return the person.
   */
  static Person person(final String firstName, final String lastName, final String email, final String phone) {
    return Person.create(firstName, lastName).withEmail(new EmailAddress(email)).withPhone(new PhoneNumber(phone));
  }

}
