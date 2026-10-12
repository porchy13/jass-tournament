package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.EmailAddress;
import ch.varani.jass.directory.Person;
import ch.varani.jass.directory.PhoneNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests of {@link PersonForm}: values of a person of the directory and the person they make. The persons are
 * fictitious.
 */
final class PersonFormTest {

  /** The date of today in the tests. */
  private static final LocalDate TODAY = LocalDate.of(2026, Month.NOVEMBER, 20);

  /**
   * A new person takes the typed values; a blank e-mail or phone means none.
   */
  @Test
  @DisplayName("A new person takes the typed values, blank contact details meaning none")
  void newPersonTest() {
    final PersonForm form = new PersonForm();
    form.setFirstName(" Anne ");
    form.setLastName("Fontaine");
    form.setEmail("Anne@Example.ch");
    form.setPhone(" ");
    form.setNotes("Capitaine");
    final Person person = form.apply(null, TODAY);
    assertAll(
        () -> assertEquals("Anne", person.firstName()),
        () -> assertEquals("anne@example.ch", person.email().value()),
        () -> assertNull(person.phone()),
        () -> assertEquals("Capitaine", person.notes()),
        () -> assertEquals(ContactStatus.CONTACTABLE, person.status()),
        () -> assertEquals(Optional.empty(), form.problem(TODAY)));
  }

  /**
   * An existing person keeps its identifier; a change of status is dated today; an unchanged status keeps its date.
   */
  @Test
  @DisplayName("An edited person keeps its identifier; a new status is dated today")
  void editTest() {
    final Person known = Person.create("Zoé", "Müller").withPhone(new PhoneNumber("079 000 00 01"))
                               .withStatus(ContactStatus.DO_NOT_CONTACT, TODAY.minusYears(1));
    final PersonForm same = PersonForm.of(known);
    final PersonForm changed = PersonForm.of(known);
    changed.setStatus(ContactStatus.DECEASED);
    assertAll(
        () -> assertEquals("Zoé", same.firstName()),
        () -> assertEquals("Müller", same.lastName()),
        () -> assertEquals("", same.email()),
        () -> assertEquals("+41790000001", same.phone()),
        () -> assertEquals("", same.notes()),
        () -> assertEquals(ContactStatus.DO_NOT_CONTACT, same.status()),
        () -> assertEquals(known, same.apply(known, TODAY)),
        () -> assertEquals(TODAY, changed.apply(known, TODAY).statusDate()),
        () -> assertEquals(known.id(), changed.apply(known, TODAY).id()),
        () -> assertEquals("anne@example.ch", PersonForm.of(known.withEmail(new EmailAddress("anne@example.ch")))
                                                        .email()));
  }

  /**
   * An invalid value is reported by the key of its message.
   */
  @Test
  @DisplayName("An invalid value is reported")
  void problemTest() {
    final PersonForm form = new PersonForm();
    final Optional<String> blank = form.problem(TODAY);
    form.setFirstName("Anne");
    form.setEmail("anne");
    assertAll(
        () -> assertEquals(Optional.of("error.validation.BLANK_FIRST_NAME"), blank),
        () -> assertEquals(Optional.of("error.validation.INVALID_EMAIL"), form.problem(TODAY)));
  }

}
