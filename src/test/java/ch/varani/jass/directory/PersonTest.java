package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Person}: creation, cleaning and validation of the values, contact status, erasure of the contact
 * details, participations and link to the players of the editions.
 */
final class PersonTest {

  /**
   * A new person has a new identifier, cleaned names, no contact details, no notes, no participations, and is
   * contactable.
   */
  @Test
  @DisplayName("A new person is contactable, without contact details nor participations")
  void createTest() {
    final Person person = Person.create("  Anne  Marie ", " Fontaine ");
    assertAll(
        () -> assertEquals("Anne Marie", person.firstName()),
        () -> assertEquals("Fontaine", person.lastName()),
        () -> assertEquals("Anne Marie Fontaine", person.fullName()),
        () -> assertNull(person.email()),
        () -> assertNull(person.phone()),
        () -> assertEquals("", person.notes()),
        () -> assertEquals(ContactStatus.CONTACTABLE, person.status()),
        () -> assertNull(person.statusDate()),
        () -> assertTrue(person.isContactable()),
        () -> assertEquals(List.of(), person.participations()),
        () -> assertNotEquals(person.id(), Person.create("Anne Marie", "Fontaine").id()));
  }

  /**
   * The names follow the rules of the players: the first name is mandatory, no control character.
   */
  @Test
  @DisplayName("The names follow the rules of the players")
  void namesTest() {
    final Person person = Person.create("Anne", "Fontaine");
    assertAll(
        () -> assertEquals("Zoé", person.withNames("Zoé", "").firstName()),
        () -> assertEquals(person.id(), person.withNames("Zoé", "").id()),
        () -> assertEquals(ValidationError.BLANK_FIRST_NAME,
                           assertThrows(DomainException.class, () -> person.withNames(" ", "Fontaine")).getError()),
        () -> assertEquals(ValidationError.INVALID_CHARACTER,
                           assertThrows(DomainException.class, () -> person.withNames("A\u0007", "F")).getError()),
        () -> assertThrows(NullPointerException.class, () -> Person.create(null, "Fontaine")));
  }

  /**
   * The person is linked to the players registered from it: same names, its identifier.
   */
  @Test
  @DisplayName("The player of a person copies its names and refers to it")
  void asPlayerTest() {
    final Person person = Person.create("Anne", "Fontaine");
    assertEquals(new Player("Anne", "Fontaine", person.id()), person.asPlayer());
  }

  /**
   * The contact details and the notes can be set and removed; the notes are stripped and are one line only.
   */
  @Test
  @DisplayName("The contact details and the notes can be changed; the notes are one line")
  void contactDetailsTest() {
    final Person person = People.person("Anne", "Fontaine", "anne@example.ch", "079 000 00 01");
    assertAll(
        () -> assertEquals("anne@example.ch", person.email().value()),
        () -> assertEquals("+41790000001", person.phone().value()),
        () -> assertNull(person.withEmail(null).email()),
        () -> assertNull(person.withPhone(null).phone()),
        () -> assertEquals("Captain", person.withNotes("  Captain ").notes()),
        () -> assertEquals(ValidationError.INVALID_CHARACTER,
                           assertThrows(DomainException.class, () -> person.withNotes("a\nb")).getError()),
        () -> assertThrows(NullPointerException.class, () -> person.withNotes(null)));
  }

  /**
   * Erasing the contact details removes the e-mail address and the phone number for good, and keeps the rest.
   */
  @Test
  @DisplayName("Erasing the contact details removes the e-mail address and the phone number")
  void withoutContactDetailsTest() {
    final Person person = People.person("Anne", "Fontaine", "anne@example.ch", "079 000 00 01").withNotes("Note");
    final Person erased = person.withoutContactDetails();
    assertAll(
        () -> assertNull(erased.email()),
        () -> assertNull(erased.phone()),
        () -> assertEquals("Note", erased.notes()),
        () -> assertEquals(person.id(), erased.id()));
  }

  /**
   * A person who is not contactable has the date of the change of its status; a contactable person may have one.
   */
  @Test
  @DisplayName("A person who is not contactable has the date of the change of its status")
  void statusTest() {
    final Person person = Person.create("Anne", "Fontaine");
    final Person blocked = person.withStatus(ContactStatus.DO_NOT_CONTACT, People.DATE);
    assertAll(
        () -> assertFalse(blocked.isContactable()),
        () -> assertEquals(People.DATE, blocked.statusDate()),
        () -> assertFalse(person.withStatus(ContactStatus.DECEASED, People.DATE).isContactable()),
        () -> assertTrue(blocked.withStatus(ContactStatus.CONTACTABLE, People.DATE).isContactable()),
        () -> assertThrows(NullPointerException.class, () -> person.withStatus(ContactStatus.DECEASED, null)),
        () -> assertThrows(NullPointerException.class, () -> person.withStatus(null, People.DATE)),
        () -> assertEquals(ValidationError.MISSING_STATUS_DATE,
                           assertThrows(DomainException.class,
                                        () -> new Person(person.id(), "Anne", "", null, null, "",
                                                         ContactStatus.DECEASED, null, List.of())).getError()));
  }

  /**
   * A person has at most one participation per edition, and does not share the list it was created with.
   */
  @Test
  @DisplayName("A person has at most one participation per edition")
  void participationsTest() {
    final Person person = Person.create("Anne", "Fontaine");
    final Participation first = new Participation(People.EDITION, "Match", People.DATE, null);
    final Participation again = new Participation(People.EDITION, "Match", People.DATE, People.OTHER);
    final Participation other = new Participation(People.OTHER_EDITION, "Match", People.DATE, null);
    final List<Participation> list = new ArrayList<>(List.of(first, other));
    final Person withTwo = person.withParticipations(list);
    list.clear();
    assertAll(
        () -> assertEquals(List.of(first, other), withTwo.participations()),
        () -> assertEquals(ValidationError.DUPLICATE_PARTICIPATION,
                           assertThrows(DomainException.class,
                                        () -> person.withParticipations(List.of(first, again))).getError()),
        () -> assertThrows(NullPointerException.class, () -> person.withParticipations(null)));
  }

  /**
   * The identifier and the status are mandatory.
   */
  @Test
  @DisplayName("The identifier and the status are mandatory")
  void mandatoryTest() {
    final UUID id = UUID.randomUUID();
    assertAll(
        () -> assertThrows(NullPointerException.class,
                           () -> new Person(null, "Anne", "", null, null, "", ContactStatus.CONTACTABLE, null,
                                            List.of())),
        () -> assertThrows(NullPointerException.class,
                           () -> new Person(id, "Anne", "", null, null, "", null, null, List.of())));
  }

}
