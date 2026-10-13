package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.EmailAddress;
import ch.varani.jass.directory.Person;
import ch.varani.jass.directory.PhoneNumber;
import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.EntryType;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Money;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.DirectoryStore;
import ch.varani.jass.persistence.EditionStore;
import ch.varani.jass.persistence.LogoStore;
import ch.varani.jass.solver.SolverOptions;
import ch.varani.jass.solver.SolverResult;
import ch.varani.jass.solver.TeamScheduleSolver;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The fictitious data of the screenshots of the user guide ({@code guide/}): a data folder holding three past
 * editions of the match, the edition of the next year with its registration in progress, the directory of the
 * players with their (fictitious) contact details, and the logo of the documents.
 *
 * <p>The edition of 2026 has twelve teams, the schedule of the team solver (one worker, fixed seed, so always the
 * same), the score sheets of every game drawn from a seeded random generator, and its accounting; its categories and
 * descriptions are written in the language of the guide. Every name is fictitious ({@code AGENTS.md} §5), every
 * e-mail address uses the reserved domain {@code example.ch}.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
final class GuideFixtures {

  /** The name of the edition shown in the guide. */
  static final String EDITION = "Match aux cartes 2026";
  /** The name of the edition whose registration is in progress. */
  static final String NEXT = "Match aux cartes 2027";
  /** The number of teams of the edition shown in the guide. */
  static final int TEAMS = 12;
  /** The number of teams registered so far for the next edition, one more than its maximum: the last one waits. */
  private static final int NEXT_TEAMS = 7;
  /** The number of teams of the past editions. */
  private static final int PAST_TEAMS = 10;
  /** The number of games of an edition. */
  private static final int GAMES = 4;
  /** The highest points of a deal typed in column A by the generator. */
  private static final int MAX_POINTS = 158;
  /** The seed of the generator of the points. */
  private static final long SEED = 2026;
  /** The teams who have not paid their fee yet, by index. */
  private static final List<Integer> UNPAID = List.of(4, 9);
  /** The registration fee per player. */
  private static final String FEE = "30.00";
  /** The year of the edition shown in the guide. */
  private static final int YEAR = 2026;
  /** The day of the month of the editions. */
  private static final int DAY = 20;
  /** The first names of the players, in the order of the teams. */
  private static final List<String> FIRST_NAMES = List.of(
      "Anne", "Bernard", "Claire", "Daniel", "Élise", "François", "Gabrielle", "Henri", "Isabelle", "Jacques",
      "Laure", "Marc", "Nicole", "Olivier", "Pascale", "René", "Sophie", "Thierry", "Ursula", "Vincent",
      "Yvonne", "Werner", "Lucia", "Marco");
  /** The last names of the players, in the order of the teams. */
  private static final List<String> LAST_NAMES = List.of(
      "Fontaine", "Rochat", "Perret", "Jaquet", "Monnier", "Vuille", "Favre", "Girard", "Matthey", "Borel",
      "Junod", "Robert", "Dubois", "Racine", "Humbert", "Gauchat", "Steiner", "Meier", "Bianchi", "Keller",
      "Schmid", "Rossi", "Huguenin", "Calame");
  /** The entries of the accounting. */
  private static final List<Entry> ENTRIES = List.of(
      new Entry(EntryType.INCOME, "640.00", "bar", "bar.day"),
      new Entry(EntryType.INCOME, "300.00", "sponsor", "sponsor.garage"),
      new Entry(EntryType.EXPENSE, "250.00", "hall", "hall.rent"),
      new Entry(EntryType.EXPENSE, "420.00", "prizes", "prizes.baskets"),
      new Entry(EntryType.EXPENSE, "85.50", "bar", "bar.drinks"));
  /** The texts of the accounting per language: categories and descriptions. */
  private static final Map<String, Map<String, String>> TEXTS = Map.of(
      "fr", Map.of("bar", "Buvette", "bar.day", "Recettes du jour", "bar.drinks", "Boissons",
                   "sponsor", "Sponsoring", "sponsor.garage", "Garage du Village",
                   "hall", "Salle", "hall.rent", "Location de la salle", "prizes", "Prix",
                   "prizes.baskets", "Paniers garnis"),
      "de", Map.of("bar", "Bar", "bar.day", "Einnahmen des Tages", "bar.drinks", "Getränke",
                   "sponsor", "Sponsoring", "sponsor.garage", "Dorfgarage",
                   "hall", "Saal", "hall.rent", "Saalmiete", "prizes", "Preise",
                   "prizes.baskets", "Geschenkkörbe"),
      "it", Map.of("bar", "Bar", "bar.day", "Incassi del giorno", "bar.drinks", "Bevande",
                   "sponsor", "Sponsorizzazione", "sponsor.garage", "Garage del Villaggio",
                   "hall", "Sala", "hall.rent", "Affitto della sala", "prizes", "Premi",
                   "prizes.baskets", "Cesti regalo"),
      "en", Map.of("bar", "Bar", "bar.day", "Takings of the day", "bar.drinks", "Drinks",
                   "sponsor", "Sponsorship", "sponsor.garage", "Village garage",
                   "hall", "Hall", "hall.rent", "Rent of the hall", "prizes", "Prizes",
                   "prizes.baskets", "Gift baskets"));
  /** The first phone number of the directory, the others follow. */
  private static final long PHONE = 790_000_001L;
  /** The persons of the directory who no longer play: first name, last name, status. */
  private static final List<String[]> FORMER = List.of(
      new String[] {"Albert", "Cuche", "DO_NOT_CONTACT"},
      new String[] {"Germaine", "Droz", "DECEASED"});

  /**
   * Prevents the instantiation of this utility class.
   */
  private GuideFixtures() {
  }

