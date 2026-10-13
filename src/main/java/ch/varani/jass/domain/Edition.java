package ch.varani.jass.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An edition of the <i>match aux cartes</i>: its name, its date, the number of games of each participant, the content
 * of the games (plan of the deals), the registrations, whose type gives the tournament mode, and the schedule once it
 * is computed.
 *
 * <p>The name distinguishes the editions organised in the same calendar year; the name and the date appear on every
 * generated document. The name is cleaned (leading and trailing spaces removed) and must not be blank.</p>
 *
 * <p>The schedule refers to the participants (teams or players) by their number: it must match the mode, the number
 * of participants and the number of games of the edition. Replacing a participant (e.g. to correct a name) keeps the
 * schedule; adding or removing a participant, or changing the number of games, requires to remove the schedule first
 * ({@link #withoutSchedule()}), i.e. to reopen the registration. The plan of the deals does not change the
 * schedule.</p>
 *
 * <p>The results refer to the games of the schedule (round and table) and to the participants by their number: they
 * require the schedule, and each sheet has one value per deal of the plan. Once a result is entered, the schedule
 * cannot be removed nor the plan of the deals changed, unless the results are removed first ({@link Results#EMPTY});
 * an absence or an exclusion is then the way to handle a missing participant (rules 17 and 19).</p>
 *
 * <p><b>Maximum and waiting list</b> (decided by the maintainer, not in the regulation): an edition accepts at most
 * {@code maxTeams} teams, i.e. {@link #capacity()} participants ({@code 2 × maxTeams} players in individual mode). A
 * participant registered once the edition is full, or once the schedule is computed, goes to the waiting list, first
 * in, first out ({@link #isFull()}). While the registration is open, the waiting list only holds participants when the
 * edition is full: a participant who withdraws ({@link #withdraw(int)}) is followed by the first one waiting, raising
 * the maximum registers the first ones waiting, lowering it moves the last registered to the head of the waiting list
 * ({@link #withMaxTeams(int)}). Once the schedule is computed, a participant who withdraws is replaced, by the first
 * one waiting ({@link #replaceByFirstWaiting(int)}) or by another one: the new participant takes its number, so that
 * the schedule is kept; a replacement is allowed only as long as no result concerns the participant
 * ({@link #canReplace(int)}).</p>
 *
 * <p>A new edition can be created from an existing one ({@link #duplicate(String, LocalDate)}): it takes over the
 * settings of the tournament (mode, games, plan of the deals, time of a game, maximum of teams, registration fee),
 * but neither the
 * registrations, the schedule, the results nor the entries of the accounting.</p>
 *
 * <p>Every modification returns a new instance. Immutable, safe for concurrent use.</p>
 *
 * @param name the name of the edition, not blank.
 * @param date the date of the edition.
 * @param games the number of games of each participant, at least 1 ({@link Regulation#GAMES} by default).
 * @param deals the plan of the deals of every game ({@link DealPlan#REGULATION} by default).
 * @param registrations the registrations, whose type gives the tournament mode.
 * @param schedule the schedule, of the mode of the registrations, or {@code null} while the registration is open.
 * @param results the results entered (score sheets, exclusions, drawing of lots), {@link Results#EMPTY} until the
 *                first score is entered.
 * @param ledger the accounting: registration fee and entries with their supporting documents.
 * @param gameMinutes the time given to play a game, in minutes, from 1 to {@value #MAX_GAME_MINUTES}
 *                    ({@value #DEFAULT_GAME_MINUTES} by default): the countdown of the external display (F18); not
 *                    set by the regulation, which asks a game to end as soon as possible (rule 2).
 * @param maxTeams the maximum number of teams, at least 1 ({@value #DEFAULT_MAX_TEAMS} by default); twice as many
 *                 players in individual mode.
 */
public record Edition(String name, LocalDate date, int games, DealPlan deals, Registrations registrations,
                      Schedule schedule, Results results, Ledger ledger, int gameMinutes, int maxTeams) {

  /** The time given to play a game when the user did not set it, in minutes (decided by the maintainer). */
  public static final int DEFAULT_GAME_MINUTES = 60;
  /** The longest time that can be given to play a game, in minutes: five hours. */
  public static final int MAX_GAME_MINUTES = 300;
  /** The maximum number of teams of a new edition (decided by the maintainer, not in the regulation). */
  public static final int DEFAULT_MAX_TEAMS = 20;

  /**
   * Creates an edition, checking its values.
   *
   * @param name the name of the edition.
   * @param date the date of the edition.
   * @param games the number of games of each participant.
   * @param deals the plan of the deals of every game.
   * @param registrations the registrations.
   * @param schedule the schedule, or {@code null} while the registration is open.
   * @param results the results entered.
   * @param ledger the accounting.
   * @param gameMinutes the time given to play a game, in minutes.
   * @param maxTeams the maximum number of teams.
   *
   * @throws NullPointerException if the name, the date, the plan of the deals, the registrations, the results or the
   *                              accounting are {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_EDITION_NAME} if the name is blank, with
   *                         {@link ValidationError#INVALID_CHARACTER} if it contains a character that is not allowed,
   *                         with {@link ValidationError#INVALID_GAMES} if the number of games is lower than 1, with
   *                         {@link ValidationError#WRONG_MODE} if the schedule is not of the mode of the registrations,
   *                         or with {@link ValidationError#SCHEDULE_MISMATCH} if the schedule does not match the
   *                         number of participants or of games, or with {@link ValidationError#RESULTS_MISMATCH} if the
   *                         results do not match the schedule, the plan of the deals or the registrations, or with
   *                         {@link ValidationError#INVALID_GAME_MINUTES} if the time of a game is not between 1 and
   *                         {@value #MAX_GAME_MINUTES} minutes, or with {@link ValidationError#INVALID_MAX_TEAMS} if
   *                         the maximum of teams is lower than 1, or with
   *                         {@link ValidationError#TOO_MANY_REGISTRATIONS} if more participants are registered than
   *                         the maximum allows.
   */
  public Edition {
    name = TextChecks.checkCharacters(Objects.requireNonNull(name, "name").strip());
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(deals, "deals");
    Objects.requireNonNull(registrations, "registrations");
    Objects.requireNonNull(results, "results");
    Objects.requireNonNull(ledger, "ledger");
    if (name.isEmpty()) {
      throw new DomainException(ValidationError.BLANK_EDITION_NAME);
    }
    if (games < 1) {
      throw new DomainException(ValidationError.INVALID_GAMES);
    }
    if (gameMinutes < 1 || gameMinutes > MAX_GAME_MINUTES) {
      throw new DomainException(ValidationError.INVALID_GAME_MINUTES);
    }
    if (maxTeams < 1) {
      throw new DomainException(ValidationError.INVALID_MAX_TEAMS);
    }
    if (registrations.size() > capacity(registrations.mode(), maxTeams)) {
      throw new DomainException(ValidationError.TOO_MANY_REGISTRATIONS);
    }
    if (schedule != null) {
      checkSchedule(schedule, games, registrations);
    }
    checkResults(results, schedule, deals, registrations.size());
  }

  /**
   * Creates a new edition without any registration, with {@link Regulation#GAMES} games per participant, the plan
   * of the deals of the regulation ({@link DealPlan#REGULATION}), {@value #DEFAULT_GAME_MINUTES} minutes per game and
   * at most {@value #DEFAULT_MAX_TEAMS} teams.
   *
   * @param name the name of the edition.
   * @param date the date of the edition.
   * @param mode the tournament mode.
   *
   * @return the new edition.
   *
   * @throws NullPointerException if a parameter is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_EDITION_NAME} if the name is blank.
   */
  public static Edition create(final String name, final LocalDate date, final TournamentMode mode) {
    return new Edition(name, date, Regulation.GAMES, DealPlan.REGULATION,
                       emptyRegistrations(Objects.requireNonNull(mode, "mode")), null, Results.EMPTY,
                       Ledger.EMPTY, DEFAULT_GAME_MINUTES, DEFAULT_MAX_TEAMS);
  }

  /**
   * Returns the number of participants that a maximum of teams allows in a tournament mode.
   *
   * @param mode the tournament mode.
   * @param teams the maximum number of teams.
   *
   * @return {@code teams} in team mode, {@code teams × }{@value Regulation#PLAYERS_PER_TEAM} players in individual
   *         mode.
   */
  private static int capacity(final TournamentMode mode, final int teams) {
    return switch (mode) {
      case TEAM -> teams;
      case INDIVIDUAL -> teams * Regulation.PLAYERS_PER_TEAM;
    };
  }

  /**
   * Returns the number of teams that registrations fill: the number of teams in team mode, the number of players
   * divided by {@value Regulation#PLAYERS_PER_TEAM}, rounded up, in individual mode. It is the smallest maximum of
   * teams that accepts these registrations.
   *
   * @param registrations the registrations.
   *
   * @return the number of teams, the waiting list excluded.
   */
  public static int teamsOf(final Registrations registrations) {
    return switch (registrations.mode()) {
      case TEAM -> registrations.size();
      case INDIVIDUAL -> Math.ceilDiv(registrations.size(), Regulation.PLAYERS_PER_TEAM);
    };
  }

  /**
   * Returns the empty registrations of a tournament mode.
   *
   * @param mode the tournament mode.
   *
   * @return the empty team registrations, or the empty individual registrations.
   */
  private static Registrations emptyRegistrations(final TournamentMode mode) {
    return switch (mode) {
      case TEAM -> TeamRegistrations.empty();
      case INDIVIDUAL -> IndividualRegistrations.empty();
    };
  }

  /**
   * Checks that a schedule can belong to an edition: the schedule has the mode of the registrations, their number of
   * participants and the number of games of the edition.
   *
   * @param schedule the schedule to check.
   * @param games the number of games of the edition.
   * @param registrations the registrations of the edition.
   *
   * @throws DomainException with {@link ValidationError#WRONG_MODE} if the schedule is not of the mode of the
   *                         registrations, or with {@link ValidationError#SCHEDULE_MISMATCH} if the number of
   *                         participants or of games differs.
   */
  private static void checkSchedule(final Schedule schedule, final int games, final Registrations registrations) {
    if (schedule.mode() != registrations.mode()) {
      throw new DomainException(ValidationError.WRONG_MODE);
    }
    if (schedule.participants() != registrations.size() || schedule.games() != games) {
      throw new DomainException(ValidationError.SCHEDULE_MISMATCH);
    }
  }

  /**
   * Checks that results can belong to an edition: no result without schedule; every sheet for a game of the schedule,
   * with one value per deal of the plan; every excluded or drawn participant registered.
   *
   * @param results the results.
   * @param schedule the schedule of the edition, or {@code null}.
   * @param deals the plan of the deals of the edition.
   * @param participants the number of participants of the edition.
   *
   * @throws DomainException with {@link ValidationError#RESULTS_MISMATCH} if the results do not match.
   */
  private static void checkResults(final Results results, final Schedule schedule, final DealPlan deals,
                                   final int participants) {
    if (results.isEmpty()) {
      return;
    }
    if (schedule == null) {
      throw new DomainException(ValidationError.RESULTS_MISMATCH);
    }
    final List<Pairing> pairings = schedule.pairings();
    for (final ScoreSheet sheet : results.sheets()) {
      if (sheet.deals().size() != deals.size()
          || pairings.stream().noneMatch(pairing -> sheet.isFor(pairing.round(), pairing.table()))) {
        throw new DomainException(ValidationError.RESULTS_MISMATCH);
      }
    }
    final List<Integer> numbers = new ArrayList<>(results.excluded());
    if (results.tieBreak() != null) {
      numbers.addAll(results.tieBreak().order());
    }
    if (numbers.stream().anyMatch(number -> number < 1 || number > participants)) {
      throw new DomainException(ValidationError.RESULTS_MISMATCH);
    }
  }

  /**
   * Returns the tournament mode, given by the type of the registrations.
   *
   * @return the tournament mode.
   */
  public TournamentMode mode() {
    return registrations.mode();
  }

  /**
   * Tells whether there are enough registrations to try to compute the schedule (see
   * {@link Registrations#minimumSize(int)}).
   *
   * @return {@code true} if the number of registrations reaches the minimum.
   */
  public boolean hasEnoughRegistrations() {
    return registrations.size() >= registrations.minimumSize(games);
  }

  /**
   * Returns a copy of this edition with another name.
   *
   * @param newName the new name.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#BLANK_EDITION_NAME} if the name is blank.
   */
  public Edition withName(final String newName) {
    return new Edition(newName, date, games, deals, registrations, schedule, results, ledger, gameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition with another date.
   *
   * @param newDate the new date.
   *
   * @return the new edition.
   */
  public Edition withDate(final LocalDate newDate) {
    return new Edition(name, newDate, games, deals, registrations, schedule, results, ledger, gameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition with another number of games per participant.
   *
   * @param newGames the new number of games.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#INVALID_GAMES} if the number is lower than 1, or with
   *                         {@link ValidationError#SCHEDULE_MISMATCH} if the number changes while there is a schedule.
   */
  public Edition withGames(final int newGames) {
    return new Edition(name, date, newGames, deals, registrations, schedule, results, ledger, gameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition with another time given to play a game, e.g. the day of the tournament.
   *
   * @param newGameMinutes the new time, in minutes.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#INVALID_GAME_MINUTES} if the time is not between 1 and
   *                         {@value #MAX_GAME_MINUTES} minutes.
   */
  public Edition withGameMinutes(final int newGameMinutes) {
    return new Edition(name, date, games, deals, registrations, schedule, results, ledger, newGameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition with another plan of the deals; the schedule is kept.
   *
   * @param newDeals the new plan of the deals.
   *
   * @return the new edition.
   *
   * @throws NullPointerException if the plan is {@code null}.
   * @throws DomainException with {@link ValidationError#RESULTS_MISMATCH} if score sheets have been entered with
   *                         another number of deals.
   */
  public Edition withDeals(final DealPlan newDeals) {
    return new Edition(name, date, games, newDeals, registrations, schedule, results, ledger, gameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition with other registrations (possibly of another mode).
   *
   * @param newRegistrations the new registrations.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#WRONG_MODE} or {@link ValidationError#SCHEDULE_MISMATCH} if
   *                         there is a schedule and the new registrations change the mode or the number of
   *                         participants.
   */
  public Edition withRegistrations(final Registrations newRegistrations) {
    return new Edition(name, date, games, deals, newRegistrations, schedule, results, ledger, gameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition with another maximum of teams. While the registration is open, the first
   * participants waiting are registered when the maximum is raised, and the last registered participants move to the
   * head of the waiting list when it is lowered.
   *
   * @param newMaxTeams the new maximum of teams.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#INVALID_MAX_TEAMS} if the maximum is lower than 1, or with
   *                         {@link ValidationError#TOO_MANY_REGISTRATIONS} if the schedule is computed and more
   *                         participants are registered than the new maximum allows.
   */
  public Edition withMaxTeams(final int newMaxTeams) {
    Registrations kept = registrations;
    while (schedule == null && kept.size() > capacity(mode(), newMaxTeams)) {
      kept = kept.demote();
    }
    return new Edition(name, date, games, deals, kept, schedule, results, ledger, gameMinutes, newMaxTeams).filled();
  }

  /**
   * Returns this edition, or a copy of it in which the first participants waiting are registered while the
   * registration is open and the edition is not full.
   *
   * @return the edition with the waiting list moved up.
   */
  private Edition filled() {
    Registrations filled = registrations;
    while (schedule == null && filled.waitingSize() > 0 && filled.size() < capacity()) {
      filled = filled.promote();
    }
    return withRegistrations(filled);
  }

  /**
   * Returns the number of participants that the maximum of teams allows.
   *
   * @return {@code maxTeams} in team mode, {@code maxTeams × }{@value Regulation#PLAYERS_PER_TEAM} players in
   *         individual mode.
   */
  public int capacity() {
    return capacity(mode(), maxTeams);
  }

  /**
   * Tells whether a new participant goes to the waiting list: the edition is full, or the schedule is computed.
   *
   * @return {@code true} if a new registration goes to the waiting list.
   */
  public boolean isFull() {
    return schedule != null || registrations.size() >= capacity();
  }

  /**
   * Returns a copy of this edition with a new participant: registered when the edition is not full, otherwise added
   * at the end of the waiting list ({@link #isFull()}).
   *
   * @param players the two players of a team, or the player in individual mode.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#WRONG_TEAM_SIZE} if the number of players does not match the
   *                         mode, or with {@link ValidationError#DUPLICATE_PLAYER} if a person is already registered.
   */
  public Edition register(final List<Player> players) {
    return withRegistrations(registrations.enrol(players, isFull()));
  }

  /**
   * Returns a copy of this edition in which a registered participant who withdraws is replaced by another one, made
   * of players, who takes its number and its place in the schedule; the replaced participant leaves the edition.
   *
   * @param number the number of the participant who withdraws.
   * @param players the two players of a team, or the player in individual mode.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#REPLACEMENT_REFUSED} if a result concerns the participant
   *                         ({@link #canReplace(int)}), with {@link ValidationError#UNKNOWN_REGISTRATION} if no
   *                         participant has this number, with {@link ValidationError#WRONG_TEAM_SIZE} if the number
   *                         of players does not match the mode, or with {@link ValidationError#DUPLICATE_PLAYER} if a
   *                         person would be registered twice.
   */
  public Edition replace(final int number, final List<Player> players) {
    if (!canReplace(number)) {
      throw new DomainException(ValidationError.REPLACEMENT_REFUSED);
    }
    return withRegistrations(registrations.correct(number, players, false));
  }

  /**
   * Returns a copy of this edition without a registered participant, who withdraws while the registration is open;
   * the following participants are renumbered and the first participant waiting is registered.
   *
   * @param number the number of the participant who withdraws.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#UNKNOWN_REGISTRATION} if no participant has this number, or
   *                         with {@link ValidationError#REPLACEMENT_REFUSED} if the schedule is computed (the
   *                         participant must then be replaced).
   */
  public Edition withdraw(final int number) {
    if (schedule != null) {
      throw new DomainException(ValidationError.REPLACEMENT_REFUSED);
    }
    return withRegistrations(registrations.remove(number)).filled();
  }

  /**
   * Tells whether a registered participant can be replaced by another one, keeping the schedule: no score sheet of its
   * games is entered (absences included), it is not excluded and the drawing of lots is not done.
   *
   * @param number the number of the participant.
   *
   * @return {@code true} if no result concerns the participant.
   */
  public boolean canReplace(final int number) {
    if (results.tieBreak() != null || results.excluded().contains(number)) {
      return false;
    }
    return schedule == null || schedule.pairings().stream()
        .filter(pairing -> pairing.giver().contains(number) || pairing.receiver().contains(number))
        .noneMatch(pairing -> results.sheet(pairing.round(), pairing.table()).isPresent());
  }

  /**
   * Returns a copy of this edition in which a registered participant who withdraws is replaced by the first
   * participant waiting, who takes its number and its place in the schedule; the replaced participant leaves the
   * edition.
   *
   * @param number the number of the participant who withdraws.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#REPLACEMENT_REFUSED} if a result concerns the participant
   *                         ({@link #canReplace(int)}), or with {@link ValidationError#UNKNOWN_REGISTRATION} if no
   *                         participant has this number or if the waiting list is empty.
   */
  public Edition replaceByFirstWaiting(final int number) {
    if (!canReplace(number)) {
      throw new DomainException(ValidationError.REPLACEMENT_REFUSED);
    }
    return withRegistrations(registrations.replaceByFirstWaiting(number));
  }

  /**
   * Returns a copy of this edition with a schedule, which closes the registration.
   *
   * @param newSchedule the schedule computed for the registrations of this edition.
   *
   * @return the new edition.
   *
   * @throws NullPointerException if the schedule is {@code null}.
   * @throws DomainException with {@link ValidationError#WRONG_MODE} if the schedule is not of the mode of the
   *                         edition, or with {@link ValidationError#SCHEDULE_MISMATCH} if the schedule does not
   *                         match the number of participants or of games.
   */
  public Edition withSchedule(final Schedule newSchedule) {
    return new Edition(name, date, games, deals, registrations, Objects.requireNonNull(newSchedule, "schedule"),
                       results, ledger, gameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition without schedule, which reopens the registration; the first participants waiting
   * are registered while the edition is not full.
   *
   * @return the new edition.
   *
   * @throws DomainException with {@link ValidationError#RESULTS_MISMATCH} if results have been entered.
   */
  public Edition withoutSchedule() {
    return new Edition(name, date, games, deals, registrations, null, results, ledger, gameMinutes, maxTeams)
        .filled();
  }

  /**
   * Returns a copy of this edition with other results, e.g. after a score sheet is entered.
   *
   * @param newResults the new results, {@link Results#EMPTY} to remove them all.
   *
   * @return the new edition.
   *
   * @throws NullPointerException if the results are {@code null}.
   * @throws DomainException with {@link ValidationError#RESULTS_MISMATCH} if the results do not match the schedule,
   *                         the plan of the deals or the registrations.
   */
  public Edition withResults(final Results newResults) {
    return new Edition(name, date, games, deals, registrations, schedule, newResults, ledger, gameMinutes, maxTeams);
  }

  /**
   * Returns a copy of this edition with another accounting.
   *
   * @param newLedger the new accounting.
   *
   * @return the new edition.
   *
   * @throws NullPointerException if the accounting is {@code null}.
   */
  public Edition withLedger(final Ledger newLedger) {
    return new Edition(name, date, games, deals, registrations, schedule, results, newLedger, gameMinutes, maxTeams);
  }

  /**
   * Tells whether a result has been entered (a score sheet, an exclusion or a drawing of lots).
   *
   * @return {@code true} if the results are not empty.
   */
  public boolean hasResults() {
    return !results.isEmpty();
  }

  /**
   * Creates a new edition with the settings of the tournament of this one: the mode, the number of games, the plan of
   * the deals, the time of a game, the maximum of teams and the registration fee. The new edition has no
   * registration, no schedule, no result and no entry in its accounting.
   *
   * @param newName the name of the new edition.
   * @param newDate the date of the new edition.
   *
   * @return the new edition.
   *
   * @throws NullPointerException if the name or the date is {@code null}.
   * @throws DomainException with {@link ValidationError#BLANK_EDITION_NAME} if the name is blank, or with
   *                         {@link ValidationError#INVALID_CHARACTER} if it contains a character that is not allowed.
   */
  public Edition duplicate(final String newName, final LocalDate newDate) {
    return new Edition(newName, newDate, games, deals, emptyRegistrations(mode()), null, Results.EMPTY,
                       Ledger.EMPTY.withFee(ledger.fee()), gameMinutes, maxTeams);
  }

  /**
   * Tells whether the schedule has been computed, i.e. whether the registration is closed.
   *
   * @return {@code true} if the edition has a schedule.
   */
  public boolean isScheduled() {
    return schedule != null;
  }

  /**
   * Returns the registrations of the team mode.
   *
   * @return the team registrations.
   *
   * @throws DomainException with {@link ValidationError#WRONG_MODE} if the edition is in individual mode.
   */
  public TeamRegistrations teamRegistrations() {
    if (registrations instanceof final TeamRegistrations teams) {
      return teams;
    }
    throw new DomainException(ValidationError.WRONG_MODE);
  }

  /**
   * Returns the registrations of the individual mode.
   *
   * @return the individual registrations.
   *
   * @throws DomainException with {@link ValidationError#WRONG_MODE} if the edition is in team mode.
   */
  public IndividualRegistrations individualRegistrations() {
    if (registrations instanceof final IndividualRegistrations players) {
      return players;
    }
    throw new DomainException(ValidationError.WRONG_MODE);
  }

}
