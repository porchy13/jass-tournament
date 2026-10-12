package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.TieBreak;
import ch.varani.jass.scoring.Ranking;
import ch.varani.jass.scoring.RankingEntry;
import ch.varani.jass.scoring.Scoring;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/**
 * The ranking of an open edition (F11, rules 14, 15 and 17), computed from the results each time it is shown, so that
 * it follows every score entered.
 *
 * <p><b>Layout</b> (DESIGN.md §6): the state of the ranking (provisional with the number of games with a result,
 * ties to break, final), then the ranking: rank, number, participant, its points in each game (empty while the game has
 * no result, or when the participant sits out), its total and its number of games with a result; then the
 * participants excluded for cheating, who can be readmitted. The drawing of lots is the primary action of the toolbar,
 * the exclusion of the selected participant a destructive tool.</p>
 *
 * <p><b>Drawing of lots</b> (rule 15): possible once every game has a result and while participants share a rank;
 * after confirmation, the application draws ({@link Scoring#drawLots()}) and stores the outcome, which ranks the tied
 * participants and closes the match: the sheets and the exclusions can no longer be changed. To correct a score, the
 * draw is cancelled first (a destructive action, confirmed), then done again once the scores are corrected.</p>
 *
 * <p><b>Exclusion</b> (rule 17): after confirmation, the selected participant loses all its games, its opponents
 * receive half of the points of a game, and it leaves the ranking; the exclusion can be cancelled.</p>
 *
 * <p>Every change is saved at once ({@link MainController#save(MainController.OpenEdition)}).</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class RankingController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The style class of the numeric columns, aligned to the right. */
  private static final String NUMBER = "number";
  /** The height of a row of the tables, in pixels (DESIGN.md §5). */
  private static final double ROW_HEIGHT = 44;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The open edition. */
  private final MainController.OpenEdition open;
  /** The scoring of the edition. */
  private final Scoring scoring;
  /** The ranking. */
  private final Ranking ranking;
  /** The name and date of the edition. */
  @FXML
  private Label title;
  /** The settings of the edition. */
  @FXML
  private Label settings;
  /** The state of the ranking. */
  @FXML
  private Label state;
  /** The lines of the ranking. */
  @FXML
  private TableView<RankingEntry> table;
  /** The participants excluded for cheating, with their table. */
  @FXML
  private VBox excludedBox;
  /** The numbers of the participants excluded for cheating. */
  @FXML
  private TableView<Integer> excludedTable;
  /** The button drawing lots. */
  @FXML
  private Button drawButton;
  /** The button cancelling the drawing of lots. */
  @FXML
  private Button cancelDrawButton;
  /** The button excluding the selected participant. */
  @FXML
  private Button excludeButton;
  /** The button readmitting the selected excluded participant. */
  @FXML
  private Button readmitButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the ranking of an edition.
   *
   * @param main the main window.
   * @param open the open edition and its identifier.
   */
  RankingController(final MainController main, final MainController.OpenEdition open) {
    this.main = main;
    this.open = open;
    this.scoring = new Scoring(open.edition());
    this.ranking = scoring.ranking();
  }

  /**
   * Fills the title, the state, the ranking and the excluded participants; before the schedule exists, the ranking
   * says that it is established once the schedule is computed. Called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Edition edition = open.edition();
    final Messages messages = main.messages();
    title.setText(main.title(edition));
    settings.setText(main.describe(edition));
    final Button registration = new Button(messages.get("schedule.registration"));
    registration.setId("registrationButton");
    registration.setOnAction(event -> main.showPart(MainController.Place.REGISTRATION));
    table.setPlaceholder(Views.emptyState(messages.get("ranking.empty"), registration));
    final boolean drawn = edition.results().tieBreak() != null;
    excludeButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(
        new SimpleBooleanProperty(drawn)));
    readmitButton.disableProperty().bind(excludedTable.getSelectionModel().selectedItemProperty().isNull().or(
        new SimpleBooleanProperty(drawn)));
    drawButton.setDisable(!ranking.complete() || !ranking.hasTies());
    drawButton.setVisible(!drawn);
    drawButton.setManaged(!drawn);
    cancelDrawButton.setVisible(drawn);
    cancelDrawButton.setManaged(drawn);
    final List<Integer> excluded = edition.results().excluded();
    excludedBox.setVisible(!excluded.isEmpty());
    excludedBox.setManaged(!excluded.isEmpty());
    excludedTable.setPrefHeight((excluded.size() + 1) * ROW_HEIGHT);
    column(excludedTable, messages.get("column.number"), String::valueOf, true);
    column(excludedTable, messages.get("ranking.column.participant"), this::name, false);
    excludedTable.getItems().setAll(excluded);
    if (!edition.isScheduled()) {
      return;
    }
    showState();
    column(table, messages.get("ranking.column.rank"), entry -> String.valueOf(entry.rank()), true);
    column(table, messages.get("column.number"), entry -> String.valueOf(entry.participant()), true);
    column(table, messages.get("ranking.column.participant"), entry -> name(entry.participant()), false);
    final List<Pairing> pairings = edition.schedule().pairings();
    for (final int round : pairings.stream().map(Pairing::round).distinct().toList()) {
      column(table, messages.get("ranking.column.game", round), entry -> points(entry.participant(), round), true);
    }
    column(table, messages.get("ranking.column.total"), entry -> messages.number(entry.points()), true);
    column(table, messages.get("ranking.column.games"), entry -> String.valueOf(entry.games()), true);
    table.getItems().setAll(ranking.entries());
  }

  /**
   * Adds a column to a table.
   *
   * @param <T> the type of the lines.
   * @param target the table.
   * @param heading the title of the column.
   * @param text gives the text of a line.
   * @param number {@code true} for a numeric column, aligned to the right.
   */
  private static <T> void column(final TableView<T> target, final String heading, final Function<T, String> text,
                                 final boolean number) {
    final TableColumn<T, String> column = new TableColumn<>(heading);
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
    Views.style(column, NUMBER, number);
    target.getColumns().add(column);
  }

  /**
   * Shows the state of the ranking: provisional, ties to break by drawing lots, or final.
   */
  private void showState() {
    final Messages messages = main.messages();
    Views.style(state, "warning", ranking.complete() && ranking.hasTies());
    if (!ranking.complete()) {
      state.setText(messages.get("ranking.provisional", scoring.results().size(),
                                 open.edition().schedule().pairings().size()));
    } else if (ranking.hasTies()) {
      state.setText(messages.get("ranking.ties"));
    } else {
      final TieBreak draw = open.edition().results().tieBreak();
      state.setText(messages.get(draw == null || draw.order().isEmpty() ? "ranking.final" : "ranking.final.drawn"));
    }
  }

  // *******************************************************************************************************************
  // Helpers
  // *******************************************************************************************************************

  /**
   * Names a participant: the team with its players, or the player with its number.
   *
   * @param participant the number of the participant.
   *
   * @return its name.
   */
  private String name(final int participant) {
    return main.side(open.edition(), List.of(participant));
  }

  /**
   * Returns the points of a participant in a round.
   *
   * @param participant the number of the participant.
   * @param round the number of the round.
   *
   * @return the points of its game of the round, or an empty text if it has no result or sits out.
   */
  private String points(final int participant, final int round) {
    return scoring.points(participant, round).map(main.messages()::number).orElse("");
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Rule 15: draws lots between the tied participants, after confirmation, and stores the outcome.
   */
  @FXML
  void draw() {
    if (main.dialogs().confirm("confirm.draw")) {
      final Results results = open.edition().results().withTieBreak(scoring.drawLots());
      main.save(new MainController.OpenEdition(open.id(), open.edition().withResults(results)));
      main.notify("info.drawn");
    }
  }

  /**
   * Cancels the drawing of lots, after confirmation, so that the scores can be corrected; the lots are drawn again
   * afterwards.
   */
  @FXML
  void cancelDraw() {
    if (main.dialogs().confirmDestructive("confirm.cancel.draw")) {
      final Results results = open.edition().results().withTieBreak(null);
      main.save(new MainController.OpenEdition(open.id(), open.edition().withResults(results)));
      main.notify("info.draw.cancelled");
    }
  }

  /**
   * Rule 17: excludes the selected participant for cheating, after confirmation.
   */
  @FXML
  void exclude() {
    final int participant = table.getSelectionModel().getSelectedItem().participant();
    final String name = name(participant);
    if (main.dialogs().confirmDestructive("confirm.exclude", name,
                                          main.messages().number(open.edition().deals().forfeitPoints()))) {
      exclusion(participant, true);
      main.notify("info.excluded", name);
    }
  }

  /**
   * Cancels the exclusion of the selected excluded participant.
   */
  @FXML
  void readmit() {
    final int participant = excludedTable.getSelectionModel().getSelectedItem();
    exclusion(participant, false);
    main.notify("info.readmitted", name(participant));
  }

  /**
   * Excludes a participant or cancels its exclusion, and saves the edition.
   *
   * @param participant the number of the participant.
   * @param excluded {@code true} to exclude it.
   */
  private void exclusion(final int participant, final boolean excluded) {
    final Results results = open.edition().results().withExclusion(participant, excluded);
    main.save(new MainController.OpenEdition(open.id(), open.edition().withResults(results)));
  }

}
