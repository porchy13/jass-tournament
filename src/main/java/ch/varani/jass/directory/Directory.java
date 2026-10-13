package ch.varani.jass.directory;

import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.ValidationError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The directory (<i>annuaire</i>) of the persons who took part in the editions, kept across the editions in a file of
 * its own, to register them again and to contact them.
 *
 * <p><b>Operations</b>:</p>
 * <ul>
 *   <li>persons: {@link #add(Person)}, {@link #addAll(Collection)}, {@link #replace(Person)}, {@link #remove(UUID)}
 *       (the person and its contact details are gone for good; the participations of the others no longer refer to
 *       it as team mate), {@link #get(UUID)}, {@link #find(UUID)};</li>
 *   <li>search ({@link #search(String)}): every word of the query must appear in the full name, accents, case and
 *       spaces ignored; {@link #registrationCandidates(String)} leaves out the deceased persons;</li>
 *   <li>duplicates ({@link #duplicatesOf(Person)}): the other persons with the same names (accents, case and spaces
 *       ignored), the same e-mail address or the same phone number; {@link #merge(UUID, UUID)} merges two persons;</li>
 *   <li>participations: {@link #recordParticipations(UUID, Edition)} records the players of an edition linked to the
 *       directory, {@link #participantsOf(UUID)} lists them, {@link #lastTeamMate(UUID)} proposes the previous team
 *       mate of a person;</li>
 *   <li>contact ({@link #emailsOf(Collection)}, {@link #phonesOf(Collection)}): the e-mail addresses or phone numbers
 *       of a selection, without the persons who are not contactable.</li>
 * </ul>
 *
 * <p>The persons are listed sorted by last name, then first name (comparison form, accents and case ignored).</p>
 *
 * <p>Every modification returns a new instance. Immutable, safe for concurrent use.</p>
 *
 * @param persons the persons, with distinct identifiers.
 */
public record Directory(List<Person> persons) {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The order of the lists: last name, then first name, in their comparison form, then identifier. */
  private static final Comparator<Person> ORDER =
      Comparator.comparing((final Person person) -> Player.comparisonKey(person.lastName()))
                .thenComparing(person -> Player.comparisonKey(person.firstName()))
                .thenComparing(Person::id);
  /** The separator of the notes of two merged persons. */
  static final String NOTES_SEPARATOR = " / ";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a directory, checking that the identifiers of the persons are distinct.
   *
   * @param persons the persons.
   *
   * @throws NullPointerException if the list or a person is {@code null}.
   * @throws DomainException with {@link ValidationError#DUPLICATE_PERSON_ID} if two persons have the same identifier.
   */
  public Directory {
    persons = List.copyOf(persons);
    final Set<UUID> ids = new HashSet<>();
    for (final Person person : persons) {
      if (!ids.add(person.id())) {
        throw new DomainException(ValidationError.DUPLICATE_PERSON_ID);
      }
    }
  }

  /**
   * Returns an empty directory, the content of the directory at the first start of the application.
   *
   * @return the empty directory.
   */
  public static Directory empty() {
    return new Directory(List.of());
  }

  // *******************************************************************************************************************
  // Persons
  // *******************************************************************************************************************

  /**
   * Returns a person.
   *
   * @param id the identifier of the person.
   *
   * @return the person.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_PERSON} if no person has this identifier.
   */
  public Person get(final UUID id) {
    return find(id).orElseThrow(() -> new DomainException(ValidationError.UNKNOWN_PERSON));
  }

  /**
   * Looks for a person.
   *
   * @param id the identifier of the person.
   *
   * @return the person, or nothing if no person has this identifier (e.g. a player of an edition whose person has
   *         been deleted from the directory).
   */
  public Optional<Person> find(final UUID id) {
    return persons.stream().filter(person -> person.id().equals(id)).findFirst();
  }

  /**
   * Returns a copy of this directory with one more person.
   *
   * @param person the new person.
   *
   * @return the new directory.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PERSON_ID} if a person has the same identifier.
   */
  public Directory add(final Person person) {
    return addAll(List.of(person));
  }

  /**
   * Returns a copy of this directory with more persons, e.g. the valid rows of an import.
   *
   * @param newPersons the new persons.
   *
   * @return the new directory.
   *
   * @throws DomainException with {@link ValidationError#DUPLICATE_PERSON_ID} if two persons have the same identifier.
   */
  public Directory addAll(final Collection<Person> newPersons) {
    final List<Person> all = new ArrayList<>(persons);
    all.addAll(newPersons);
    return new Directory(all);
  }

  /**
   * Returns a copy of this directory where a person is replaced by a new version of it (same identifier).
   *
   * @param person the new version of the person.
   *
   * @return the new directory.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_PERSON} if no person has its identifier.
   */
  public Directory replace(final Person person) {
    get(person.id());
    return map(current -> current.id().equals(person.id()) ? person : current);
  }

  /**
   * Returns a copy of this directory without a person: its names, contact details, notes and participations are gone;
   * the participations of the other persons no longer refer to it as team mate. The editions keep the names of the
   * player.
   *
   * @param id the identifier of the person.
   *
   * @return the new directory.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_PERSON} if no person has this identifier.
   */
  public Directory remove(final UUID id) {
    get(id);
    final List<Person> others = persons.stream().filter(person -> !person.id().equals(id)).toList();
    return new Directory(others).replaceTeamMate(id, null);
  }

  // *******************************************************************************************************************
  // Search and duplicates
  // *******************************************************************************************************************

  /**
   * Searches the persons whose full name contains every word of a query, accents, case and spaces ignored, e.g. for
   * an incremental search while typing.
   *
   * @param query the words searched, possibly blank to list every person.
   *
   * @return the matching persons, sorted.
   */
  public List<Person> search(final String query) {
    final List<String> words = Arrays.stream(Player.comparisonKey(query).split(" "))
                                     .filter(word -> !word.isEmpty())
                                     .toList();
    return persons.stream()
                  .filter(person -> words.stream().allMatch(Player.comparisonKey(person.fullName())::contains))
                  .sorted(ORDER)
                  .toList();
  }

  /**
   * Searches the persons that can be registered in an edition: as {@link #search(String)}, without the deceased
   * persons.
   *
   * @param query the words searched, possibly blank.
   *
   * @return the matching persons, sorted.
   */
  public List<Person> registrationCandidates(final String query) {
    return search(query).stream().filter(person -> person.status() != ContactStatus.DECEASED).toList();
  }

  /**
   * Looks for the persons that may be the same as a given one, e.g. before adding it: the other persons with the same
   * names (accents, case and spaces ignored), the same e-mail address or the same phone number.
   *
   * @param candidate the person, in the directory or not.
   *
   * @return the possible duplicates, sorted, without the candidate itself.
   */
  public List<Person> duplicatesOf(final Person candidate) {
    return persons.stream()
                  .filter(person -> !person.id().equals(candidate.id()) && areDuplicates(person, candidate))
                  .sorted(ORDER)
                  .toList();
  }

  /**
   * Tells whether two persons may be the same: same names (accents, case and spaces ignored), same e-mail address or
   * same phone number.
   *
   * @param first a person.
   * @param second another person.
   *
   * @return {@code true} if the two persons may be the same.
   */
  static boolean areDuplicates(final Person first, final Person second) {
    return first.asPlayer().hasSameNameAs(second.asPlayer())
           || first.email() != null && first.email().equals(second.email())
           || first.phone() != null && first.phone().equals(second.phone());
  }

  /**
   * Merges two persons that are the same: the kept person takes over the participations of the removed one, its
   * missing e-mail address or phone number, its notes (joined with {@link #NOTES_SEPARATOR}) and its contact status
   * when it is more restrictive; the removed person is deleted and the participations of the others refer to the
   * kept one as team mate. The names of the kept person are not changed.
   *
   * <p>A participation of the removed person in an edition where the kept one already has a participation is
   * dropped.</p>
   *
   * @param keptId the identifier of the person kept.
   * @param removedId the identifier of the person merged into the kept one.
   *
   * @return the new directory.
   *
   * @throws IllegalArgumentException if both identifiers are the same.
   * @throws DomainException with {@link ValidationError#UNKNOWN_PERSON} if a person does not exist.
   */
  public Directory merge(final UUID keptId, final UUID removedId) {
    if (keptId.equals(removedId)) {
      throw new IllegalArgumentException("A person cannot be merged with itself.");
    }
    final Person kept = get(keptId);
    final Person removed = get(removedId);
    final Person merged = mergeStatus(kept, removed)
        .withEmail(kept.email() != null ? kept.email() : removed.email())
        .withPhone(kept.phone() != null ? kept.phone() : removed.phone())
        .withNotes(mergeNotes(kept.notes(), removed.notes()))
        .withParticipations(mergeParticipations(kept.participations(), removed.participations()));
    return replaceTeamMate(removedId, keptId).remove(removedId).replace(merged);
  }

  /**
   * Returns the kept person with the most restrictive contact status of the two persons, and the date of that status.
   *
   * @param kept the person kept.
   * @param removed the person merged into the kept one.
   *
   * @return the kept person, possibly with the status of the removed one.
   */
  private static Person mergeStatus(final Person kept, final Person removed) {
    if (removed.status().compareTo(kept.status()) > 0) {
      return kept.withStatus(removed.status(), removed.statusDate());
    }
    return kept;
  }

  /**
   * Joins the notes of two merged persons: the non-empty ones, once each.
   *
   * @param kept the notes of the person kept.
   * @param removed the notes of the person merged into the kept one.
   *
   * @return the joined notes.
   */
  private static String mergeNotes(final String kept, final String removed) {
    if (removed.isEmpty() || removed.equals(kept)) {
      return kept;
    }
    return kept.isEmpty() ? removed : kept + NOTES_SEPARATOR + removed;
  }

  /**
   * Joins the participations of two merged persons, keeping the one of the kept person when both took part in the
   * same edition.
   *
   * @param kept the participations of the person kept.
   * @param removed the participations of the person merged into the kept one.
   *
   * @return the joined participations.
   */
  private static List<Participation> mergeParticipations(final List<Participation> kept,
                                                         final List<Participation> removed) {
    final List<Participation> all = new ArrayList<>(kept);
    final Set<UUID> editions = new HashSet<>(kept.stream().map(Participation::edition).toList());
    removed.stream().filter(participation -> !editions.contains(participation.edition())).forEach(all::add);
    return all;
  }

  // *******************************************************************************************************************
  // Participations
  // *******************************************************************************************************************

  /**
   * Records the participations of the players of an edition, e.g. each time the edition is saved: every person of the
   * directory registered in the edition gets a participation with the current name and date of the edition and, in
   * team mode, its team mate (when the team mate is linked to the directory); the participations in this edition of
   * the persons no longer registered are removed. The players not linked to the directory, or linked to a deleted
   * person, are ignored.
   *
   * @param editionId the identifier of the edition file.
   * @param edition the edition.
   *
   * @return the new directory.
   */
  public Directory recordParticipations(final UUID editionId, final Edition edition) {
    final Map<UUID, UUID> teamMates = teamMates(edition);
    return map(person -> {
      final List<Participation> others = person.participations().stream()
                                               .filter(participation -> !participation.edition().equals(editionId))
                                               .toList();
      if (!teamMates.containsKey(person.id())) {
        return person.withParticipations(others);
      }
      final List<Participation> all = new ArrayList<>(others);
      all.add(new Participation(editionId, edition.name(), edition.date(), teamMates.get(person.id())));
      return person.withParticipations(all);
    });
  }

  /**
   * Maps the persons registered in an edition to their team mate.
   *
   * @param edition the edition.
   *
   * @return for each player linked to the directory, the identifier of its person and the one of its team mate, or
   *         {@code null} in individual mode or when the team mate is not linked to the directory.
   */
  private static Map<UUID, UUID> teamMates(final Edition edition) {
    final Map<UUID, UUID> teamMates = new HashMap<>();
    switch (edition.registrations()) {
      case TeamRegistrations(final List<Team> teams, _) -> {
        for (final Team team : teams) {
          final Player first = team.players().get(0);
          final Player second = team.players().get(1);
          putLinked(teamMates, first, second.personId());
          putLinked(teamMates, second, first.personId());
        }
      }
      case IndividualRegistrations(final List<Player> players, _) -> {
        for (final Player player : players) {
          putLinked(teamMates, player, null);
        }
      }
    }
    return teamMates;
  }

  /**
   * Records the team mate of a player, if the player is linked to the directory.
   *
   * @param teamMates the team mates, by person.
   * @param player the player.
   * @param teamMate the identifier of the person of the team mate, or {@code null}.
   */
  private static void putLinked(final Map<UUID, UUID> teamMates, final Player player, final UUID teamMate) {
    if (player.personId() != null) {
      teamMates.put(player.personId(), teamMate);
    }
  }

  /**
   * Lists the persons who took part in an edition, e.g. to contact them.
   *
   * @param editionId the identifier of the edition file.
   *
   * @return the persons with a participation in the edition, sorted.
   */
  public List<Person> participantsOf(final UUID editionId) {
    return persons.stream()
                  .filter(person -> person.participations().stream()
                                          .anyMatch(participation -> participation.edition().equals(editionId)))
                  .sorted(ORDER)
                  .toList();
  }

  /**
   * Proposes the team mate of a person to register the same team again: the team mate of its most recent
   * participation in team mode, if that person is still in the directory and not deceased.
   *
   * @param id the identifier of the person.
   *
   * @return the previous team mate, or nothing.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_PERSON} if no person has this identifier.
   */
  public Optional<Person> lastTeamMate(final UUID id) {
    return get(id).participations().stream()
                  .filter(participation -> participation.teamMate() != null)
                  .max(Comparator.comparing(Participation::editionDate))
                  .flatMap(participation -> find(participation.teamMate()))
                  .filter(mate -> mate.status() != ContactStatus.DECEASED);
  }

  // *******************************************************************************************************************
  // Contact
  // *******************************************************************************************************************

  /**
   * Returns the e-mail addresses of a selection of persons, e.g. to paste them in the <i>Bcc</i> field of a mail
   * client; the persons who are not contactable, or without address, are left out.
   *
   * @param selection the selected persons.
   *
   * @return the distinct addresses, in the order of the selection.
   */
  public static List<String> emailsOf(final Collection<Person> selection) {
    return contactsOf(selection, person -> person.email() == null ? null : person.email().value());
  }

  /**
   * Returns the phone numbers (format E.164) of a selection of persons; the persons who are not contactable, or
   * without number, are left out.
   *
   * @param selection the selected persons.
   *
   * @return the distinct numbers, in the order of the selection.
   */
  public static List<String> phonesOf(final Collection<Person> selection) {
    return contactsOf(selection, person -> person.phone() == null ? null : person.phone().value());
  }

  /**
   * Returns a contact detail of the contactable persons of a selection.
   *
   * @param selection the selected persons.
   * @param detail gives the contact detail of a person, or {@code null}.
   *
   * @return the distinct non-null details, in the order of the selection.
   */
  private static List<String> contactsOf(final Collection<Person> selection, final Function<Person, String> detail) {
    return selection.stream()
                    .filter(Person::isContactable)
                    .map(detail)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
  }

  // *******************************************************************************************************************
  // Helpers
  // *******************************************************************************************************************

  /**
   * Returns a copy of this directory where every person is transformed.
   *
   * @param transformation the transformation of a person, which keeps its identifier.
   *
   * @return the new directory.
   */
  private Directory map(final UnaryOperator<Person> transformation) {
    return new Directory(persons.stream().map(transformation).toList());
  }

  /**
   * Returns a copy of this directory where the participations referring to a team mate refer to another one.
   *
   * @param oldTeamMate the identifier of the former team mate.
   * @param newTeamMate the identifier of the new team mate, or {@code null} to remove the reference.
   *
   * @return the new directory.
   */
  private Directory replaceTeamMate(final UUID oldTeamMate, final UUID newTeamMate) {
    return map(person -> person.withParticipations(
        person.participations().stream()
              .map(participation -> oldTeamMate.equals(participation.teamMate())
                                    ? participation.withTeamMate(newTeamMate) : participation)
              .toList()));
  }

}
