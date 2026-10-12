package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.Participation;
import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamGame;
import ch.varani.jass.domain.TeamRegistrations;
import ch.varani.jass.domain.TeamRound;
import ch.varani.jass.domain.TeamSchedule;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.DirectoryStore;
import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.EditionStore;
import ch.varani.jass.solver.SolverOptions;
import ch.varani.jass.solver.SolverResult;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static ch.varani.jass.app.UiSupport.await;
import static ch.varani.jass.app.UiSupport.disabled;
import static ch.varani.jass.app.UiSupport.menu;
import static ch.varani.jass.app.UiSupport.press;
import static ch.varani.jass.app.UiSupport.shown;
import static ch.varani.jass.app.UiSupport.text;
import static ch.varani.jass.app.UiSupport.type;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the registration and of the schedule (phase 10): entry of the players from the directory or typed,
 * previous team, same person, corrections, removal, computation of the schedule (found, impossible, cancelled,
 * failed), schedule shown, PDF documents, reopening; in French, on a temporary data folder with fictitious persons.
 */
@ExtendWith(ApplicationExtension.class)
final class RegistrationTest {

  /** The edition whose first team was corrected in the directory. */
  private static final String CORRECTED = "Match corrigé";
  /** The number of games of the editions of the tests. */
  private static final int GAMES = 2;
  /** The number of registrations once two teams have been added. */
  private static final int FOUR = 4;
  /** The number of players of the individual edition, the smallest full edition of two games. */
  private static final int PLAYERS = 8;
  /** The number of documents exported. */
  private static final int DOCUMENTS = 4;
  /** The points of the giver in the deal of the played edition. */
  private static final int POINTS = 100;
  /** The tolerance of the comparison of the progress of the computation. */
  private static final double DELTA = 1e-9;

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** Another temporary folder, for the documents. */
  @TempDir
  private Path other;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();
  /** The computation of the schedule used by the application, replaced by some tests. */
  private final AtomicReference<Function<Edition, Solving>> solving = new AtomicReference<>(Solving::of);
  /** The person Anne of the directory. */
  private Person anne;
  /** The person Hugo of the directory. */
  private Person hugo;

