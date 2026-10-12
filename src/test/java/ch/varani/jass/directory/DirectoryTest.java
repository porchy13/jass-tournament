package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Directory}: persons, search, duplicates, merge, participations and contact lists. The persons are
 * fictitious.
 */
final class DirectoryTest {

  /** The identifier of the latest fictitious edition. */
  private static final UUID LATEST = UUID.fromString("2d8e5b30-8c5f-4e4d-9f90-4b3c7d0ebf23");

  /** A fictitious person. */
  private final Person anne = People.person("Anne", "Fontaine", "anne@example.ch", "079 000 00 01");
  /** A fictitious person. */
  private final Person zoe = People.person("Zoé", "Müller");
  /** A fictitious person. */
  private final Person leo = People.person("Léo", "Berger");
  /** A directory of the three persons. */
  private final Directory directory = Directory.empty().add(anne).add(zoe).add(leo);

  /**
   * The persons are found by identifier; an unknown identifier is reported.
   */
  @Test
  @DisplayName("A person is found by its identifier")
  void getTest() {
    assertAll(
        () -> assertEquals(anne, directory.get(anne.id())),
        () -> assertEquals(Optional.empty(), directory.find(People.OTHER)),
        () -> assertEquals(ValidationError.UNKNOWN_PERSON,
                           assertThrows(DomainException.class, () -> directory.get(People.OTHER)).getError()));
  }

  /**
   * Two persons cannot have the same identifier; the directory does not share the list it was created with.
   */
  @Test
  @DisplayName("Two persons cannot have the same identifier")
  void duplicateIdTest() {
    final Person sameId = anne.withNotes("Other");
    assertAll(
        () -> assertEquals(ValidationError.DUPLICATE_PERSON_ID,
                           assertThrows(DomainException.class, () -> directory.add(sameId))
                               .getError()),
        () -> assertThrows(UnsupportedOperationException.class, () -> directory.persons().clear()),
        () -> assertEquals(List.of(), Directory.empty().persons()));
  }

  /**
   * A person is replaced by its new version; an unknown person cannot be replaced.
   */
  @Test
  @DisplayName("A person is replaced by its new version")
  void replaceTest() {
    final Person renamed = anne.withNames("Anne-Marie", "Fontaine");
    final Person unknown = People.person("Eve", "Rochat");
    assertAll(
        () -> assertEquals(renamed, directory.replace(renamed).get(anne.id())),
        () -> assertEquals(directory.persons().size(), directory.replace(renamed).persons().size()),
        () -> assertEquals(ValidationError.UNKNOWN_PERSON,
                           assertThrows(DomainException.class, () -> directory.replace(unknown)).getError()));
  }

  /**
   * A removed person is gone with its contact details, and is no longer the team mate of anybody.
   */
  @Test
  @DisplayName("A removed person is gone and is no longer a team mate")
  void removeTest() {
    final Directory played = directory.recordParticipations(People.EDITION, teamEdition(anne, zoe));
    final Directory removed = played.remove(anne.id());
    assertAll(
        () -> assertEquals(Optional.empty(), removed.find(anne.id())),
        () -> assertEquals(anne.id(), played.get(zoe.id()).participations().get(0).teamMate()),
        () -> assertEquals(null, removed.get(zoe.id()).participations().get(0).teamMate()),
        () -> assertEquals(ValidationError.UNKNOWN_PERSON,
                           assertThrows(DomainException.class, () -> directory.remove(People.OTHER)).getError()));
  }

  /**
   * The search ignores accents, case and spaces, needs every word of the query, and sorts by last name.
   */
  @Test
  @DisplayName("The search ignores accents and case, needs every word, and sorts by last name")
  void searchTest() {
    assertAll(
        () -> assertEquals(List.of(leo, anne, zoe), directory.search("  ")),
        () -> assertEquals(List.of(zoe), directory.search("ZOE mull")),
        () -> assertEquals(List.of(leo, zoe), directory.search("er")),
        () -> assertEquals(List.of(), directory.search("zoe berger")),
        () -> assertEquals(List.of(leo), directory.search("léo")));
  }

