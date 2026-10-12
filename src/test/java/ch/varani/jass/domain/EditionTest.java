package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Edition}: creation, validation, modifications, access to the registrations of each mode and
 * consistency of the schedule with the registrations.
 */
final class EditionTest {

  /** The name of the fictitious edition. */
  private static final String NAME = "Match aux cartes 2026";
  /** The date of the fictitious edition. */
  private static final LocalDate DATE = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** A number of games different from the regulation. */
  private static final int OTHER_GAMES = 6;
  /** A time of a game other than the default one, in minutes. */
  private static final int OTHER_MINUTES = 90;
  /** A plan of deals different from the regulation: 16 deals of forced trump. */
  private static final DealPlan FORCED_ONLY =
      new DealPlan(Collections.nCopies(Regulation.DEALS_PER_GAME, DealType.FORCED_TRUMP));

  /**
   * A new edition has the given name, date and mode, the games and the deals of the regulation and no registration.
   *
   * @param mode the tournament mode.
   */
  @ParameterizedTest
  @EnumSource(TournamentMode.class)
  @DisplayName("A new edition has no registration, and the games and the deals of the regulation")
  void createTest(final TournamentMode mode) {
    final Edition edition = Edition.create("  " + NAME + " ", DATE, mode);
    assertAll(
        () -> assertEquals(NAME, edition.name()),
        () -> assertEquals(DATE, edition.date()),
        () -> assertEquals(mode, edition.mode()),
        () -> assertEquals(Regulation.GAMES, edition.games()),
        () -> assertEquals(DealPlan.REGULATION, edition.deals()),
        () -> assertEquals(0, edition.registrations().size()),
        () -> assertFalse(edition.isScheduled()),
        () -> assertNull(edition.schedule()));
  }

  /**
   * A blank name, a number of games lower than 1 and missing values are rejected.
   */
  @Test
  @DisplayName("A blank name, less than one game and missing values are rejected")
  void validationTest() {
    final Edition edition = Edition.create(NAME, DATE, TournamentMode.TEAM);
    assertAll(
        () -> assertEquals(ValidationError.BLANK_EDITION_NAME,
                           assertThrows(DomainException.class, () -> edition.withName(" ")).getError()),
        () -> assertEquals(ValidationError.INVALID_GAMES,
                           assertThrows(DomainException.class, () -> edition.withGames(0)).getError()),
        () -> assertEquals(ValidationError.INVALID_GAME_MINUTES,
                           assertThrows(DomainException.class, () -> edition.withGameMinutes(0)).getError()),
        () -> assertEquals(ValidationError.INVALID_GAME_MINUTES,
                           assertThrows(DomainException.class,
                                        () -> edition.withGameMinutes(Edition.MAX_GAME_MINUTES + 1)).getError()),
        () -> assertThrows(NullPointerException.class, () -> edition.withName(null)),
        () -> assertThrows(NullPointerException.class, () -> edition.withDate(null)),
        () -> assertThrows(NullPointerException.class, () -> edition.withDeals(null)),
        () -> assertThrows(NullPointerException.class, () -> edition.withRegistrations(null)),
        () -> assertThrows(NullPointerException.class, () -> Edition.create(NAME, DATE, null)));
  }

  /**
   * The modifications return a new edition with the new value.
   */
  @Test
  @DisplayName("The modifications return a new edition with the new value")
  void withTest() {
    final Edition edition = Edition.create(NAME, DATE, TournamentMode.TEAM);
    final IndividualRegistrations players = IndividualRegistrations.empty();
    assertAll(
        () -> assertEquals("Other", edition.withName("Other").name()),
        () -> assertEquals(DATE.plusDays(1), edition.withDate(DATE.plusDays(1)).date()),
        () -> assertEquals(OTHER_GAMES, edition.withGames(OTHER_GAMES).games()),
        () -> assertEquals(FORCED_ONLY, edition.withDeals(FORCED_ONLY).deals()),
        () -> assertEquals(TournamentMode.INDIVIDUAL, edition.withRegistrations(players).mode()),
        () -> assertEquals(NAME, edition.name()));
  }

  /**
   * The registrations of the right mode are returned; the other mode is rejected.
   */
  @Test
  @DisplayName("The registrations of the other mode are rejected")
  void registrationsOfModeTest() {
    final Edition teams = Edition.create(NAME, DATE, TournamentMode.TEAM);
    final Edition players = Edition.create(NAME, DATE, TournamentMode.INDIVIDUAL);
    assertAll(
        () -> assertSame(teams.registrations(), teams.teamRegistrations()),
        () -> assertSame(players.registrations(), players.individualRegistrations()),
        () -> assertEquals(ValidationError.WRONG_MODE,
                           assertThrows(DomainException.class, teams::individualRegistrations).getError()),
        () -> assertEquals(ValidationError.WRONG_MODE,
                           assertThrows(DomainException.class, players::teamRegistrations).getError()));
  }