  /**
   * Fills a data folder with the editions, the directory and the logo of the guide.
   *
   * @param folder the data folder, empty.
   * @param locale the language of the guide: the texts of the accounting.
   * @param logo the image of the logo of the documents.
   *
   * @throws Exception if a file cannot be written.
   */
  static void fill(final Path folder, final Locale locale, final Path logo) throws Exception {
    final List<Person> persons = persons();
    final EditionStore store = new EditionStore(folder);
    Directory directory = Directory.empty();
    for (final Person person : persons) {
      directory = directory.add(person);
    }
    final int previous = YEAR - 2;
    for (int year = previous; year < YEAR; year++) {
      final Edition past = Edition.create("Match aux cartes " + year, LocalDate.of(year, Month.NOVEMBER, DAY),
                                          TournamentMode.TEAM).withGames(GAMES)
                                  .withRegistrations(teams(persons, year - previous, PAST_TEAMS, true));
      directory = directory.recordParticipations(store.create(past), past);
    }
    final Edition edition = played(Edition.create(EDITION, UiSupport.TODAY, TournamentMode.TEAM).withGames(GAMES)
                                          .withRegistrations(teams(persons, 0, TEAMS, false)))
        .withLedger(ledger(locale));
    directory = directory.recordParticipations(store.create(edition), edition);
    store.create(Edition.create(NEXT, LocalDate.of(YEAR + 1, Month.NOVEMBER, DAY - 1), TournamentMode.TEAM)
                        .withGames(GAMES).withRegistrations(teams(persons, 0, NEXT_TEAMS, true))
                        .withMaxTeams(NEXT_TEAMS - 1));
    for (final String[] former : FORMER) {
      directory = directory.add(Person.create(former[0], former[1])
                                      .withStatus(ContactStatus.valueOf(former[2]), UiSupport.TODAY));
    }
    new DirectoryStore(folder).save(directory);
    new LogoStore(folder).save(logo);
  }

  /**
   * Creates the persons of the directory who play: names, e-mail address and phone number, all fictitious.
   *
   * @return the persons, in the order of the teams.
   */
  private static List<Person> persons() {
    final List<Person> persons = new ArrayList<>();
    for (int i = 0; i < FIRST_NAMES.size(); i++) {
      final String first = FIRST_NAMES.get(i);
      final String last = LAST_NAMES.get(i);
      persons.add(Person.create(first, last)
                        .withEmail(new EmailAddress(ascii(first) + "." + ascii(last) + "@example.ch"))
                        .withPhone(new PhoneNumber("0" + (PHONE + i))));
    }
    return persons;
  }

  /**
   * Writes a name without accents and in lower case, for an e-mail address.
   *
   * @param name the name.
   *
   * @return the name in ASCII.
   */
  private static String ascii(final String name) {
    return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
  }

  /**
   * Forms the teams of an edition from the persons, two after two.
   *
   * @param persons the persons, in the order of the teams.
   * @param shift the number of persons skipped first, so that the teams change from one edition to the next.
   * @param count the number of teams.
   * @param paid whether every player has paid; otherwise the teams of {@link #UNPAID} have not.
   *
   * @return the registrations.
   */
  private static TeamRegistrations teams(final List<Person> persons, final int shift, final int count,
                                         final boolean paid) {
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 0; i < count; i++) {
      final boolean hasPaid = paid || !UNPAID.contains(i);
      teams = teams.add(new Team(persons.get((2 * i + shift) % persons.size()).asPlayer().withPaid(hasPaid),
                                 persons.get((2 * i + 1 + shift) % persons.size()).asPlayer().withPaid(hasPaid)));
    }
    return teams;
  }

  /**
   * Computes the schedule of an edition with the team solver, then fills every score sheet with points drawn from a
   * seeded generator, column B derived.
   *
   * @param edition the edition with its registrations.
   *
   * @return the edition with its schedule and its results.
   */
  private static Edition played(final Edition edition) {
    final SolverResult<TeamSchedule> result = new TeamScheduleSolver(TEAMS, GAMES, SolverOptions.DEFAULT).solve();
    final TeamSchedule schedule = ((SolverResult.Solved<TeamSchedule>) result).schedule();
    final Random random = new Random(SEED);
    Results results = Results.EMPTY;
    for (final TeamRound round : schedule.rounds()) {
      for (final TeamGame game : round.games()) {
        final List<DealScore> deals = new ArrayList<>();
        for (int deal = 0; deal < DealPlan.REGULATION.size(); deal++) {
          deals.add(DealScore.of(random.nextInt(MAX_POINTS)));
        }
        results = results.withSheet(new ScoreSheet(round.number(), game.table(), deals, Absence.NONE));
      }
    }
    return edition.withSchedule(schedule).withResults(results);
  }

  /**
   * Creates the accounting of the edition: the fee per player and its entries, in the language of the guide.
   *
   * @param locale the language of the guide.
   *
   * @return the accounting.
   */
  private static Ledger ledger(final Locale locale) {
    final Map<String, String> texts = TEXTS.get(locale.getLanguage());
    Ledger ledger = Ledger.EMPTY.withFee(Money.of(FEE));
    for (final Entry entry : ENTRIES) {
      ledger = ledger.add(LedgerEntry.create(UiSupport.TODAY, entry.type(), texts.get(entry.category()),
                                             texts.get(entry.description()), Money.of(entry.amount())));
    }
    return ledger;
  }

  /**
   * An entry of the accounting of the guide.
   *
   * @param type income or expense.
   * @param amount the amount, in CHF.
   * @param category the key of the category in {@link #TEXTS}.
   * @param description the key of the description in {@link #TEXTS}.
   */
  private record Entry(EntryType type, String amount, String category, String description) {
  }

}
