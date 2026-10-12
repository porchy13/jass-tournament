package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Registrations;
import ch.varani.jass.domain.Schedule;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.solver.SolverResult;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The registration of an open edition (F5, F6, F14): the registered teams (or players in individual mode), the entry
 * of the players, chosen from the directory or typed, the closing of the registration by the computation of the
 * schedule, and its reopening.
 *
 * <p><b>Entry</b>: for each player, the user types the names; the persons of the directory matching them (accents and
 * case ignored, the deceased left out) are proposed, and choosing one links the player to it. In team mode, once the
 * first player is linked, its previous team mate is proposed to register the same team again. A typed player not
 * linked to the directory becomes a new person of the directory; when a person with the same names exists, the user
 * is asked whether it is the same; when only a deceased person has the same names, the user is asked whether it is
 * another person (a homonym, added to the directory), else nothing is registered. Selecting a registered row fills the
 * entry, to correct names (always allowed, the schedule is kept). A registration whose players were corrected in the
 * directory since is marked (warning colour and icon, {@link Views#warnings}); selecting it fills the entry with the
 * corrected names, and says that Correct takes them. A registration of a person declared deceased in the directory
 * since is marked too: selecting it says so, and it cannot be corrected until the person is replaced (it can still be
 * removed). Shortcut+S registers the entry: it corrects the selected registration, or adds a new one.</p>
 *
 * <p><b>Layout</b> (DESIGN.md §6): the registered participants at the left, the entry in the detail panel
 * ({@link DetailLayout}), floating when the window is narrow; the closing of the registration is the primary action of
 * the toolbar, the new registration (Shortcut+N) a tool; Delete removes the selected registration.</p>
 *
 * <p><b>Schedule</b>: once enough participants are registered, the schedule is computed by the CP-SAT solver in a
 * background task ({@link Task}), with a cancellation ({@link Solving#of(Edition)}: one worker in team mode,
 * reproducible, eight in individual mode); its progress is shown each second, as the time spent out of the time
 * limit of the solver, which the computation never exceeds. The schedule is then shown. The registration can be
 * reopened (schedule removed, a destructive action) as long as no result is entered (§3.3).</p>
 *
 * <p>Every change is saved at once ({@link MainController#save(MainController.OpenEdition)}).</p>
 *
 * <p>Confined to the JavaFX Application Thread, except the solving.</p>
 */
final class RegistrationController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The greatest number of persons proposed for a player. */
  static final int PROPOSALS = 8;
  /** The interval between two updates of the progress of the computation, in seconds. */
  private static final double TICK = 1;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The open edition. */
  private final MainController.OpenEdition open;
  /** The players being registered: two in team mode, one in individual mode. */
  private final List<PlayerSlot> slots = new ArrayList<>();
  /** The solving in progress, or {@code null}. */
  private Solving solving;
  /** Updates the progress of the solving in progress each second. */
  private final Timeline ticks = new Timeline();
  /** The seconds spent by the solving in progress. */
  private long elapsed;
  /** Whether the fields are being filled by the program, so that their listeners ignore the change. */
  private boolean filling;
  /** The layout of the detail panel. */
  private DetailLayout layout;
  /** The name and date of the edition. */
  @FXML
  private Label title;
  /** The settings of the edition. */
  @FXML
  private Label settings;
  /** The number of registrations and the minimum. */
  @FXML
  private Label count;
  /** The registered teams or players, by number. */
  @FXML
  private TableView<Integer> table;
  /** The first names of the players being registered. */
  @FXML
  private TextField first1;
  /** The last names of the players being registered. */
  @FXML
  private TextField last1;
  /** The proposals of the directory for the first player. */
  @FXML
  private ListView<Person> proposals1;
  /** Whether the first player is linked to the directory. */
  @FXML
  private Label link1;
  /** The first name of the second player. */
  @FXML
  private TextField first2;
  /** The last name of the second player. */
  @FXML
  private TextField last2;
  /** The proposals of the directory for the second player. */
  @FXML
  private ListView<Person> proposals2;
  /** Whether the second player is linked to the directory. */
  @FXML
  private Label link2;
  /** The entry of the second player, hidden in individual mode. */
  @FXML
  private Pane slot2;
  /** The button registering the previous team mate. */
  @FXML
  private Button previousButton;
  /** Why the entry cannot be registered. */
  @FXML
  private Label problem;
  /** Tells that the selected registration was corrected in the directory, or that one of its persons died. */
  @FXML
  private Label outdated;
  /** The button adding a registration. */
  @FXML
  private Button addButton;
  /** The button replacing the selected registration. */
  @FXML
  private Button modifyButton;
  /** The button removing the selected registration. */
  @FXML
  private Button removeButton;
  /** The button computing the schedule. */
  @FXML
  private Button solveButton;
  /** The progress of the solving: the time spent out of the time limit. */
  @FXML
  private ProgressBar progress;
  /** The state of the solving. */
  @FXML
  private Label solveStatus;
  /** The button cancelling the solving. */
  @FXML
  private Button cancelButton;
  /** The button reopening the registration. */
  @FXML
  private Button reopenButton;
  /** The button starting a new registration. */
  @FXML
  private Button newButton;
  /** The button closing the floating detail panel. */
  @FXML
  private Button closeDetail;
  /** The stack pane of the view. */
  @FXML
  private StackPane body;
  /** The split pane of the list and of the detail panel. */
  @FXML
  private SplitPane split;
  /** The detail panel: the entry of the players. */
  @FXML
  private ScrollPane detail;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the registration of an edition.
   *
   * @param main the main window.
   * @param open the open edition and its identifier.
   */
  RegistrationController(final MainController main, final MainController.OpenEdition open) {
    this.main = main;
    this.open = open;
    slots.add(new PlayerSlot());
    if (isTeam()) {
      slots.add(new PlayerSlot());
    }
  }

  /**
   * Fills the title, the table, the entry and the actions; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Edition edition = open.edition();
    final Messages messages = main.messages();
    title.setText(main.title(edition));
    settings.setText(main.describe(edition));
    Icon.PLUS.decorate(newButton, messages.get("registration.create"));
    Icon.CLOSE.decorate(closeDetail, messages.get("button.close"));
    layout = new DetailLayout(main, body, split, detail, closeDetail);
    table.setPlaceholder(Views.emptyState(messages.get("registration.empty"), null));
    Views.keys(table, null, edition.isScheduled() ? null : this::remove);
    count.setText(messages.get(isTeam() ? "registration.count.teams" : "registration.count.players",
                               edition.registrations().size(),
                               edition.registrations().minimumSize(edition.games())));
    Views.warnings(table, column(messages.get("column.number"), String::valueOf, true),
                   number -> outdated(number) || !deceased(players(number)).isEmpty(),
                   number -> messages.get(deceased(players(number)).isEmpty() ? "registration.outdated"
                                                                              : "registration.deceased"));
    for (int i = 0; i < slots.size(); i++) {
      final int player = i;
      column(messages.get(isTeam() ? "column.player" : "column.name", player + 1),
             number -> players(number).get(player).fullName(), false);
    }
    table.getItems().setAll(IntStream.rangeClosed(1, edition.registrations().size()).boxed().toList());
    table.getSelectionModel().selectedItemProperty().addListener((property, before, after) -> select(after));
    slot2.setVisible(isTeam());
    slot2.setManaged(isTeam());
    watch(0, first1, last1, proposals1);
    watch(1, first2, last2, proposals2);
    modifyButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    if (edition.isScheduled()) {
      removeButton.setDisable(true);
    } else {
      removeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }
    addButton.setDisable(edition.isScheduled());
    solveButton.setVisible(!edition.isScheduled());
    solveButton.setManaged(!edition.isScheduled());
    solveButton.setDisable(!edition.hasEnoughRegistrations());
    reopenButton.setVisible(edition.isScheduled());
    reopenButton.setManaged(edition.isScheduled());
    reopenButton.setDisable(edition.hasResults());
    ticks.getKeyFrames().setAll(new KeyFrame(Duration.seconds(TICK), event -> tick()));
    ticks.setCycleCount(Animation.INDEFINITE);
    showSolving(false);
    refresh();
  }

  /**
   * Adds a column to the table.
   *
   * @param titleText the title of the column.
   * @param text gives the text of a registration.
   * @param number {@code true} for a column of numbers, aligned to the right.
   *
   * @return the column.
   */
  private TableColumn<Integer, String> column(final String titleText, final IntFunction<String> text,
                                              final boolean number) {
    final TableColumn<Integer, String> column = new TableColumn<>(titleText);
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
    Views.style(column, Views.NUMBER, number);
    table.getColumns().add(column);
    return column;
  }

  /**
   * Follows the fields of a player: typing changes the slot and the proposals of the directory; choosing a proposal
   * links the player.
   *
   * @param index the index of the slot.
   * @param first the field of the first name.
   * @param last the field of the last name.
   * @param proposals the proposals of the directory.
   */
  private void watch(final int index, final TextField first, final TextField last, final ListView<Person> proposals) {
    proposals.setCellFactory(view -> new PersonCell(main.messages()));
    if (index >= slots.size()) {
      return;
    }
    final PlayerSlot slot = slots.get(index);
    first.textProperty().addListener((property, before, after) -> typed(slot, first, last));
    last.textProperty().addListener((property, before, after) -> typed(slot, first, last));
    proposals.getSelectionModel().selectedItemProperty().addListener((property, before, after) -> {
      if (after != null) {
        slot.link(after);
        refresh();
      }
    });
  }

  /**
   * Takes the names typed in the fields of a player.
   *
   * @param slot the slot of the player.
   * @param first the field of the first name.
   * @param last the field of the last name.
   */
  private void typed(final PlayerSlot slot, final TextField first, final TextField last) {
    if (!filling) {
      slot.setNames(first.getText(), last.getText());
      refresh();
    }
  }

  // *******************************************************************************************************************
  // Display
  // *******************************************************************************************************************

  /**
   * Shows the slots again: their names, their link to the directory, the proposals, the previous team mate.
   */
  private void refresh() {
    filling = true;
    show(slots.get(0), first1, last1, proposals1, link1);
    if (isTeam()) {
      show(slots.get(1), first2, last2, proposals2, link2);
    }
    filling = false;
    final Person mate = previousTeamMate();
    previousButton.setVisible(mate != null);
    previousButton.setManaged(mate != null);
    if (mate != null) {
      previousButton.setText(main.messages().get("registration.previous", mate.fullName()));
    }
    problem.setText("");
  }

  /**
   * Shows a slot in its fields.
   *
   * @param slot the slot.
   * @param first the field of the first name.
   * @param last the field of the last name.
   * @param proposals the proposals of the directory.
   * @param link the label of the link to the directory.
   */
  private void show(final PlayerSlot slot, final TextField first, final TextField last,
                    final ListView<Person> proposals, final Label link) {
    first.setText(slot.firstName());
    last.setText(slot.lastName());
    final Directory directory = main.directory();
    proposals.getItems().setAll(slot.person() != null || slot.isBlank() || directory == null
                                ? List.of() : proposals(slot, directory));
    proposals.setVisible(!proposals.getItems().isEmpty());
    proposals.setManaged(proposals.isVisible());
    final String linkKey = slot.person() == null ? "registration.new" : "registration.linked";
    link.setText(slot.isBlank() ? "" : main.messages().get(linkKey));
  }

  /**
   * Returns the persons of the directory proposed for a slot: those matching the names typed, except the deceased and
   * the persons who already play in the edition ({@link #taken(PlayerSlot)}), at most {@link #PROPOSALS}.
   *
   * @param slot the slot, with names typed and without person.
   * @param directory the directory.
   *
   * @return the proposals.
   */
  private List<Person> proposals(final PlayerSlot slot, final Directory directory) {
    final Set<UUID> taken = taken(slot);
    return directory.registrationCandidates(slot.query()).stream().filter(person -> !taken.contains(person.id()))
                    .limit(PROPOSALS).toList();
  }

  /**
   * Returns the persons that cannot be proposed for a slot, as they already play in the edition: the persons of the
   * registered players, except those of the registration being corrected, and the person chosen in the other slot.
   *
   * @param slot the slot whose proposals are shown.
   *
   * @return the identifiers of the persons.
   */
  private Set<UUID> taken(final PlayerSlot slot) {
    final Integer number = table.getSelectionModel().getSelectedItem();
    final List<Player> corrected = number == null ? List.of() : players(number);
    final Set<UUID> taken = new HashSet<>();
    open.edition().registrations().allPlayers().stream()
        .filter(player -> player.personId() != null && !corrected.contains(player))
        .forEach(player -> taken.add(player.personId()));
    slots.stream().filter(other -> other != slot && other.person() != null)
         .forEach(other -> taken.add(other.person().id()));
    return taken;
  }

  /**
   * Returns the previous team mate to propose: in team mode, when the first player is linked and the second is
   * empty.
   *
   * @return the person, or {@code null}.
   */
  private Person previousTeamMate() {
    final Directory directory = main.directory();
    if (!isTeam() || slots.get(0).person() == null || !slots.get(1).isBlank()) {
      return null;
    }
    return directory.find(slots.get(0).person().id())
                    .flatMap(person -> directory.lastTeamMate(person.id())).orElse(null);
  }

  /**
   * Fills the entry with a registered row, to correct it.
   *
   * @param number the number of the registration, or {@code null} without selection.
   */
  private void select(final Integer number) {
    if (number == null) {
      outdated.setVisible(false);
      outdated.setManaged(false);
      return;
    }
    final String dead = deceased(players(number));
    outdated.setText(dead.isEmpty() ? main.messages().get("registration.outdated.hint")
                                    : main.messages().get("registration.deceased.hint", dead));
    outdated.setVisible(outdated(number) || !dead.isEmpty());
    outdated.setManaged(outdated.isVisible());
    final List<Player> players = players(number);
    final Directory directory = main.directory();
    for (int i = 0; i < slots.size(); i++) {
      final Player player = players.get(i);
      slots.get(i).show(player, directory == null || player.personId() == null ? null
                                : directory.find(player.personId()).orElse(null));
    }
    refresh();
    layout.open();
  }

  /**
   * Tells whether the players of a registration were corrected in the directory since they were registered: the
   * person of one of them has other names.
   *
   * @param number the number of the registration.
   *
   * @return {@code true} if the names of a player differ from those of its person in the directory.
   */
  private boolean outdated(final int number) {
    final Directory directory = main.directory();
    return directory != null && players(number).stream().anyMatch(
        player -> player.personId() != null && directory.find(player.personId())
            .filter(person -> !List.of(person.firstName(), person.lastName())
                                   .equals(List.of(player.firstName(), player.lastName()))).isPresent());
  }

  /**
   * Names the players linked to a person declared deceased in the directory.
   *
   * @param players the players.
   *
   * @return their full names, joined by " &amp; ", empty when none died.
   */
  private String deceased(final List<Player> players) {
    final Directory directory = main.directory();
    if (directory == null) {
      return "";
    }
    return players.stream()
                  .filter(player -> player.personId() != null && directory.find(player.personId())
                      .filter(person -> person.status() == ContactStatus.DECEASED).isPresent())
                  .map(Player::fullName)
                  .collect(Collectors.joining(" & "));
  }

  /**
   * Shows or hides the progress and the cancellation of the solving; the progress starts at 0 and is updated each
   * second while the schedule is being computed.
   *
   * @param running {@code true} while the schedule is being computed.
   */
  private void showSolving(final boolean running) {
    progress.setVisible(running);
    progress.setManaged(running);
    cancelButton.setVisible(running);
    cancelButton.setManaged(running);
    if (!running) {
      ticks.stop();
      return;
    }
    solveButton.setDisable(true);
    elapsed = 0;
    showProgress();
    ticks.playFromStart();
  }

  /**
   * Counts a second of the solving in progress, and shows it.
   */
  private void tick() {
    elapsed++;
    showProgress();
  }

  /**
   * Shows the progress of the solving: the time spent, out of the time limit of the solver.
   */
  private void showProgress() {
    final long limit = solving.timeLimit().toSeconds();
    progress.setProgress(Math.min(1, (double) elapsed / limit));
    solveStatus.setText(main.messages().get("registration.solving", elapsed, limit));
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Registers the previous team mate of the first player as second player.
   */
  @FXML
  void previousTeam() {
    slots.get(1).link(previousTeamMate());
    refresh();
  }

  /**
   * Registers the entry as a new team or player.
   */
  @FXML
  void add() {
    register(null);
  }

  /**
   * Registers the entry from the keyboard (Shortcut+S): corrects the selected registration, or adds a new one while
   * the registration is open.
   */
  void save() {
    final Integer number = table.getSelectionModel().getSelectedItem();
    if (number != null) {
      register(number);
    } else if (!addButton.isDisabled()) {
      register(null);
    }
  }

  /**
   * Replaces the selected registration by the entry, e.g. to correct a name.
   */
  @FXML
  void modify() {
    register(table.getSelectionModel().getSelectedItem());
  }

  /**
   * Removes the selected registration, after confirmation; the following ones are renumbered.
   */
  @FXML
  void remove() {
    final Integer number = table.getSelectionModel().getSelectedItem();
    final String name = players(number).stream().map(Player::fullName).reduce((a, b) -> a + " & " + b).orElse("");
    if (main.dialogs().confirmDestructive("confirm.remove.registration", name)) {
      final Registrations registrations = switch (open.edition().registrations()) {
        case final TeamRegistrations teams -> teams.remove(number);
        case final IndividualRegistrations players -> players.remove(number);
      };
      main.save(new MainController.OpenEdition(open.id(), open.edition().withRegistrations(registrations)));
      main.notify("info.registration.removed", name);
    }
  }

  /**
   * Empties the entry for a new registration, and moves the focus to the first name of the first player.
   */
  @FXML
  void clear() {
    slots.forEach(PlayerSlot::clear);
    table.getSelectionModel().clearSelection();
    refresh();
    layout.open();
    first1.requestFocus();
  }

  /**
   * Registers the entry: the players typed become persons of the directory (or the existing person when the user
   * says so), then the registration is added or replaced and the edition saved. Nothing is registered when a player
   * is linked to a person declared deceased in the directory, or when the user does not confirm a homonym of a
   * deceased person.
   *
   * @param number the number of the replaced registration, or {@code null} to add one.
   */
  private void register(final Integer number) {
    final String dead = deceased(slots.stream().map(PlayerSlot::person).filter(Objects::nonNull)
                                      .map(Person::asPlayer).toList());
    if (!dead.isEmpty()) {
      problem.setText(main.messages().get("registration.deceased.hint", dead));
      return;
    }
    final List<Person> created = new ArrayList<>();
    final List<Player> players = new ArrayList<>();
    try {
      for (final PlayerSlot slot : slots) {
        final Player player = player(slot, created);
        if (player == null) {
          return;
        }
        players.add(player);
      }
      final Edition edition = open.edition().withRegistrations(registrations(players, number));
      if (!created.isEmpty()) {
        main.updateDirectory(directory -> directory.addAll(created), directory -> { });
      }
      main.save(new MainController.OpenEdition(open.id(), edition));
    } catch (final DomainException e) {
      problem.setText(main.messages().error(e));
    }
  }

  /**
   * Makes the player of a slot: the linked person, the existing person of the same names when the user confirms
   * it, or a new person of the directory; when a deceased person has the same names, the new person only when the
   * user confirms it is another person (a homonym).
   *
   * @param slot the slot.
   * @param created receives the new persons of the directory.
   *
   * @return the player, or {@code null} when the user did not confirm the homonym of a deceased person.
   *
   * @throws DomainException if the names are not valid.
   */
  private Player player(final PlayerSlot slot, final List<Person> created) {
    if (slot.person() != null) {
      return slot.person().asPlayer();
    }
    final Person person = Person.create(slot.firstName(), slot.lastName());
    final Directory directory = main.directory();
    if (directory == null) {
      return new Player(person.firstName(), person.lastName());
    }
    final Person same = directory.registrationCandidates("").stream()
                                 .filter(known -> known.asPlayer().hasSameNameAs(person.asPlayer()))
                                 .findFirst().orElse(null);
    if (same != null && main.dialogs().confirm("confirm.same.person", same.fullName())) {
      return same.asPlayer();
    }
    final boolean homonym = directory.persons().stream()
        .anyMatch(known -> known.status() == ContactStatus.DECEASED
                           && known.asPlayer().hasSameNameAs(person.asPlayer()));
    if (homonym && !main.dialogs().confirm("confirm.deceased.homonym", person.fullName())) {
      return null;
    }
    created.add(person);
    return person.asPlayer();
  }

  /**
   * Adds or replaces a registration.
   *
   * @param players the players of the registration.
   * @param number the number of the replaced registration, or {@code null} to add one.
   *
   * @return the new registrations.
   *
   * @throws DomainException if the registration is not valid (same person twice…).
   */
  private Registrations registrations(final List<Player> players, final Integer number) {
    return switch (open.edition().registrations()) {
      case final TeamRegistrations teams -> number == null ? teams.add(new Team(players))
                                                           : teams.replace(number, new Team(players));
      case final IndividualRegistrations individuals -> number == null ? individuals.add(players.getFirst())
                                                                       : individuals.replace(number,
                                                                                             players.getFirst());
    };
  }

  /**
   * Computes the schedule in a background task, which closes the registration once found.
   */
  @FXML
  void solve() {
    solving = main.solving(open.edition());
    final Solving running = solving;
    final Task<SolverResult<?>> task = new Task<>() {

      /**
       * Solves, on the background thread.
       *
       * @return the result of the solver.
       */
      @Override
      protected SolverResult<?> call() {
        return running.solve();
      }

    };
    task.setOnSucceeded(event -> solved(task.getValue()));
    task.setOnFailed(event -> {
      showSolving(false);
      solveButton.setDisable(false);
      main.dialogs().error(task.getException());
    });
    showSolving(true);
    Thread.ofPlatform().daemon().name("jass-solver").start(task);
  }

  /**
   * Takes the result of the solver: saves the schedule, or explains why there is none.
   *
   * @param result the result of the solver.
   */
  private void solved(final SolverResult<?> result) {
    showSolving(false);
    switch (result) {
      case SolverResult.Solved<?>(final Schedule schedule) -> {
        main.setSection(MainController.Place.SCHEDULE);
        main.save(new MainController.OpenEdition(open.id(), open.edition().withSchedule(schedule)));
      }
      case SolverResult.Unsolved<?>(final SolverResult.Reason reason) -> {
        solveButton.setDisable(false);
        solveStatus.setText(main.messages().get("solver." + reason.name()));
      }
    }
  }

  /**
   * Cancels the solving in progress.
   */
  @FXML
  void cancelSolving() {
    solving.cancel();
  }

  /**
   * Reopens the registration, after confirmation: the schedule is removed.
   */
  @FXML
  void reopen() {
    if (main.dialogs().confirmDestructive("confirm.reopen")) {
      main.save(new MainController.OpenEdition(open.id(), open.edition().withoutSchedule()));
    }
  }

  // *******************************************************************************************************************
  // Helpers
  // *******************************************************************************************************************

  /**
   * Tells whether the edition is in team mode.
   *
   * @return {@code true} in team mode.
   */
  private boolean isTeam() {
    return open.edition().mode() == TournamentMode.TEAM;
  }

  /**
   * Returns the players of a registration.
   *
   * @param number the number of the registration.
   *
   * @return the two players of a team, or the player.
   */
  private List<Player> players(final int number) {
    return switch (open.edition().registrations()) {
      case final TeamRegistrations teams -> teams.get(number).players();
      case final IndividualRegistrations players -> List.of(players.get(number));
    };
  }

}
