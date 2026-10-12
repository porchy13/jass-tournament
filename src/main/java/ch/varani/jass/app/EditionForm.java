package ch.varani.jass.app;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.TournamentMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The values of the form of a new edition (F2, F4, F15) or of the duplication of an edition (F16), and their check:
 * name, date, tournament mode, number of games and plan of the deals.
 *
 * <p>A new form proposes the settings of the regulation (4 games, plan of rules 3 and 8 to 11); a duplicate takes over
 * the mode (fixed), the games, the plan and the registration fee of the duplicated edition, with an empty name. The
 * plan is edited deal by deal, and by blocks of {@link Regulation#DEALS_PER_BLOCK} deals so that it stays valid.</p>
 *
 * <p>{@link #problem()} tells why the values cannot make an edition yet: the key of the message of the user interface,
 * from the code of the domain ({@code error.validation.<code>}), or {@code form.date.missing}. The class holds no
 * JavaFX object: it is tested without the toolkit.</p>
 *
 * <p>Not thread-safe: confined to the JavaFX Application Thread.</p>
 */
final class EditionForm {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The key of the message of a missing date. */
  static final String MISSING_DATE = "form.date.missing";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The duplicated edition, or {@code null} for a new edition. */
  private final Edition source;
  /** The deals of the plan, in their order. */
  private final List<DealType> deals;
  /** The name of the edition. */
  private String name = "";
  /** The date of the edition, or {@code null} while not chosen. */
  private LocalDate date;
  /** The tournament mode. */
  private TournamentMode mode = TournamentMode.TEAM;
  /** The number of games of each participant. */
  private int games = Regulation.GAMES;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a form.
   *
   * @param source the duplicated edition, or {@code null} for a new edition.
   * @param deals the initial deals of the plan.
   */
  private EditionForm(final Edition source, final List<DealType> deals) {
    this.source = source;
    this.deals = new ArrayList<>(deals);
  }

  /**
   * Creates the form of a new edition, with the settings of the regulation.
   *
   * @param today the proposed date.
   *
   * @return the form.
   */
  static EditionForm create(final LocalDate today) {
    final EditionForm form = new EditionForm(null, DealPlan.REGULATION.deals());
    form.date = today;
    return form;
  }

  /**
   * Creates the form of the duplication of an edition: its mode, games and plan, an empty name, and its date one year
   * later.
   *
   * @param edition the duplicated edition.
   *
   * @return the form.
   */
  static EditionForm duplicate(final Edition edition) {
    final EditionForm form = new EditionForm(edition, edition.deals().deals());
    form.mode = edition.mode();
    form.games = edition.games();
    form.date = edition.date().plusYears(1);
    return form;
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Tells whether the form duplicates an edition, i.e. the mode cannot be changed.
   *
   * @return {@code true} for a duplication.
   */
  boolean isDuplicate() {
    return source != null;
  }

  /**
   * Returns the name of the edition.
   *
   * @return the name, as typed.
   */
  String name() {
    return name;
  }

  /**
   * Changes the name of the edition.
   *
   * @param newName the name, as typed.
   */
  void setName(final String newName) {
    name = newName;
  }

  /**
   * Returns the date of the edition.
   *
   * @return the date, or {@code null} while not chosen.
   */
  LocalDate date() {
    return date;
  }

  /**
   * Changes the date of the edition.
   *
   * @param newDate the date, or {@code null}.
   */
  void setDate(final LocalDate newDate) {
    date = newDate;
  }

  /**
   * Returns the tournament mode.
   *
   * @return the mode.
   */
  TournamentMode mode() {
    return mode;
  }

  /**
   * Changes the tournament mode; ignored for a duplication.
   *
   * @param newMode the mode.
   */
  void setMode(final TournamentMode newMode) {
    if (!isDuplicate()) {
      mode = newMode;
    }
  }

  /**
   * Returns the number of games of each participant.
   *
   * @return the number of games.
   */
  int games() {
    return games;
  }

  /**
   * Changes the number of games of each participant.
   *
   * @param newGames the number of games.
   */
  void setGames(final int newGames) {
    games = newGames;
  }

  /**
   * Returns the deals of the plan.
   *
   * @return an unmodifiable copy of the deals, in their order.
   */
  List<DealType> deals() {
    return List.copyOf(deals);
  }

  // *******************************************************************************************************************
  // Plan of the deals
  // *******************************************************************************************************************

  /**
   * Changes the type of a deal.
   *
   * @param index the index of the deal, from 0.
   * @param type the new type.
   */
  void setDeal(final int index, final DealType type) {
    deals.set(index, type);
  }

  /**
   * Replaces the plan by the plan of the regulation.
   */
  void useRegulation() {
    deals.clear();
    deals.addAll(DealPlan.REGULATION.deals());
  }

  /**
   * Adds a block of {@link Regulation#DEALS_PER_BLOCK} deals of forced trump at the end of the plan.
   */
  void addBlock() {
    deals.addAll(Collections.nCopies(Regulation.DEALS_PER_BLOCK, DealType.FORCED_TRUMP));
  }

  /**
   * Removes the last block of {@link Regulation#DEALS_PER_BLOCK} deals, keeping at least one block.
   */
  void removeBlock() {
    if (deals.size() > Regulation.DEALS_PER_BLOCK) {
      deals.subList(deals.size() - Regulation.DEALS_PER_BLOCK, deals.size()).clear();
    }
  }

  // *******************************************************************************************************************
  // Result
  // *******************************************************************************************************************

  /**
   * Tells why the values cannot make an edition.
   *
   * @return the key of the message for the user, or nothing if the edition can be created.
   */
  Optional<String> problem() {
    if (date == null) {
      return Optional.of(MISSING_DATE);
    }
    try {
      build();
      return Optional.empty();
    } catch (final DomainException e) {
      return Optional.of("error.validation." + e.getError().name());
    }
  }

  /**
   * Creates the edition of the values: a new edition, or the duplicate of the source edition with the values of the
   * form.
   *
   * @return the edition, without registration nor schedule.
   *
   * @throws NullPointerException if the date is missing.
   * @throws DomainException if a value is not valid (blank name, invalid character, number of games lower than 1).
   */
  Edition build() {
    final Edition created = isDuplicate() ? source.duplicate(name, date) : Edition.create(name, date, mode);
    return created.withGames(games).withDeals(new DealPlan(deals));
  }

}