  /**
   * Rule 1: the schedule can be computed once games + 1 teams are registered.
   */
  @Test
  @DisplayName("Rule 1: enough registrations once games + 1 teams are registered")
  void enoughRegistrationsTest() {
    TeamRegistrations registrations = TeamRegistrations.empty();
    for (int i = 1; i <= Regulation.GAMES; i++) {
      registrations = registrations.add(new Team(new Player("First" + i, "A"), new Player("First" + i, "B")));
    }
    final Edition edition = Edition.create(NAME, DATE, TournamentMode.TEAM).withRegistrations(registrations);
    final TeamRegistrations oneMore = registrations.add(new Team(new Player("Last", "A"), new Player("Last", "B")));
    assertAll(
        () -> assertFalse(edition.hasEnoughRegistrations()),
        () -> assertTrue(edition.withRegistrations(oneMore).hasEnoughRegistrations()));
  }

  /**
   * Creates team registrations of fictitious players.
   *
   * @param count the number of teams.
   *
   * @return the registrations.
   */
  private static TeamRegistrations teams(final int count) {
    TeamRegistrations registrations = TeamRegistrations.empty();
    for (int i = 1; i <= count; i++) {
      registrations = registrations.add(new Team(new Player("First" + i, "A"), new Player("First" + i, "B")));
    }
    return registrations;
  }

  /**
   * Creates a team edition with the minimum number of teams and its schedule.
   *
   * @return the scheduled edition.
   */
  private static Edition scheduledEdition() {
    final TeamRegistrations registrations = teams(Regulation.GAMES + 1);
    return Edition.create(NAME, DATE, TournamentMode.TEAM)
                  .withRegistrations(registrations)
                  .withSchedule(new TeamSchedule(registrations.size(), Regulation.GAMES, List.of()));
  }

  /**
   * A schedule matching the registrations closes the registration, and removing it reopens the registration; the
   * name, the date, the plan of the deals and the names of the players can still be changed.
   */
  @Test
  @DisplayName("A matching schedule is kept when the name, the date, the deals or a team is changed")
  void scheduleTest() {
    final Edition edition = scheduledEdition();
    final Team renamed = new Team(new Player("Other", "A"), new Player("Other", "B"));
    assertAll(
        () -> assertTrue(edition.isScheduled()),
        () -> assertFalse(edition.withoutSchedule().isScheduled()),
        () -> assertTrue(edition.withName("Other").isScheduled()),
        () -> assertTrue(edition.withDate(DATE.plusDays(1)).isScheduled()),
        () -> assertTrue(edition.withDeals(FORCED_ONLY).isScheduled()),
        () -> assertTrue(edition.withRegistrations(edition.teamRegistrations().replace(1, renamed)).isScheduled()),
        () -> assertThrows(NullPointerException.class, () -> edition.withSchedule(null)));
  }

  /**
   * A schedule that does not match the number of teams or of games of the edition is rejected: adding or removing a
   * team, or changing the number of games, requires to remove the schedule first.
   */
  @Test
  @DisplayName("Adding or removing a team, or changing the games, requires to remove the schedule first")
  void scheduleMismatchTest() {
    final Edition edition = scheduledEdition();
    final TeamRegistrations oneMore = teams(Regulation.GAMES + 2);
    final TeamSchedule otherGames = new TeamSchedule(edition.registrations().size(), OTHER_GAMES, List.of());
    final TeamRegistrations oneLess = edition.teamRegistrations().remove(1);
    assertAll(
        () -> assertEquals(ValidationError.SCHEDULE_MISMATCH,
                           assertThrows(DomainException.class, () -> edition.withRegistrations(oneMore)).getError()),
        () -> assertEquals(ValidationError.SCHEDULE_MISMATCH,
                           assertThrows(DomainException.class, () -> edition.withRegistrations(oneLess)).getError()),
        () -> assertEquals(ValidationError.SCHEDULE_MISMATCH,
                           assertThrows(DomainException.class, () -> edition.withGames(OTHER_GAMES)).getError()),
        () -> assertEquals(ValidationError.SCHEDULE_MISMATCH,
                           assertThrows(DomainException.class, () -> edition.withSchedule(otherGames)).getError()),
        () -> assertEquals(oneMore, edition.withoutSchedule().withRegistrations(oneMore).registrations()));
  }