  /**
   * Fills the data folder: a directory where Anne and Bruno played together in 2025, a deceased person, and three
   * editions: two teams registered, three teams ready to be scheduled, and eight players of an individual edition.
   *
   * @param stage the stage provided by TestFX.
   *
   * @throws Exception never.
   */
  @Start
  void start(final Stage stage) throws Exception {
    final UUID previous = UUID.randomUUID();
    final Person bruno = Person.create("Bruno", "Rochat");
    anne = Person.create("Anne", "Fontaine").withParticipations(List.of(
        new Participation(previous, "Match 2025", UiSupport.TODAY.minusYears(1), bruno.id())));
    hugo = Person.create("Hugo", "Meyer");
    new DirectoryStore(folder).save(Directory.empty().add(anne).add(bruno).add(hugo)
        .add(Person.create("Anna", "Fontana").withStatus(ContactStatus.DECEASED, UiSupport.TODAY)));
    final EditionStore store = new EditionStore(folder);
    store.create(teams("Match 2026", 2));
    store.create(teams("Match prêt", GAMES + 1));
    store.create(teams("Match joué", GAMES + 1)
        .withSchedule(new TeamSchedule(GAMES + 1, GAMES, List.of(new TeamRound(1, List.of(new TeamGame(1, 1, 2))))))
        .withResults(Results.EMPTY.withSheet(ScoreSheet.empty(1, 1, DealPlan.REGULATION)
                                                       .withDeal(1, DealScore.of(POINTS)))));
    final List<Player> players = new ArrayList<>();
    for (int i = 1; i <= PLAYERS; i++) {
      players.add(new Player("Joueur" + i, "Test"));
    }
    store.create(Edition.create(CORRECTED, UiSupport.TODAY, TournamentMode.TEAM).withGames(GAMES)
                        .withRegistrations(TeamRegistrations.empty()
                            .add(new Team(new Player("Anne", "Fontaine-Ancien", anne.id(), false), bruno.asPlayer()))
                            .add(new Team(hugo.asPlayer(), new Player("Iris", "Absente", UUID.randomUUID(), false)))
                            .add(new Team(new Player("Jules", "Libre"), new Player("Karl", "Libre")))));
    store.create(Edition.create("Individuel", UiSupport.TODAY, TournamentMode.INDIVIDUAL).withGames(GAMES)
                        .withRegistrations(new IndividualRegistrations(players)));
    new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(), UiSupport.CLOCK,
                                       edition -> solving.get().apply(edition), false, FakeUpdater.NONE)).start(stage);
  }

  /**
   * Creates a team edition with fictitious teams not linked to the directory.
   *
   * @param name the name of the edition.
   * @param count the number of teams.
   *
   * @return the edition.
   */
  private static Edition teams(final String name, final int count) {
    TeamRegistrations teams = TeamRegistrations.empty();
    for (int i = 1; i <= count; i++) {
      teams = teams.add(new Team(new Player("Claire" + i, "Dubois"), new Player("David" + i, "Petit")));
    }
    return Edition.create(name, UiSupport.TODAY, TournamentMode.TEAM).withGames(GAMES).withRegistrations(teams);
  }

  /**
   * Opens an edition from the start screen.
   *
   * @param robot the TestFX robot.
   * @param name the name of the edition.
   *
   * @throws Exception if the edition is not opened in time.
   */
  @SuppressWarnings("unchecked")
  private static void open(final FxRobot robot, final String name) throws Exception {
    await(() -> shown(robot, "openButton"));
    final TableView<EditionHeader> table = robot.lookup("#table").queryAs(TableView.class);
    robot.interact(() -> table.getSelectionModel().select(table.getItems().stream()
        .filter(header -> header.name().equals(name)).findFirst().orElseThrow()));
    press(robot, "openButton");
    await(() -> shown(robot, "count"));
  }

  /**
   * Returns the table of the registrations.
   *
   * @param robot the TestFX robot.
   *
   * @return the table.
   */
  @SuppressWarnings("unchecked")
  private static TableView<Integer> registrations(final FxRobot robot) {
    return robot.lookup("#table").queryAs(TableView.class);
  }

  /**
   * Returns the text of a cell of the table of the registrations.
   *
   * @param robot the TestFX robot.
   * @param row the index of the row.
   * @param column the index of the column.
   *
   * @return the text.
   */
  private static String cell(final FxRobot robot, final int row, final int column) {
    final TableView<Integer> table = registrations(robot);
    return String.valueOf(table.getColumns().get(column).getCellData(row));
  }

  /**
   * Returns the proposals of the directory for a player.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the list.
   *
   * @return the list.
   */
  @SuppressWarnings("unchecked")
  private static ListView<Person> proposals(final FxRobot robot, final String id) {
    return robot.lookup("#" + id).queryAs(ListView.class);
  }

  /**
   * F5, F14: a team is registered from the directory with the previous team mate; a typed player with the names of a
   * person of the directory is that person when the user says so, a new player becomes a person of the directory;
   * invalid entries are explained; a registration is corrected and removed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F5, F14: teams registered from the directory, with the previous team mate and new persons")
  void teamTest(final FxRobot robot) throws Exception {
    open(robot, "Match 2026");
    final String counted = text(robot, "count");
    final boolean solveDisabled = disabled(robot, "solveButton");
    type(robot, "first1", "ann");
    final List<String> offered = proposals(robot, "proposals1").getItems().stream().map(Person::fullName).toList();
    robot.interact(() -> proposals(robot, "proposals1").getSelectionModel().select(0));
    WaitForAsyncUtils.waitForFxEvents();
    final String linked = text(robot, "link1");
    final String previous = robot.lookup("#previousButton").queryButton().getText();
    press(robot, "previousButton");
    press(robot, "addButton");
    await(() -> registrations(robot).getItems().size() == GAMES + 1);
    type(robot, "first1", "Hugo");
    type(robot, "last1", "Meyer");
    type(robot, "first2", "Ivan");
    type(robot, "last2", "Nouveau");
    final String fresh = text(robot, "link2");
    natives.answer(true);
    press(robot, "addButton");
    await(() -> registrations(robot).getItems().size() == FOUR);
    await(() -> Files.size(folder.resolve("directory.jassdir")) > 0
                && new DirectoryStore(folder).load().search("Ivan").size() == 1);
    final String player = cell(robot, FOUR - 1, 1);
    type(robot, "first1", " ");
    press(robot, "addButton");
    final String blank = text(robot, "problem");
    type(robot, "first1", "Claire1");
    type(robot, "last1", "Dubois");
    type(robot, "first2", "Autre");
    type(robot, "last2", "Joueur");
    press(robot, "addButton");
    final String duplicate = text(robot, "problem");
    robot.interact(() -> registrations(robot).getSelectionModel().select(0));
    WaitForAsyncUtils.waitForFxEvents();
    final String selected = text(robot, "first1");
    type(robot, "last1", "Dubois-Martin");
    press(robot, "modifyButton");
    await(() -> cell(robot, 0, 1).equals("Claire1 Dubois-Martin"));
    robot.interact(() -> registrations(robot).getSelectionModel().select(0));
    natives.answer(false);
    press(robot, "removeButton");
    final int kept = registrations(robot).getItems().size();
    natives.answer(true);
    press(robot, "removeButton");
    await(() -> registrations(robot).getItems().size() == GAMES + 1);
    robot.interact(() -> registrations(robot).getSelectionModel().select(0));
    robot.interact(() -> registrations(robot).getSelectionModel().clearSelection());
    press(robot, "newButton");
    assertAll(
        () -> assertEquals("2 équipes inscrites (au moins 3 pour calculer les rencontres)", counted),
        () -> assertTrue(solveDisabled),
        () -> assertEquals(List.of("Anne Fontaine"), offered),
        () -> assertEquals("de l’annuaire", linked),
        () -> assertEquals("Reprendre l’équipe précédente\navec Bruno Rochat", previous),
        () -> assertEquals("nouvelle personne de l’annuaire", fresh),
        () -> assertEquals("Hugo Meyer", player),
        () -> assertEquals("Indiquez le prénom du joueur.", blank),
        () -> assertEquals("Cette personne est déjà inscrite.", duplicate),
        () -> assertEquals("Claire1", selected),
        () -> assertEquals(FOUR, kept),
        () -> assertEquals("", text(robot, "first1")),
        () -> assertEquals(hugo.id(), saved("Match 2026").teamRegistrations().get(GAMES + 1).players().get(0)
                                                          .personId()));
  }

  /**
   * F5, F14: a registration whose player was corrected in the directory is marked; selecting it says that Correct
   * takes the correction, and Shortcut+S corrects it; Shortcut+N then Shortcut+S register a new team; once the
   * registration is closed, Shortcut+S without selection adds nothing.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F5, F14: corrections of the directory are marked; Shortcut+S registers the entry")
  void correctionTest(final FxRobot robot) throws Exception {
    open(robot, CORRECTED);
    final int marked = robot.lookup(".table-row-cell.warned").queryAll().size();
    final boolean hiddenHint = !robot.lookup("#outdated").query().isVisible();
    final String tooltip = robot.lookup(".table-row-cell.warned .label").queryLabeled().getTooltip().getText();
    robot.interact(() -> registrations(robot).getSelectionModel().select(0));
    WaitForAsyncUtils.waitForFxEvents();
    final boolean hint = robot.lookup("#outdated").query().isVisible();
    final String corrected = text(robot, "last1");
    menu(robot, "saveItem");
    await(() -> cell(robot, 0, 1).equals("Anne Fontaine"));
    await(() -> robot.lookup(".table-row-cell.warned").queryAll().isEmpty());
    menu(robot, "newItem");
    type(robot, "first1", "Léa");
    type(robot, "last1", "Nouvelle");
    type(robot, "first2", "Marc");
    type(robot, "last2", "Nouveau");
    menu(robot, "saveItem");
    await(() -> registrations(robot).getItems().size() == FOUR);
    menu(robot, "listItem");
    open(robot, "Match joué");
    final int before = registrations(robot).getItems().size();
    menu(robot, "saveItem");
    assertAll(
        () -> assertEquals(1, marked),
        () -> assertTrue(hiddenHint),
        () -> assertEquals("Noms corrigés dans l’annuaire", tooltip),
        () -> assertTrue(hint),
        () -> assertEquals("Fontaine", corrected),
        () -> assertEquals("Anne Fontaine", saved(CORRECTED).teamRegistrations().get(1).players().get(0).fullName()),
        () -> assertEquals("Léa Nouvelle", saved(CORRECTED).teamRegistrations().get(FOUR).players().get(0)
                                                            .fullName()),
        () -> assertEquals(before, registrations(robot).getItems().size()));
  }

  /**
   * F5, F14: the persons who already play in the edition are not proposed, except the players of the registration
   * being corrected; the person chosen for the other player of the team is not proposed either.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F5, F14: the persons registered in the edition are not proposed again")
  void registeredTest(final FxRobot robot) throws Exception {
    open(robot, CORRECTED);
    type(robot, "first1", "Hugo");
    final List<Person> registered = List.copyOf(proposals(robot, "proposals1").getItems());
    final boolean hidden = !proposals(robot, "proposals1").isVisible();
    type(robot, "first1", "");
    robot.interact(() -> registrations(robot).getSelectionModel().select(0));
    WaitForAsyncUtils.waitForFxEvents();
    type(robot, "first2", "Bru");
    type(robot, "last2", "");
    final List<String> corrected = proposals(robot, "proposals2").getItems().stream().map(Person::fullName).toList();
    type(robot, "first2", "Fon");
    final List<String> teamMate = proposals(robot, "proposals2").getItems().stream().map(Person::fullName).toList();
    assertAll(
        () -> assertEquals(List.of(), registered),
        () -> assertTrue(hidden),
        () -> assertEquals(List.of("Bruno Rochat"), corrected),
        () -> assertEquals(List.of(), teamMate));
  }

  /**
   * F6: the schedule cannot be found (reason shown), the computation fails (error shown) or is cancelled; then it is
   * found, shown per round with the teams sitting out, its documents exported, and the registration reopened.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F6: the schedule is computed, cancelled, shown, exported, and the registration reopened")
  void scheduleTest(final FxRobot robot) throws Exception {
    open(robot, "Match prêt");
    solving.set(edition -> fixed(new SolverResult.Unsolved<>(SolverResult.Reason.INFEASIBLE)));
    press(robot, "solveButton");
    await(() -> text(robot, "solveStatus").startsWith("Aucun"));
    final String infeasible = text(robot, "solveStatus");
    solving.set(edition -> failing());
    press(robot, "solveButton");
    await(() -> natives.texts().contains("Une erreur inattendue est survenue."));
    final CountDownLatch started = new CountDownLatch(1);
    solving.set(edition -> blocking(started));
    press(robot, "solveButton");
    started.await(UiSupport.WAIT, TimeUnit.SECONDS);
    final boolean progress = robot.lookup("#progress").query().isVisible();
    await(() -> text(robot, "solveStatus").startsWith("Calcul des rencontres : 1 s"));
    final String elapsed = text(robot, "solveStatus");
    final double done = robot.lookup("#progress").queryAs(ProgressBar.class).getProgress();
    press(robot, "cancelButton");
    await(() -> text(robot, "solveStatus").equals("Le calcul a été annulé."));
    solving.set(Solving::of);
    press(robot, "solveButton");
    await(() -> shown(robot, "rounds") && robot.lookup(".round").queryAll().size() == GAMES + 1);
    final boolean scheduleShown = robot.lookup("#scheduleNav").queryAs(ToggleButton.class).isSelected();
    final List<String> rounds = robot.lookup(".round .section-head").queryAllAs(Label.class).stream()
                                     .map(Label::getText).toList();
    final List<File> documents = new ArrayList<>();
    for (final String button : List.of("scheduleButton", "placesButton", "cardsButton", "sheetsButton")) {
      final File file = other.resolve(button + ".pdf").toFile();
      documents.add(file);
      natives.choose(file);
      press(robot, button);
    }
    await(() -> documents.size() == DOCUMENTS && documents.stream().allMatch(File::isFile)
                && text(robot, "notice").equals("Le document a été enregistré."));
    final ToggleButton registration = robot.lookup("#registrationNav").queryAs(ToggleButton.class);
    robot.interact(registration::fire);
    await(() -> shown(robot, "addButton"));
    final boolean addDisabled = disabled(robot, "addButton");
    natives.answer(false);
    press(robot, "reopenButton");
    natives.answer(true);
    press(robot, "reopenButton");
    await(() -> !disabled(robot, "solveButton"));
    assertAll(
        () -> assertEquals("Aucun tableau ne respecte les règles pour ces inscriptions.", infeasible),
        () -> assertTrue(progress),
        () -> assertEquals("Calcul des rencontres : 1 s (au plus 60 s)…", elapsed),
        () -> assertEquals(1.0 / SolverOptions.DEFAULT_TIME_LIMIT.toSeconds(), done, DELTA),
        () -> assertTrue(scheduleShown),
        () -> assertEquals(List.of("Partie 1", "Partie 2", "Partie 3"), rounds),
        () -> assertTrue(documents.stream().allMatch(file -> {
          try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1).startsWith("%PDF");
          } catch (Exception _) {
            return false;
          }
        })),
        () -> assertTrue(addDisabled),
        () -> assertFalse(robot.lookup("#reopenButton").query().isVisible()));
  }

  /**
   * F12: in individual mode, one player is registered at a time, and the schedule shows the partners.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("F12: individual mode registers players one at a time and schedules the partners")
  void individualTest(final FxRobot robot) throws Exception {
    open(robot, "Individuel");
    final boolean secondShown = robot.lookup("#slot2").query().isVisible();
    final String header = registrations(robot).getColumns().get(1).getText();
    type(robot, "first1", "Anne");
    type(robot, "last1", "Fontaine");
    natives.answer(false);
    press(robot, "addButton");
    await(() -> registrations(robot).getItems().size() == PLAYERS + 1);
    robot.interact(() -> registrations(robot).getSelectionModel().select(PLAYERS));
    type(robot, "last1", "Fontaine-Neu");
    press(robot, "modifyButton");
    await(() -> cell(robot, PLAYERS, 1).equals("Anne Fontaine-Neu"));
    robot.interact(() -> registrations(robot).getSelectionModel().select(PLAYERS));
    natives.answer(true);
    press(robot, "removeButton");
    await(() -> registrations(robot).getItems().size() == PLAYERS);
    final long namesakes = new DirectoryStore(folder).load().persons().stream()
                                                    .filter(person -> person.fullName().equals("Anne Fontaine"))
                                                    .count();
    press(robot, "solveButton");
    await(() -> robot.lookup(".round").queryAll().size() == GAMES);
    @SuppressWarnings("unchecked")
    final TableView<Object> round = robot.lookup(".round .table-view").queryAs(TableView.class);
    final String side = String.valueOf(round.getColumns().get(2).getCellData(0));
    assertAll(
        () -> assertFalse(secondShown),
        () -> assertEquals(2L, namesakes),
        () -> assertEquals("Nom", header),
        () -> assertTrue(side.matches("Joueur\\d Test \\(\\d\\) & Joueur\\d Test \\(\\d\\)"), side));
  }

  /**
   * Without a readable directory, the players typed are registered without link, and nothing is proposed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("Without directory, the players are registered without link")
  void noDirectoryTest(final FxRobot robot) throws Exception {
    Files.writeString(folder.resolve("directory.jassdir"), "damaged");
    final Stage[] stage = new Stage[1];
    robot.interact(() -> {
      stage[0] = new Stage();
      new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(), UiSupport.CLOCK,
                                         Solving::of, false, FakeUpdater.NONE)).start(stage[0]);
    });
    await(() -> natives.texts().size() == 1 && robot.from(stage[0].getScene().getRoot()).lookup("#openButton")
                                                     .tryQuery().isPresent());
    final FxRobot window = new FxRobot();
    @SuppressWarnings("unchecked")
    final TableView<EditionHeader> list = window.from(stage[0].getScene().getRoot()).lookup("#table")
                                                .queryAs(TableView.class);
    robot.interact(() -> list.getSelectionModel().select(list.getItems().stream()
        .filter(header -> header.name().equals("Match 2026")).findFirst().orElseThrow()));
    robot.interact(() -> window.from(stage[0].getScene().getRoot()).lookup("#openButton").queryButton().fire());
    await(() -> window.from(stage[0].getScene().getRoot()).lookup("#first1").tryQuery().isPresent());
    robot.interact(() -> {
      window.from(stage[0].getScene().getRoot()).lookup("#first1").queryAs(TextField.class).setText("Anne");
      window.from(stage[0].getScene().getRoot()).lookup("#first2").queryAs(TextField.class).setText("Jean");
    });
    @SuppressWarnings("unchecked")
    final ListView<Person> offered = window.from(stage[0].getScene().getRoot()).lookup("#proposals1")
                                           .queryAs(ListView.class);
    final boolean none = offered.getItems().isEmpty();
    robot.interact(() -> window.from(stage[0].getScene().getRoot()).lookup("#addButton").queryButton().fire());
    await(() -> saved("Match 2026").registrations().size() == GAMES + 1);
    @SuppressWarnings("unchecked")
    final TableView<Integer> table = window.from(stage[0].getScene().getRoot()).lookup("#table")
                                           .queryAs(TableView.class);
    await(() -> table.getItems().size() == GAMES + 1);
    robot.interact(() -> table.getSelectionModel().select(0));
    final String shownName = window.from(stage[0].getScene().getRoot()).lookup("#first1").queryAs(TextField.class)
                                   .getText();
    robot.interact(stage[0]::close);
    assertAll(
        () -> assertTrue(none),
        () -> assertEquals("Claire1", shownName),
        () -> assertEquals(null, saved("Match 2026").teamRegistrations().get(GAMES + 1).players().get(0).personId()));
  }

  /**
   * An edition with results cannot be reopened: its registration stays closed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("An edition with results cannot be reopened")
  void playedTest(final FxRobot robot) throws Exception {
    open(robot, "Match joué");
    assertAll(
        () -> assertTrue(disabled(robot, "reopenButton")),
        () -> assertTrue(disabled(robot, "addButton")),
        () -> assertFalse(robot.lookup("#solveButton").query().isVisible()));
  }

  /**
   * Reads an edition saved in the data folder.
   *
   * @param name the name of the edition.
   *
   * @return the edition.
   *
   * @throws Exception if it cannot be read.
   */
  private Edition saved(final String name) throws Exception {
    final EditionStore store = new EditionStore(folder);
    return store.open(store.list().editions().stream().filter(header -> header.name().equals(name)).findFirst()
                           .orElseThrow().id());
  }

  /**
   * Returns a computation that gives a fixed result.
   *
   * @param result the result.
   *
   * @return the computation.
   */
  private static Solving fixed(final SolverResult<?> result) {
    return new Solving() {

      /**
       * Gives the result.
       *
       * @return the fixed result.
       */
      @Override
      public SolverResult<?> solve() {
        return result;
      }

      /**
       * Does nothing.
       */
      @Override
      public void cancel() {
        // Nothing to cancel.
      }

    };
  }

  /**
   * Returns a computation that fails.
   *
   * @return the computation.
   */
  private static Solving failing() {
    return new Solving() {

      /**
       * Fails.
       *
       * @return never.
       */
      @Override
      public SolverResult<?> solve() {
        throw new IllegalStateException("solver failure");
      }

      /**
       * Does nothing.
       */
      @Override
      public void cancel() {
        // Nothing to cancel.
      }

    };
  }

  /**
   * Returns a computation that waits until it is cancelled.
   *
   * @param started counted down when the computation starts.
   *
   * @return the computation.
   */
  private static Solving blocking(final CountDownLatch started) {
    final CountDownLatch cancelled = new CountDownLatch(1);
    return new Solving() {

      /**
       * Waits until cancelled.
       *
       * @return the cancellation.
       */
      @Override
      public SolverResult<?> solve() {
        started.countDown();
        try {
          cancelled.await(UiSupport.WAIT, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
        }
        return new SolverResult.Unsolved<>(SolverResult.Reason.CANCELLED);
      }

      /**
       * Ends the wait.
       */
      @Override
      public void cancel() {
        cancelled.countDown();
      }

    };
  }

}