  /**
   * The persons are sorted by last name, then first name, then identifier.
   */
  @Test
  @DisplayName("Homonyms are sorted by first name, then identifier")
  void orderTest() {
    final Person first = People.person("Anne", "Fontaine");
    final Person second = People.person("Anne", "Fontaine");
    final Person bea = People.person("Béa", "Fontaine");
    final Directory homonyms = Directory.empty().add(bea).add(second).add(first);
    final List<Person> expected = first.id().compareTo(second.id()) < 0
                                  ? List.of(first, second, bea) : List.of(second, first, bea);
    assertEquals(expected, homonyms.search(""));
  }

  /**
   * The deceased persons are not proposed during the registration; the persons not to be contacted are.
   */
  @Test
  @DisplayName("The deceased persons are not proposed during the registration")
  void registrationCandidatesTest() {
    final Directory changed = directory.replace(zoe.withStatus(ContactStatus.DECEASED, People.DATE))
                                       .replace(leo.withStatus(ContactStatus.DO_NOT_CONTACT, People.DATE));
    assertEquals(List.of(changed.get(leo.id()), anne), changed.registrationCandidates(""));
  }

  /**
   * A duplicate has the same names (accents, case and spaces ignored), the same e-mail address or the same phone
   * number; a person is not its own duplicate.
   */
  @Test
  @DisplayName("A duplicate has the same names, e-mail address or phone number")
  void duplicatesTest() {
    final Person sameName = People.person(" anne ", "FONTAINE");
    final Person sameEmail = People.person("Eve", "Rochat").withEmail(new EmailAddress("ANNE@example.ch"));
    final Person samePhone = People.person("Eve", "Rochat").withPhone(new PhoneNumber("+41 79 000 00 01"));
    final Person nobody = People.person("Eve", "Rochat", "eve@example.ch", "079 000 00 02");
    assertAll(
        () -> assertEquals(List.of(anne), directory.duplicatesOf(sameName)),
        () -> assertEquals(List.of(anne), directory.duplicatesOf(sameEmail)),
        () -> assertEquals(List.of(anne), directory.duplicatesOf(samePhone)),
        () -> assertEquals(List.of(), directory.duplicatesOf(nobody)),
        () -> assertEquals(List.of(), directory.duplicatesOf(anne)),
        () -> assertEquals(List.of(), directory.duplicatesOf(People.person("Eve", "Rochat"))));
  }

  /**
   * A merge keeps the names of the kept person, completes its contact details, joins the notes and the
   * participations, keeps the most restrictive status, and redirects the team mates.
   */
  @Test
  @DisplayName("A merge completes the kept person and redirects the team mates")
  void mergeTest() {
    final Person removed = People.person("Anne", "Fontaine-Favre", "other@example.ch", "079 000 00 03")
                                 .withNotes("Left-handed")
                                 .withStatus(ContactStatus.DO_NOT_CONTACT, People.DATE);
    final Person kept = zoe.withNotes("Captain");
    final Directory before = Directory.empty().add(kept).add(removed).add(leo)
                                      .recordParticipations(People.EDITION, teamEdition(removed, leo))
                                      .recordParticipations(People.OTHER_EDITION, teamEdition(kept, leo));
    final Directory after = before.merge(kept.id(), removed.id());
    final Person merged = after.get(kept.id());
    assertAll(
        () -> assertEquals(2, after.persons().size()),
        () -> assertEquals("Zoé", merged.firstName()),
        () -> assertEquals("other@example.ch", merged.email().value()),
        () -> assertEquals("+41790000003", merged.phone().value()),
        () -> assertEquals("Captain" + Directory.NOTES_SEPARATOR + "Left-handed", merged.notes()),
        () -> assertEquals(ContactStatus.DO_NOT_CONTACT, merged.status()),
        () -> assertEquals(People.DATE, merged.statusDate()),
        () -> assertEquals(2, merged.participations().size()),
        () -> assertTrue(after.get(leo.id()).participations().stream()
                              .allMatch(participation -> kept.id().equals(participation.teamMate()))));
  }

