package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.TextChecks;
import ch.varani.jass.domain.ValidationError;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A person of the directory: someone who took part in an edition, or may take part in one.
 *
 * <p><b>Content</b>: a stable identifier; the first name (mandatory) and the last name (possibly empty), cleaned and
 * checked as the names of a {@link Player}; the optional contact details, an e-mail address and a phone number;
 * optional notes (one line); the contact status and the date of its last change; the participations in the
 * editions, at most one per edition.</p>
 *
 * <p><b>Privacy</b> ({@code AGENTS.md} §5): the contact details are stored in the directory only, never in the edition
 * files nor on the documents, and never logged. {@link #withoutContactDetails()} erases them for good.</p>
 *
 * <p>Every modification returns a new instance. Immutable, safe for concurrent use.</p>
 *
 * @param id the identifier of the person, stable over time.
 * @param firstName the first name, not blank.
 * @param lastName the last name, possibly empty.
 * @param email the e-mail address, or {@code null}.
 * @param phone the phone number, or {@code null}.
 * @param notes the notes, possibly empty.
 * @param status the contact status.
 * @param statusDate the date of the last change of the contact status; mandatory unless the person is
 *                   {@link ContactStatus#CONTACTABLE}, possibly {@code null} otherwise.
 * @param participations the participations in the editions, at most one per edition.
 */
public record Person(UUID id, String firstName, String lastName, EmailAddress email, PhoneNumber phone, String notes,
                     ContactStatus status, LocalDate statusDate, List<Participation> participations) {

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a person, cleaning and checking its values.
   *
   * @param id the identifier of the person.
   * @param firstName the first name.
   * @param lastName the last name, possibly empty.
   * @param email the e-mail address, or {@code null}.
   * @param phone the phone number, or {@code null}.
   * @param notes the notes, possibly empty.
   * @param status the contact status.
   * @param statusDate the date of the last change of the contact status, or {@code null} if the person is
   *                   contactable.
   * @param participations the participations in the editions.
   *
   * @throws NullPointerException if a mandatory value is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_FIRST_NAME} if the first name is blank, with
   *                         {@link ValidationError#INVALID_CHARACTER} if a name or the notes contain a character that
   *                         is not allowed, with {@link ValidationError#MISSING_STATUS_DATE} if the person is not
   *                         contactable and the status date is {@code null}, or with
   *                         {@link ValidationError#DUPLICATE_PARTICIPATION} if two participations are in the same
   *                         edition.
   */
  public Person {
    Objects.requireNonNull(id, "id");
    final Player name = new Player(firstName, lastName);
    firstName = name.firstName();
    lastName = name.lastName();
    notes = TextChecks.checkCharacters(Objects.requireNonNull(notes, "notes").strip());
    Objects.requireNonNull(status, "status");
    if (status != ContactStatus.CONTACTABLE && statusDate == null) {
      throw new DomainException(ValidationError.MISSING_STATUS_DATE);
    }
    participations = List.copyOf(participations);
    final Set<UUID> editions = new HashSet<>();
    for (final Participation participation : participations) {
      if (!editions.add(participation.edition())) {
        throw new DomainException(ValidationError.DUPLICATE_PARTICIPATION);
      }
    }
  }

  /**
   * Creates a new person with a new identifier, without contact details, notes nor participations, contactable.
   *
   * @param firstName the first name.
   * @param lastName the last name, possibly empty.
   *
   * @return the new person.
   *
   * @throws NullPointerException if a name is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_FIRST_NAME} if the first name is blank, or with
   *                         {@link ValidationError#INVALID_CHARACTER} if a name contains a character that is not
   *                         allowed.
   */
  public static Person create(final String firstName, final String lastName) {
    return new Person(UUID.randomUUID(), firstName, lastName, null, null, "", ContactStatus.CONTACTABLE, null,
                      List.of());
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Returns the person as a player of an edition: a copy of the names, linked to this person.
   *
   * @return the player.
   */
  public Player asPlayer() {
    return new Player(firstName, lastName, id);
  }

  /**
   * Returns the full name of the person, the first name followed by the last name when there is one.
   *
   * @return the full name.
   */
  public String fullName() {
    return asPlayer().fullName();
  }

  /**
   * Tells whether the person may be contacted, i.e. appears in the contact lists and copies.
   *
   * @return {@code true} if the status is {@link ContactStatus#CONTACTABLE}.
   */
  public boolean isContactable() {
    return status == ContactStatus.CONTACTABLE;
  }

  /**
   * Returns a copy of this person with other names, e.g. to correct a spelling.
   *
   * @param newFirstName the new first name.
   * @param newLastName the new last name, possibly empty.
   *
   * @return the new person.
   *
   * @throws DomainException with {@link ValidationError#BLANK_FIRST_NAME} or {@link ValidationError#INVALID_CHARACTER}
   *                         if a name is not valid.
   */
  public Person withNames(final String newFirstName, final String newLastName) {
    return new Person(id, newFirstName, newLastName, email, phone, notes, status, statusDate, participations);
  }

  /**
   * Returns a copy of this person with another e-mail address.
   *
   * @param newEmail the new address, or {@code null} to remove it.
   *
   * @return the new person.
   */
  public Person withEmail(final EmailAddress newEmail) {
    return new Person(id, firstName, lastName, newEmail, phone, notes, status, statusDate, participations);
  }

  /**
   * Returns a copy of this person with another phone number.
   *
   * @param newPhone the new number, or {@code null} to remove it.
   *
   * @return the new person.
   */
  public Person withPhone(final PhoneNumber newPhone) {
    return new Person(id, firstName, lastName, email, newPhone, notes, status, statusDate, participations);
  }

  /**
   * Returns a copy of this person with other notes.
   *
   * @param newNotes the new notes, possibly empty.
   *
   * @return the new person.
   *
   * @throws DomainException with {@link ValidationError#INVALID_CHARACTER} if the notes contain a character that is
   *                         not allowed (e.g. a line break).
   */
  public Person withNotes(final String newNotes) {
    return new Person(id, firstName, lastName, email, phone, newNotes, status, statusDate, participations);
  }

  /**
   * Returns a copy of this person with another contact status.
   *
   * @param newStatus the new status.
   * @param date the date of the change.
   *
   * @return the new person.
   *
   * @throws NullPointerException if the status or the date is {@code null}.
   */
  public Person withStatus(final ContactStatus newStatus, final LocalDate date) {
    return new Person(id, firstName, lastName, email, phone, notes, newStatus, Objects.requireNonNull(date, "date"),
                      participations);
  }

  /**
   * Returns a copy of this person without e-mail address nor phone number: the contact details are erased for good,
   * e.g. when the person asks not to be contacted any more.
   *
   * @return the new person.
   */
  public Person withoutContactDetails() {
    return new Person(id, firstName, lastName, null, null, notes, status, statusDate, participations);
  }

  /**
   * Returns a copy of this person with other participations.
   *
   * @param newParticipations the new participations, at most one per edition.
   *
   * @return the new person.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PARTICIPATION} if two participations are in the same
   *                         edition.
   */
  public Person withParticipations(final List<Participation> newParticipations) {
    return new Person(id, firstName, lastName, email, phone, notes, status, statusDate, newParticipations);
  }

}
