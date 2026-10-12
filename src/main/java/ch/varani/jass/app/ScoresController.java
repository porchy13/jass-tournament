package ch.varani.jass.app;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Pairing;
import ch.varani.jass.domain.Regulation;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.scoring.GameResult;
import ch.varani.jass.scoring.Scoring;
import ch.varani.jass.scoring.SheetTotals;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The scores of an open edition (F9, rules 2, 4, 16, 17 and 19): the games of the schedule with their results, and
 * the score sheet of the selected game, entered from the paper sheet.
 *
 * <p><b>Layout</b> (DESIGN.md §6): the games at the left, in the order of the schedule, each with its sides and its
 * result; the score sheet of the selected game in the detail panel ({@link DetailLayout}), as the paper sheet: one
 * line per deal of the plan with its number, its type and the points of column A (the giver side, which deals first,
 * rule 6) and of column B, a subtotal after each block of {@link Regulation#DEALS_PER_BLOCK} deals, the totals, the
 * rule 16 deduction when the total exceeds the points of a game, and the points counted in the ranking. Each side can
 * be marked absent (rule 19); the game is then a forfeit, as for a side whose participant is excluded (rule 17, from
 * the ranking). The tool of the toolbar goes to the next game without result.</p>
 *
 * <p><b>Entry</b> ({@link ScoreEntry}): the user types column A; column B shows the derived value
 * ({@code 157 − A}, style class {@value #DERIVED}) until it is typed. A value is taken when the field is left or on
 * Enter, which moves to the next deal; the arrows Up and Down move between the deals of a column. A value that is not
 * a number between 0 and 157 is rejected with a message; the deals whose columns do not sum to 157 are highlighted
 * (style class {@value #INCONSISTENT}) and listed under the sheet, never rejected, and the game is marked in the list
 * (warning colour and icon, {@link Views#warnings}). Enter in the list of the games moves to the first
 * deal of the sheet; Delete clears the sheet, after confirmation.</p>
 *
 * <p><b>Lock</b>: once the lots are drawn (rule 15), the match is closed: the sheets can no longer be changed, until
 * the draw is cancelled from the ranking.</p>
 *
 * <p><b>Saving</b>: every value taken is saved at once ({@link MainController#update(MainController.OpenEdition)}),
 * without showing the view again, so that the focus stays in the sheet; the results of the list are recomputed
 * ({@link Scoring}). A sheet where nothing is entered is removed from the results.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class ScoresController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The style class of a column derived from the other one. */
  static final String DERIVED = "derived";
  /** The style class of the fields of a deal whose columns do not sum to the points of a deal. */
  static final String INCONSISTENT = "inconsistent";
  /** The style class of a field whose text is rejected. */
  static final String INVALID = "invalid";
  /** The column of the grid of the sheet holding column A, after the number and the type of the deal. */
  private static final int GIVER_COLUMN = 2;
  /** The column of the grid of the sheet holding column B. */
  private static final int RECEIVER_COLUMN = 3;
  /** The width of the fields of the points, in characters: up to 157. */
  private static final int POINTS_WIDTH = 3;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The identifier of the edition file. */
  private final UUID id;
  /** The edition, replaced at each change. */
  private Edition edition;
  /** The scoring of the edition, replaced at each change. */
  private Scoring scoring;
  /** Whether the lots are drawn, which closes the sheets. */
  private final boolean locked;
  /** The game of the sheet shown, or {@code null} without selection. */
  private Pairing pairing;
  /** The fields of the points: one line per deal, column A then column B. */
  private TextField[][] fields;
  /** The subtotals of each block: one line per block, column A then column B. */
  private Label[][] subtotals;
  /** The totals before correction, column A then column B. */
  private final Label[] totals = {new Label(), new Label()};
  /** The rule 16 deductions, column A then column B. */
  private final Label[] deductions = {new Label(), new Label()};
  /** The title of the line of the rule 16 deduction, shown only when there is one. */
  private final Label deductionTitle = new Label();
  /** The layout of the detail panel. */
  private DetailLayout layout;
  /** The name and date of the edition. */
  @FXML
  private Label title;
  /** The settings of the edition. */
  @FXML
  private Label settings;
  /** The number of games with a result. */
  @FXML
  private Label count;
  /** The games of the schedule. */
  @FXML
  private TableView<Pairing> table;
  /** The button going to the next game without result. */
  @FXML
  private Button nextButton;
  /** The title of the sheet: round and table. */
  @FXML
  private Label sheetTitle;
  /** The invitation to choose a game, shown without selection. */
  @FXML
  private Label choose;
  /** The sheet of the selected game. */
  @FXML
  private VBox sheet;
  /** The head of the giver side: column A and its place. */
  @FXML
  private Label giverHead;
  /** The participants of the giver side. */
  @FXML
  private Label giverName;
  /** Whether the giver side is absent. */
  @FXML
  private CheckBox giverAbsent;
  /** The head of the receiver side: column B and its place. */
  @FXML
  private Label receiverHead;
  /** The participants of the receiver side. */
  @FXML
  private Label receiverName;
  /** Whether the receiver side is absent. */
  @FXML
  private CheckBox receiverAbsent;
  /** The deals of the sheet, with their subtotals and totals. */
  @FXML
  private GridPane deals;
  /** Why the text typed is rejected. */
  @FXML
  private Label problem;
  /** The deals whose columns do not sum to the points of a deal. */
  @FXML
  private Label check;
  /** The points counted in the ranking. */
  @FXML
  private Label result;
  /** The notice telling that the sheets are closed by the draw. */
  @FXML
  private Label lockNotice;
  /** The button clearing the sheet. */
  @FXML
  private Button clearButton;
  /** The button closing the floating detail panel. */
  @FXML
  private Button closeDetail;
  /** The stack pane of the view. */
  @FXML
  private StackPane body;
  /** The split pane of the list and of the detail panel. */
  @FXML
  private SplitPane split;
  /** The detail panel: the score sheet. */
  @FXML
  private ScrollPane detail;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the scores of an edition.
   *
   * @param main the main window.
   * @param open the open edition and its identifier.
   */
  ScoresController(final MainController main, final MainController.OpenEdition open) {
    this.main = main;
    this.id = open.id();
    this.edition = open.edition();
    this.scoring = new Scoring(edition);
    this.locked = edition.results().tieBreak() != null;
  }

  /**
   * Fills the title, the list of the games and the lines of the sheet, then selects the first game without result;
   * before the schedule exists, the list says that the scores are entered once it is computed. Called by the FXML
   * loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    title.setText(main.title(edition));
    settings.setText(main.describe(edition));
    Icon.CLOSE.decorate(closeDetail, messages.get("button.close"));
    layout = new DetailLayout(main, body, split, detail, closeDetail);
    final Button registration = new Button(messages.get("schedule.registration"));
    registration.setId("registrationButton");
    registration.setOnAction(event -> main.showPart(MainController.Place.REGISTRATION));
    table.setPlaceholder(Views.emptyState(messages.get("scores.empty"), registration));
    Views.keys(table, this::focusSheet, this::clearSheet);
    column("column.round", game -> String.valueOf(game.round()), true);
    column("column.table", game -> String.valueOf(game.table()), true);
    column("scores.column.giver", game -> main.side(edition, game.giver()), false);
    column("scores.column.receiver", game -> main.side(edition, game.receiver()), false);
    final TableColumn<Pairing, String> results = column("column.result", game -> scoring.result(game.round(),
        game.table()).map(this::describe).orElse(""), true);
    Views.warnings(table, results, this::toCheck, game -> messages.get("scores.warning"));
    sheet.visibleProperty().bind(table.getSelectionModel().selectedItemProperty().isNotNull());
    sheet.managedProperty().bind(sheet.visibleProperty());
    choose.visibleProperty().bind(sheet.visibleProperty().not());
    choose.managedProperty().bind(choose.visibleProperty());
    nextButton.setDisable(!edition.isScheduled());
    lockNotice.setVisible(locked);
    lockNotice.setManaged(locked);
    giverAbsent.setDisable(locked);
    receiverAbsent.setDisable(locked);
    table.getSelectionModel().selectedItemProperty().addListener((property, before, after) -> select(after));
    if (!edition.isScheduled()) {
      showCount();
      return;
    }
    buildSheet();
    table.getItems().setAll(edition.schedule().pairings());
    showCount();
    table.getSelectionModel().select(nextOpen(0).orElse(table.getItems().getFirst()));
  }

  /**
   * Adds a column to the list of the games.
   *
   * @param key the key of the title of the column.
   * @param text gives the text of a game.
   * @param number {@code true} for a column of numbers, aligned to the right.
   *
   * @return the column.
   */
  private TableColumn<Pairing, String> column(final String key, final Function<Pairing, String> text,
                                              final boolean number) {
    final TableColumn<Pairing, String> column = new TableColumn<>(main.messages().get(key));
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
    Views.style(column, Views.NUMBER, number);
    table.getColumns().add(column);
    return column;
  }

  /**
   * Tells whether the sheet of a game is to be checked: some of its deals do not sum to the points of a deal.
   *
   * @param game the game.
   *
   * @return {@code true} if the sheet of the game has inconsistent deals.
   */
  private boolean toCheck(final Pairing game) {
    return edition.results().sheet(game.round(), game.table())
                  .map(scoreSheet -> !scoring.totals(scoreSheet).inconsistentDeals().isEmpty()).orElse(false);
  }

  /**
   * Builds the lines of the sheet after the plan of the deals: a head, one line per deal with its number, its type
   * and its two fields, a subtotal after each block, the totals and the line of the rule 16 deduction; no text of the
   * sheet is cut.
   */
  private void buildSheet() {
    final Messages messages = main.messages();
    final List<DealType> plan = edition.deals().deals();
    fields = new TextField[plan.size()][];
    subtotals = new Label[edition.deals().blocks()][];
    int row = 0;
    deals.addRow(row++, head(messages.get("scores.deal")), new Label(), head(messages.get("scores.column.a")),
                 head(messages.get("scores.column.b")));
    for (int deal = 1; deal <= plan.size(); deal++) {
      final DealType type = plan.get(deal - 1);
      final Label number = new Label(String.valueOf(deal));
      number.getStyleClass().add("field-label");
      final Label name = new Label(messages.get("deal." + type.name()));
      Views.style(name, EditionFormController.RED, type == DealType.IMPOSED_HEARTS
                                                    || type == DealType.IMPOSED_DIAMONDS);
      fields[deal - 1] = new TextField[] {field(deal, ScoreEntry.Column.GIVER), field(deal,
                                                                                     ScoreEntry.Column.RECEIVER)};
      deals.addRow(row++, number, name, fields[deal - 1][0], fields[deal - 1][1]);
      if (deal % Regulation.DEALS_PER_BLOCK == 0) {
        final Label[] block = {sum(), sum()};
        block[0].setId("subtotalA" + deal / Regulation.DEALS_PER_BLOCK);
        block[1].setId("subtotalB" + deal / Regulation.DEALS_PER_BLOCK);
        subtotals[deal / Regulation.DEALS_PER_BLOCK - 1] = block;
        sums(row++, head(messages.get("scores.subtotal")), block);
      }
    }
    totals[0].setId("totalA");
    totals[1].setId("totalB");
    deductions[0].setId("deductionA");
    deductions[1].setId("deductionB");
    sums(row++, head(messages.get("scores.total")), new Label[] {withSum(totals[0]), withSum(totals[1])});
    deductionTitle.setText(messages.get("scores.deduction"));
    deductionTitle.getStyleClass().add("section-head");
    sums(row, deductionTitle, new Label[] {withSum(deductions[0]), withSum(deductions[1])});
    deals.getChildren().forEach(node -> ((Region) node).setMinWidth(Region.USE_PREF_SIZE));
  }

  /**
   * Adds a line of sums to the sheet: its title over the number and the type of the deals, then the sum of each
   * column under its fields.
   *
   * @param row the index of the line.
   * @param heading the title of the line.
   * @param sums the sums of column A and column B.
   */
  private void sums(final int row, final Label heading, final Label[] sums) {
    deals.add(heading, 0, row, GIVER_COLUMN, 1);
    deals.add(sums[0], GIVER_COLUMN, row);
    deals.add(sums[1], RECEIVER_COLUMN, row);
  }

  /**
   * Creates the head of a column or of a line of the sheet.
   *
   * @param text the text.
   *
   * @return the label.
   */
  private static Label head(final String text) {
    final Label head = new Label(text);
    head.getStyleClass().add("section-head");
    return head;
  }

  /**
   * Creates a label of a sum, aligned to the right.
   *
   * @return the label.
   */
  private static Label sum() {
    return withSum(new Label());
  }

  /**
   * Styles a label of a sum, aligned to the right.
   *
   * @param label the label.
   *
   * @return the label.
   */
  private static Label withSum(final Label label) {
    label.getStyleClass().addAll("sum", Views.NUMBER);
    label.setMaxWidth(Double.MAX_VALUE);
    return label;
  }

  /**
   * Creates the field of a column of a deal: Enter takes the value and moves to the next deal, Up and Down move in
   * the column, leaving the field takes the value.
   *
   * @param deal the number of the deal, from 1.
   * @param column the column.
   *
   * @return the field.
   */
  private TextField field(final int deal, final ScoreEntry.Column column) {
    final TextField field = new TextField();
    final String letter = column == ScoreEntry.Column.GIVER ? "a" : "b";
    field.setId(letter + deal);
    field.getStyleClass().add("points");
    field.setPrefColumnCount(POINTS_WIDTH);
    field.setAccessibleText(main.messages().get("scores.field." + letter, deal));
    field.setOnAction(event -> {
      if (commit(deal, column, field)) {
        move(deal + 1, column);
      }
    });
    field.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.UP) {
        move(deal - 1, column);
        event.consume();
      } else if (event.getCode() == KeyCode.DOWN) {
        move(deal + 1, column);
        event.consume();
      }
    });
    field.focusedProperty().addListener((property, before, focused) -> {
      if (!focused) {
        commit(deal, column, field);
      }
    });
    return field;
  }

  // *******************************************************************************************************************
  // Display
  // *******************************************************************************************************************

  /**
   * Shows the number of games with a result.
   */
  private void showCount() {
    count.setText(main.messages().get("scores.count", scoring.results().size(), table.getItems().size()));
  }

  /**
   * Describes the result of a game: the points of each side, and whether it is a forfeit.
   *
   * @param game the result.
   *
   * @return the description.
   */
  private String describe(final GameResult game) {
    return main.messages().get(game.forfeit() ? "scores.result.forfeit" : "scores.result",
                               main.messages().number(game.giverPoints()),
                               main.messages().number(game.receiverPoints()));
  }

  /**
   * Shows the sheet of a game.
   *
   * @param game the game, or {@code null} without selection.
   */
  private void select(final Pairing game) {
    pairing = game;
    if (game == null) {
      return;
    }
    final Messages messages = main.messages();
    sheetTitle.setText(messages.get("scores.sheet.title", game.round(), game.table()));
    giverHead.setText(messages.get("scores.giver", 2 * game.table() - 1));
    receiverHead.setText(messages.get("scores.receiver", 2 * game.table()));
    giverName.setText(main.side(edition, game.giver()));
    receiverName.setText(main.side(edition, game.receiver()));
    showSheet();
    layout.open();
  }

  /**
   * Shows the values of the sheet of the selected game: the columns (derived or typed, inconsistent deals
   * highlighted), the subtotals, the totals, the deduction, the absences and the points counted.
   */
  private void showSheet() {
    final Messages messages = main.messages();
    final ScoreSheet current = sheet();
    final SheetTotals sums = scoring.totals(current);
    final boolean forfeit = current.absence() != Absence.NONE;
    problem.setText("");
    for (int deal = 1; deal <= fields.length; deal++) {
      final DealScore score = current.deals().get(deal - 1);
      for (final ScoreEntry.Column column : ScoreEntry.Column.values()) {
        final TextField field = fields[deal - 1][column.ordinal()];
        field.setText(ScoreEntry.text(score, column));
        field.setDisable(forfeit || locked);
        Views.style(field, INVALID, false);
        Views.style(field, DERIVED, ScoreEntry.derived(score, column));
        Views.style(field, INCONSISTENT, sums.inconsistentDeals().contains(deal));
      }
    }
    for (int block = 0; block < subtotals.length; block++) {
      subtotals[block][0].setText(messages.number(sums.giverSubtotals().get(block)));
      subtotals[block][1].setText(messages.number(sums.receiverSubtotals().get(block)));
    }
    totals[0].setText(messages.number(sums.giverTotal()));
    totals[1].setText(messages.number(sums.receiverTotal()));
    final boolean deducted = sums.deduction() > 0;
    for (final Label label : List.of(deductionTitle, deductions[0], deductions[1])) {
      label.setVisible(deducted);
    }
    deductions[0].setText(messages.get("scores.minus", messages.number(sums.deduction())));
    deductions[1].setText(deductions[0].getText());
    giverAbsent.setSelected(ScoreEntry.absent(current.absence(), ScoreEntry.Column.GIVER));
    receiverAbsent.setSelected(ScoreEntry.absent(current.absence(), ScoreEntry.Column.RECEIVER));
    check.setText(sums.inconsistentDeals().isEmpty() ? "" : messages.get("scores.inconsistent",
        sums.inconsistentDeals().stream().map(String::valueOf).collect(Collectors.joining(", ")),
        Regulation.POINTS_PER_DEAL));
    final Optional<GameResult> counted = scoring.result(pairing.round(), pairing.table());
    result.setText(counted.map(game -> messages.get("scores.counted", describe(game))).orElse(""));
    clearButton.setDisable(locked || edition.results().sheet(pairing.round(), pairing.table()).isEmpty());
  }

  /**
   * Returns the sheet of the selected game: the one entered, or an empty one.
   *
   * @return the sheet.
   */
  private ScoreSheet sheet() {
    return edition.results().sheet(pairing.round(), pairing.table())
                  .orElse(ScoreSheet.empty(pairing.round(), pairing.table(), edition.deals()));
  }

  /**
   * Returns the first game without result, from a position of the list, going round to its start.
   *
   * @param from the index of the first game looked at.
   *
   * @return the game, or nothing if every game has a result.
   */
  private Optional<Pairing> nextOpen(final int from) {
    final List<Pairing> games = new ArrayList<>(table.getItems());
    return IntStream.range(0, games.size()).mapToObj(index -> games.get((from + index) % games.size()))
                    .filter(game -> scoring.result(game.round(), game.table()).isEmpty()).findFirst();
  }

  /**
   * Moves the focus to the field of a deal, its text selected to be typed over.
   *
   * @param deal the number of the deal, from 1; nothing happens if there is no such deal.
   * @param column the column.
   */
  private void move(final int deal, final ScoreEntry.Column column) {
    if (deal < 1 || deal > fields.length) {
      return;
    }
    final TextField field = fields[deal - 1][column.ordinal()];
    field.requestFocus();
    field.selectAll();
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Takes the text of a field: the deal changes, or the text is rejected with a message.
   *
   * @param deal the number of the deal, from 1.
   * @param column the column.
   * @param field the field.
   *
   * @return {@code true} if the text is taken.
   */
  private boolean commit(final int deal, final ScoreEntry.Column column, final TextField field) {
    final ScoreSheet current = sheet();
    try {
      change(current.withDeal(deal, ScoreEntry.enter(current.deals().get(deal - 1), column, field.getText())));
      return true;
    } catch (final DomainException e) {
      problem.setText(main.messages().error(e));
      Views.style(field, INVALID, true);
      return false;
    }
  }

  /**
   * Takes a changed sheet: the results of the edition change and are saved at once, the list and the sheet are shown
   * again; nothing is saved when the sheet is unchanged. A value taken when the field is left because the view is
   * replaced (e.g. Shortcut+1) is saved without showing the edition again.
   *
   * @param changed the sheet of the selected game.
   */
  private void change(final ScoreSheet changed) {
    if (changed.equals(sheet())) {
      showSheet();
      return;
    }
    edition = edition.withResults(ScoreEntry.keep(edition.results(), changed, edition.deals()));
    scoring = new Scoring(edition);
    final MainController.OpenEdition open = new MainController.OpenEdition(id, edition);
    if (deals.getScene() == null) {
      main.write(open);
      return;
    }
    main.update(open);
    table.refresh();
    showCount();
    showSheet();
  }

  /**
   * Marks the sides absent (rule 19) as the boxes say.
   */
  @FXML
  void absenceChanged() {
    change(sheet().withAbsence(ScoreEntry.absence(giverAbsent.isSelected(), receiverAbsent.isSelected())));
  }

  /**
   * Clears the sheet of the selected game, after confirmation: the game has no result any more; nothing happens
   * without sheet or once the lots are drawn.
   */
  @FXML
  void clearSheet() {
    if (locked || edition.results().sheet(pairing.round(), pairing.table()).isEmpty()) {
      return;
    }
    if (main.dialogs().confirmDestructive("confirm.clear.sheet", pairing.round(), pairing.table())) {
      change(ScoreSheet.empty(pairing.round(), pairing.table(), edition.deals()));
      main.notify("info.sheet.cleared", pairing.round(), pairing.table());
    }
  }

  /**
   * Moves the focus to the first deal of the sheet, column A.
   */
  private void focusSheet() {
    layout.open();
    move(1, ScoreEntry.Column.GIVER);
  }

  /**
   * Selects the next game without result and moves to its sheet, or tells that every game has a result.
   */
  @FXML
  void next() {
    final int from = table.getSelectionModel().getSelectedIndex() + 1;
    nextOpen(from).ifPresentOrElse(game -> {
      table.getSelectionModel().select(game);
      table.scrollTo(game);
      focusSheet();
    }, () -> main.notify("info.scores.complete"));
  }

}
