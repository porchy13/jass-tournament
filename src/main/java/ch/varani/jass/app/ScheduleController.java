package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.report.PlacesDocument;
import ch.varani.jass.report.ScheduleDocument;
import ch.varani.jass.report.ScoreCardsDocument;
import ch.varani.jass.report.ScoreSheetsDocument;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The schedule of an open edition (F6, F7, F10, F13): for each round, the tables with their places, the side that
 * deals first (rule 6) and the side that receives, and the participants sitting out (rule 1); and the exports of the
 * PDF documents: schedule, places to post, score cards, score sheets.
 *
 * <p><b>Layout</b> (DESIGN.md §6, §7): the title of the edition, then each round under a section head, with its
 * table and the participants sitting out; the documents are the tools of the toolbar. Before the registration is
 * closed, the view says that the schedule will be computed then and offers to go to the registration, and the
 * documents are disabled.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class ScheduleController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The height of a row of the tables of the rounds, in pixels (DESIGN.md §5). */
  private static final double ROW_HEIGHT = 44;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The edition. */
  private final Edition edition;
  /** The name and date of the edition. */
  @FXML
  private Label title;
  /** The settings of the edition. */
  @FXML
  private Label settings;
  /** The empty state shown before the schedule exists. */
  @FXML
  private VBox empty;
  /** The rounds of the schedule. */
  @FXML
  private VBox rounds;
  /** The toolbar of the documents. */
  @FXML
  private HBox documents;
  /** The buttons of the documents. */
  @FXML
  private Button scheduleButton;
  /** The button of the places. */
  @FXML
  private Button placesButton;
  /** The button of the score cards. */
  @FXML
  private Button cardsButton;
  /** The button of the score sheets. */
  @FXML
  private Button sheetsButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the screen of the schedule.
   *
   * @param main the main window.
   * @param edition the edition.
   */
  ScheduleController(final MainController main, final Edition edition) {
    this.main = main;
    this.edition = edition;
  }

  /**
   * Shows the rounds of the schedule, or the message telling that it is not computed yet; called by the FXML
   * loader.
   */
  @FXML
  void initialize() {
    title.setText(main.title(edition));
    settings.setText(main.describe(edition));
    for (final Button button : List.of(scheduleButton, placesButton, cardsButton, sheetsButton)) {
      button.setGraphic(Icon.DOCUMENT.graphic());
    }
    empty.setVisible(!edition.isScheduled());
    empty.setManaged(!edition.isScheduled());
    documents.setDisable(!edition.isScheduled());
    if (!edition.isScheduled()) {
      return;
    }
    final List<Pairing> pairings = edition.schedule().pairings();
    for (final int round : pairings.stream().map(Pairing::round).distinct().toList()) {
      final List<Pairing> games = pairings.stream().filter(pairing -> pairing.round() == round).toList();
      final TableView<Pairing> table = new TableView<>();
      column(table, "column.table", pairing -> String.valueOf(pairing.table()), true);
      column(table, "column.places", pairing -> (2 * pairing.table() - 1) + " – " + 2 * pairing.table(), true);
      column(table, "column.giver", pairing -> main.side(edition, pairing.giver()), false);
      column(table, "column.receiver", pairing -> main.side(edition, pairing.receiver()), false);
      table.getItems().setAll(games);
      table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
      table.setPrefHeight((games.size() + 1) * ROW_HEIGHT);
      final Label head = new Label(main.messages().get("schedule.round", round));
      head.getStyleClass().add("section-head");
      final VBox content = new VBox(head, table);
      content.getStyleClass().add("round");
      final String resting = resting(games);
      if (!resting.isEmpty()) {
        final Label sitting = new Label(main.messages().get("schedule.sitting.out", resting));
        sitting.getStyleClass().add("caption");
        content.getChildren().add(sitting);
      }
      rounds.getChildren().add(content);
    }
  }

  /**
   * Adds a column to the table of a round.
   *
   * @param table the table.
   * @param key the key of the title of the column.
   * @param text gives the text of a game.
   * @param number {@code true} for a column of numbers, aligned to the right.
   */
  private void column(final TableView<Pairing> table, final String key, final Function<Pairing, String> text,
                      final boolean number) {
    final TableColumn<Pairing, String> column = new TableColumn<>(main.messages().get(key));
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
    Views.style(column, Views.NUMBER, number);
    table.getColumns().add(column);
  }

  /**
   * Lists the participants sitting out a round.
   *
   * @param games the games of the round.
   *
   * @return their numbers, separated by commas, or an empty text if every participant plays.
   */
  private String resting(final List<Pairing> games) {
    return IntStream.rangeClosed(1, edition.registrations().size())
                    .filter(number -> games.stream().noneMatch(game -> game.giver().contains(number)
                                                                       || game.receiver().contains(number)))
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining(", "));
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Shows the registration, which closes by computing the schedule.
   */
  @FXML
  void showRegistration() {
    main.showPart(MainController.Place.REGISTRATION);
  }

  /**
   * Exports the schedule per round.
   */
  @FXML
  void exportSchedule() {
    main.exportDocument("document.schedule", edition, ScheduleDocument::write);
  }

  /**
   * Exports the places of the participants, to be posted.
   */
  @FXML
  void exportPlaces() {
    main.exportDocument("document.places", edition, PlacesDocument::write);
  }

  /**
   * Exports the score cards of the participants.
   */
  @FXML
  void exportCards() {
    main.exportDocument("document.cards", edition, ScoreCardsDocument::write);
  }

  /**
   * Exports the score sheets of the games.
   */
  @FXML
  void exportSheets() {
    main.exportDocument("document.sheets", edition, ScoreSheetsDocument::write);
  }

}