  /**
   * A schedule of the other mode is rejected.
   */
  @Test
  @DisplayName("A schedule of the other mode is rejected")
  void scheduleWrongModeTest() {
    final Edition individual = Edition.create(NAME, DATE, TournamentMode.INDIVIDUAL);
    final Edition team = Edition.create(NAME, DATE, TournamentMode.TEAM);
    final TeamSchedule teamSchedule = new TeamSchedule(0, Regulation.GAMES, List.of());
    final IndividualSchedule individualSchedule = new IndividualSchedule(0, Regulation.GAMES, List.of());
    assertAll(
        () -> assertEquals(ValidationError.WRONG_MODE,
                           assertThrows(DomainException.class, () -> individual.withSchedule(teamSchedule))
                               .getError()),
        () -> assertEquals(ValidationError.WRONG_MODE,
                           assertThrows(DomainException.class, () -> team.withSchedule(individualSchedule))
                               .getError()));
  }

  /**
   * In individual mode, a schedule matching the number of players closes the registration; adding a player requires
   * to remove the schedule first.
   */
  @Test
  @DisplayName("An individual edition accepts a schedule matching its players")
  void individualScheduleTest() {
    IndividualRegistrations registrations = IndividualRegistrations.empty();
    for (int i = 1; i <= Regulation.GAMES + 1; i++) {
      registrations = registrations.add(new Player("First" + i, "A"));
    }
    final Edition edition = Edition.create(NAME, DATE, TournamentMode.INDIVIDUAL)
                                   .withRegistrations(registrations)
                                   .withSchedule(new IndividualSchedule(registrations.size(), Regulation.GAMES,
                                                                        List.of()));
    final IndividualRegistrations oneMore = registrations.add(new Player("Last", "A"));
    assertAll(
        () -> assertTrue(edition.isScheduled()),
        () -> assertEquals(ValidationError.SCHEDULE_MISMATCH,
                           assertThrows(DomainException.class, () -> edition.withRegistrations(oneMore)).getError()));
  }

  /**
   * A duplicated edition takes over the mode, the games and the deals of the original, with a new name and date,
   * without registration nor schedule.
   *
   * @param mode the tournament mode.
   */
  @ParameterizedTest
  @EnumSource(TournamentMode.class)
  @DisplayName("A duplicated edition keeps the mode, the games and the deals, without registration nor schedule")
  void duplicateTest(final TournamentMode mode) {
    final Edition original = Edition.create(NAME, DATE, mode).withGames(OTHER_GAMES).withDeals(FORCED_ONLY)
                                    .withGameMinutes(OTHER_MINUTES);
    final Edition copy = original.duplicate("Other", DATE.plusYears(1));
    assertAll(
        () -> assertEquals("Other", copy.name()),
        () -> assertEquals(DATE.plusYears(1), copy.date()),
        () -> assertEquals(mode, copy.mode()),
        () -> assertEquals(OTHER_GAMES, copy.games()),
        () -> assertEquals(FORCED_ONLY, copy.deals()),
        () -> assertEquals(OTHER_MINUTES, copy.gameMinutes()),
        () -> assertEquals(Edition.DEFAULT_GAME_MINUTES, Edition.create(NAME, DATE, mode).gameMinutes()),
        () -> assertEquals(Edition.MAX_GAME_MINUTES,
                           original.withGameMinutes(Edition.MAX_GAME_MINUTES).gameMinutes()),
        () -> assertEquals(0, copy.registrations().size()),
        () -> assertFalse(copy.isScheduled()),
        () -> assertEquals(ValidationError.BLANK_EDITION_NAME,
                           assertThrows(DomainException.class, () -> original.duplicate(" ", DATE)).getError()));
  }

  /**
   * A scheduled edition is duplicated without its teams and its schedule.
   */
  @Test
  @DisplayName("A scheduled edition is duplicated without its teams and its schedule")
  void duplicateScheduledTest() {
    final Edition copy = scheduledEdition().duplicate(NAME, DATE.plusYears(1));
    assertAll(
        () -> assertEquals(0, copy.registrations().size()),
        () -> assertFalse(copy.isScheduled()));
  }

  /**
   * Creates a team edition of the minimum number of teams, with one game (round 1, table 1, team 1 against team 2).
   *
   * @return the scheduled edition.
   */
  private static Edition oneGameEdition() {
    final TeamRegistrations registrations = teams(Regulation.GAMES + 1);
    return Edition.create(NAME, DATE, TournamentMode.TEAM)
                  .withRegistrations(registrations)
                  .withSchedule(new TeamSchedule(registrations.size(), Regulation.GAMES,
                                                 List.of(new TeamRound(1, List.of(new TeamGame(1, 1, 2))))));
  }

