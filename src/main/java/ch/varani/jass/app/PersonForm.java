package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.EmailAddress;
import ch.varani.jass.directory.Person;
import ch.varani.jass.directory.PhoneNumber;
import ch.varani.jass.domain.DomainException;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The values of the form of a person of the directory (F14): names, optional e-mail address, phone number and notes,
 * and contact status, as typed; and the person they make.
 *
 * <p>A blank e-mail address or phone number means none. A change of the contact status takes the date of today. The
 * class holds no JavaFX object: it is tested without the toolkit.</p>
 *
 * <p>Not thread-safe: confined to the JavaFX Application Thread.</p>
 */
final class PersonForm {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The first name, as typed. */
  private String firstName = "";
  /** The last name, as typed. */
  private String lastName = "";
  /** The e-mail address, as typed, blank for none. */
  private String email = "";
  /** The phone number, as typed, blank for none. */
  private String phone = "";
  /** The notes, as typed. */
  private String notes = "";
  /** The contact status. */
  private ContactStatus status = ContactStatus.CONTACTABLE;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the empty form of a new person.
   */
  PersonForm() {
    // The fields are initialised empty.
  }

  /**
   * Creates the form of an existing person, filled with its values.
   *
   * @param person the person.
   *
   * @return the form.
   */
  static PersonForm of(final Person person) {
    final PersonForm form = new PersonForm();
    form.firstName = person.firstName();
    form.lastName = person.lastName();
    form.email = person.email() == null ? "" : person.email().value();
    form.phone = person.phone() == null ? "" : person.phone().value();
    form.notes = person.notes();
    form.status = person.status();
    return form;
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
   * Changes the first name.
   *
   * @param value the first name, as typed.
   */
  void setFirstName(final String value) {
    firstName = value;
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
   * Changes the last name.
   *
   * @param value the last name, as typed.
   */
  void setLastName(final String value) {
    lastName = value;
  }

  /**
   * Returns the e-mail address.
   *
   * @return the e-mail address, as typed.
   */
  String email() {
    return email;
  }

  /**
   * Changes the e-mail address.
   *
   * @param value the e-mail address, as typed, blank for none.
   */
  void setEmail(final String value) {
    email = value;
  }

  /**
   * Returns the phone number.
   *
   * @return the phone number, as typed.
   */
  String phone() {
    return phone;
  }

  /**
   * Changes the phone number.
   *
   * @param value the phone number, as typed, blank for none.
   */
  void setPhone(final String value) {
    phone = value;
  }

  /**
   * Returns the notes.
   *
   * @return the notes, as typed.
   */
  String notes() {
    return notes;
  }

  /**
   * Changes the notes.
   *
   * @param value the notes, as typed.
   */
  void setNotes(final String value) {
    notes = value;
  }

  /**
   * Returns the contact status.
   *
   * @return the status.
   */
  ContactStatus status() {
    return status;
  }

  /**
   * Changes the contact status.
   *
   * @param value the status.
   */
  void setStatus(final ContactStatus value) {
    status = value;
  }

  // *******************************************************************************************************************
  // Result
  // *******************************************************************************************************************

  /**
   * Makes the person of the values: a new person, or a new version of an existing one (same identifier and
   * participations); a change of the contact status is dated today.
   *
   * @param base the existing person, or {@code null} for a new person.
   * @param today the date of today.
   *
   * @return the person.
   *
   * @throws DomainException if a value is not valid (blank first name, invalid character, e-mail or phone).
   */
  Person apply(final Person base, final LocalDate today) {
    final Person named = base == null ? Person.create(firstName, lastName) : base.withNames(firstName, lastName);
    final Person person = named.withEmail(email.isBlank() ? null : new EmailAddress(email))
                               .withPhone(phone.isBlank() ? null : new PhoneNumber(phone))
                               .withNotes(notes);
    return person.status() == status ? person : person.withStatus(status, today);
  }

  /**
   * Tells why the values cannot make a person.
   *
   * @param today the date of today.
   *
   * @return the key of the message for the user, or nothing if the values are valid.
   */
  Optional<String> problem(final LocalDate today) {
    try {
      apply(null, today);
      return Optional.empty();
    } catch (final DomainException e) {
      return Optional.of("error.validation." + e.getError().name());
    }
  }

}