  /**
   * A merge keeps the values of the kept person when it has them, and its participation in an edition where both
   * persons took part.
   */
  @Test
  @DisplayName("A merge keeps the values of the kept person")
  void mergeKeepsTest() {
    final Person removed = People.person("Anne", "F.", "other@example.ch", "079 000 00 03").withNotes("Note");
    final Person kept = anne.withNotes("Note").withStatus(ContactStatus.DECEASED, People.DATE);
    final Directory before = Directory.empty().add(kept).add(removed).add(leo)
                                      .recordParticipations(People.EDITION, teamEdition(kept, removed));
    final Person merged = before.merge(kept.id(), removed.id()).get(kept.id());
    assertAll(
        () -> assertEquals(anne.email(), merged.email()),
        () -> assertEquals(anne.phone(), merged.phone()),
        () -> assertEquals("Note", merged.notes()),
        () -> assertEquals(ContactStatus.DECEASED, merged.status()),
        () -> assertEquals(List.of(new Participation(People.EDITION, "Match", People.DATE, removed.id())),
                           before.get(kept.id()).participations()),
        () -> assertEquals(1, merged.participations().size()));
  }

  /**
   * The notes of the removed person are taken over when the kept person has none; a person cannot be merged with
   * itself nor with an unknown person.
   */
  @Test
  @DisplayName("A merge takes the notes of the removed person and needs two persons")
  void mergeNotesTest() {
    final Directory before = directory.replace(zoe.withNotes("Note"));
    assertAll(
        () -> assertEquals("Note", before.merge(anne.id(), zoe.id()).get(anne.id()).notes()),
        () -> assertEquals("", directory.merge(anne.id(), zoe.id()).get(anne.id()).notes()),
        () -> assertThrows(IllegalArgumentException.class, () -> directory.merge(anne.id(), anne.id())),
        () -> assertEquals(ValidationError.UNKNOWN_PERSON,
                           assertThrows(DomainException.class, () -> directory.merge(anne.id(), People.OTHER))
                               .getError()));
  }

  /**
   * In team mode, the participations record the edition and the team mate linked to the directory; recording again
   * updates them and removes the persons no longer registered; the players not linked are ignored.
   */
  @Test
  @DisplayName("The participations of a team edition are recorded with the team mates")
  void recordTeamTest() {
    final Edition edition = teamEdition(anne, zoe).withRegistrations(
        teamEdition(anne, zoe).teamRegistrations().add(new Team(leo.asPlayer(), new Player("Eve", "Rochat"))));
    final Directory recorded = directory.recordParticipations(People.EDITION, edition);
    final Edition renamed = edition.withName("Renamed").withRegistrations(
        TeamRegistrations.empty().add(new Team(anne.asPlayer(), new Player("Eve", "Rochat"))));
    final Directory again = recorded.recordParticipations(People.EDITION, renamed);
    assertAll(
        () -> assertEquals(List.of(new Participation(People.EDITION, "Match", People.DATE, zoe.id())),
                           recorded.get(anne.id()).participations()),
        () -> assertEquals(List.of(new Participation(People.EDITION, "Match", People.DATE, null)),
                           recorded.get(leo.id()).participations()),
        () -> assertEquals(List.of(new Participation(People.EDITION, "Renamed", People.DATE, null)),
                           again.get(anne.id()).participations()),
        () -> assertEquals(List.of(), again.get(zoe.id()).participations()),
        () -> assertEquals(List.of(anne, leo, zoe).stream().map(Person::id).sorted().toList(),
                           recorded.participantsOf(People.EDITION).stream().map(Person::id).sorted().toList()),
        () -> assertEquals(List.of(), recorded.participantsOf(People.OTHER_EDITION)));
  }