  /**
   * Results matching the schedule, the plan of the deals and the registrations are accepted.
   */
  @Test
  @DisplayName("Results matching the schedule, the deals and the registrations are accepted")
  void resultsTest() {
    final Edition edition = oneGameEdition();
    final Results results = Results.EMPTY.withSheet(ScoreSheet.empty(1, 1, DealPlan.REGULATION))
                                         .withExclusion(Regulation.GAMES + 1, true)
                                         .withTieBreak(new TieBreak(1L, List.of(1, 2)));
    final Edition scored = edition.withResults(results);
    assertAll(
        () -> assertFalse(edition.hasResults()),
        () -> assertTrue(scored.hasResults()),
        () -> assertEquals(results, scored.results()),
        () -> assertEquals(results, scored.withName("Other").results()),
        () -> assertEquals(Results.EMPTY, scored.duplicate("Other", DATE).results()),
        () -> assertFalse(scored.withResults(Results.EMPTY).withoutSchedule().isScheduled()),
        () -> assertThrows(NullPointerException.class, () -> edition.withResults(null)));
  }

  /**
   * Results that do not match the edition are rejected: without schedule, for a game that is not in the schedule,
   * with another number of deals, or with a participant that is not registered.
   */
  @Test
  @DisplayName("Results that do not match the schedule, the deals or the registrations are rejected")
  void resultsMismatchTest() {
    final Edition edition = oneGameEdition();
    final Edition scored = edition.withResults(Results.EMPTY.withSheet(ScoreSheet.empty(1, 1, DealPlan.REGULATION)));
    final DealPlan shorter = new DealPlan(DealPlan.REGULATION.deals().subList(0, Regulation.DEALS_PER_BLOCK));
    final int unknown = Regulation.GAMES + 2;
    final DealPlan sameSize = new DealPlan(DealPlan.REGULATION.deals().reversed());
    assertAll(
        () -> assertMismatch(() -> Edition.create(NAME, DATE, TournamentMode.TEAM)
                                          .withResults(Results.EMPTY.withExclusion(1, true))),
        () -> assertMismatch(scored::withoutSchedule),
        () -> assertMismatch(() -> scored.withDeals(shorter)),
        () -> assertMismatch(() -> edition.withResults(
            Results.EMPTY.withSheet(ScoreSheet.empty(1, 2, DealPlan.REGULATION)))),
        () -> assertMismatch(() -> edition.withResults(
            Results.EMPTY.withSheet(ScoreSheet.empty(2, 1, DealPlan.REGULATION)))),
        () -> assertMismatch(() -> edition.withResults(Results.EMPTY.withSheet(ScoreSheet.empty(1, 1, shorter)))),
        () -> assertMismatch(() -> edition.withResults(Results.EMPTY.withExclusion(0, true))),
        () -> assertMismatch(() -> edition.withResults(Results.EMPTY.withExclusion(unknown, true))),
        () -> assertMismatch(() -> edition.withResults(
            Results.EMPTY.withTieBreak(new TieBreak(1L, List.of(1, unknown))))),
        () -> assertEquals(sameSize, scored.withDeals(sameSize).deals()));
  }

  /**
   * Asserts that an operation is rejected because the results do not match the edition.
   *
   * @param operation the operation.
   */
  private static void assertMismatch(final Executable operation) {
    assertEquals(ValidationError.RESULTS_MISMATCH, assertThrows(DomainException.class, operation).getError());
  }

  /**
   * The accounting can be changed; a duplicated edition keeps the registration fee, not the entries.
   */
  @Test
  @DisplayName("The accounting can be changed; a duplicate keeps the fee, not the entries")
  void ledgerTest() {
    final Ledger ledger = Ledger.EMPTY.withFee(Money.of("30"))
                                      .add(LedgerEntry.create(DATE, EntryType.EXPENSE, "Prix", "", Money.of("100")));
    final Edition edition = Edition.create(NAME, DATE, TournamentMode.TEAM).withLedger(ledger);
    assertAll(
        () -> assertEquals(Ledger.EMPTY, Edition.create(NAME, DATE, TournamentMode.TEAM).ledger()),
        () -> assertEquals(ledger, edition.ledger()),
        () -> assertEquals(ledger, edition.withName("Other").ledger()),
        () -> assertEquals(Ledger.EMPTY.withFee(Money.of("30")), edition.duplicate("Other", DATE).ledger()),
        () -> assertThrows(NullPointerException.class, () -> edition.withLedger(null)));
  }

}