  /**
   * In individual mode, the participations have no team mate; the participations in other editions are kept; a
   * player linked to a deleted person is ignored.
   */
  @Test
  @DisplayName("The participations of an individual edition have no team mate")
  void recordIndividualTest() {
    final Edition edition = Edition.create("Individual", People.DATE, TournamentMode.INDIVIDUAL)
                                   .withRegistrations(new IndividualRegistrations(List.of(
                                       anne.asPlayer(), new Player("Ghost", "Gone", People.OTHER))));
    final Directory recorded = directory.recordParticipations(People.OTHER_EDITION, teamEdition(anne, zoe))
                                        .recordParticipations(People.EDITION, edition);
    assertAll(
        () -> assertEquals(2, recorded.get(anne.id()).participations().size()),
        () -> assertEquals(new Participation(People.EDITION, "Individual", People.DATE, null),
                           recorded.get(anne.id()).participations().get(1)),
        () -> assertEquals(1, recorded.get(zoe.id()).participations().size()));
  }

  /**
   * The last team mate is the one of the most recent participation in team mode with a team mate in the directory,
   * if not deceased.
   */
  @Test
  @DisplayName("The last team mate is proposed if still in the directory and alive")
  void lastTeamMateTest() {
    final Edition older = teamEdition(anne, leo).withDate(People.DATE.minusYears(1));
    final Edition individual = Edition.create("Individual", People.DATE.plusYears(1), TournamentMode.INDIVIDUAL)
                                      .withRegistrations(new IndividualRegistrations(List.of(anne.asPlayer())));
    final Directory recorded = directory.recordParticipations(People.OTHER_EDITION, older)
                                        .recordParticipations(People.EDITION, teamEdition(anne, zoe))
                                        .recordParticipations(LATEST, individual);
    assertAll(
        () -> assertEquals(Optional.of(zoe.id()), recorded.lastTeamMate(anne.id()).map(Person::id)),
        () -> assertEquals(Optional.of(anne.id()), recorded.lastTeamMate(leo.id()).map(Person::id)),
        () -> assertEquals(Optional.empty(),
                           recorded.replace(recorded.get(zoe.id()).withStatus(ContactStatus.DECEASED, People.DATE))
                                   .lastTeamMate(anne.id())),
        () -> assertEquals(Optional.of(leo.id()), recorded.remove(zoe.id()).lastTeamMate(anne.id()).map(Person::id)),
        () -> assertEquals(Optional.empty(), directory.lastTeamMate(anne.id())));
  }

  /**
   * The contact lists leave out the persons who are not contactable or have no contact detail, and the repeated
   * details.
   */
  @Test
  @DisplayName("The contact lists leave out the persons not to be contacted")
  void contactsTest() {
    final Person blocked = People.person("Eve", "Rochat", "eve@example.ch", "079 000 00 02")
                                 .withStatus(ContactStatus.DO_NOT_CONTACT, People.DATE);
    final Person dead = People.person("Max", "Rochat", "max@example.ch", "079 000 00 04")
                              .withStatus(ContactStatus.DECEASED, People.DATE);
    final Person twin = People.person("Ann", "Fontaine", "anne@example.ch", "079 000 00 01");
    final List<Person> selection = List.of(anne, zoe, blocked, dead, twin);
    assertAll(
        () -> assertEquals(List.of("anne@example.ch"), Directory.emailsOf(selection)),
        () -> assertEquals(List.of("+41790000001"), Directory.phonesOf(selection)));
  }

  /**
   * Creates a team edition named "Match" with one team of two persons.
   *
   * @param first a person.
   * @param second the team mate.
   *
   * @return the edition.
   */
  private static Edition teamEdition(final Person first, final Person second) {
    return Edition.create("Match", People.DATE, TournamentMode.TEAM)
                  .withRegistrations(TeamRegistrations.empty().add(new Team(first.asPlayer(), second.asPlayer())));
  }

}
