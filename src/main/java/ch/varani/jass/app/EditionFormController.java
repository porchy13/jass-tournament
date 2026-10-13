package ch.varani.jass.app;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.TournamentMode;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.converter.LocalDateStringConverter;

import java.util.Arrays;

/**
 * The form of a new edition or of the duplication of an edition (F2, F4, F15, F16): name, date, tournament mode,
 * number of games, maximum of teams and plan of the deals, edited deal by deal (the type of each deal chosen in a
 * list, the suits shown as ♥ ♣ ♦ ♠) or by blocks of {@link ch.varani.jass.domain.Regulation#DEALS_PER_BLOCK} deals;
 * the plan of the regulation can be restored at any time.
 *
 * <p>The values are kept in an {@link EditionForm}; the number of deals, of blocks and the points of a game are shown,
 * and the reason why the edition cannot be created yet (blank name, missing date…) is shown instead of the creation
 * button being merely disabled. The mode of a duplicate cannot be changed.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class EditionFormController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The greatest number of games proposed. */
  static final int MAX_GAMES = 20;
  /** The style class of the red suits ♥ and ♦. */
  static final String RED = "red-suit";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The values of the form. */
  private final EditionForm form;
  /** The title of the form. */
  @FXML
  private Label title;
  /** The name of the edition. */
  @FXML
  private TextField nameField;
  /** The date of the edition. */
  @FXML
  private DatePicker datePicker;
  /** The tournament mode. */
  @FXML
  private ChoiceBox<TournamentMode> modeBox;
  /** The number of games. */
  @FXML
  private Spinner<Integer> gamesSpinner;
  /** The maximum number of teams. */
  @FXML
  private Spinner<Integer> maxSpinner;
  /** The deals of the plan. */
  @FXML
  private ListView<DealType> dealList;
  /** The number of deals, blocks and points. */
  @FXML
  private Label summary;
  /** The reason why the edition cannot be created yet. */
  @FXML
  private Label problem;
  /** The button creating the edition. */
  @FXML
  private Button createButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the form.
   *
   * @param main the main window.
   * @param form the values of the form.
   */
  EditionFormController(final MainController main, final EditionForm form) {
    this.main = main;
    this.form = form;
  }

  /**
   * Fills the controls with the values of the form and follows their changes; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    title.setText(messages.get(form.isDuplicate() ? "form.title.duplicate" : "form.title.new"));
    nameField.setText(form.name());
    nameField.textProperty().addListener((property, before, after) -> {
      form.setName(after);
      check();
    });
    datePicker.setConverter(new LocalDateStringConverter(messages.dateFormat(), messages.dateFormat()));
    datePicker.setValue(form.date());
    datePicker.valueProperty().addListener((property, before, after) -> {
      form.setDate(after);
      check();
    });
    modeBox.getItems().setAll(TournamentMode.values());
    modeBox.setConverter(Converters.showing(mode -> messages.get("mode." + mode.name())));
    modeBox.setValue(form.mode());
    modeBox.setDisable(form.isDuplicate());
    modeBox.valueProperty().addListener((property, before, after) -> form.setMode(after));
    gamesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, MAX_GAMES, form.games()));
    gamesSpinner.valueProperty().addListener((property, before, after) -> form.setGames(after));
    maxSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, RegistrationController.MAX_TEAMS,
                                                                                 form.maxTeams()));
    maxSpinner.valueProperty().addListener((property, before, after) -> form.setMaxTeams(after));
    dealList.setCellFactory(view -> new DealCell());
    refreshDeals();
  }


  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Restores the plan of the regulation.
   */
  @FXML
  void useRegulation() {
    form.useRegulation();
    refreshDeals();
  }

  /**
   * Adds a block of deals at the end of the plan.
   */
  @FXML
  void addBlock() {
    form.addBlock();
    refreshDeals();
  }

  /**
   * Removes the last block of deals of the plan.
   */
  @FXML
  void removeBlock() {
    form.removeBlock();
    refreshDeals();
  }

  /**
   * Creates the edition and opens it.
   */
  @FXML
  void create() {
    main.create(form.build());
  }

  /**
   * Goes back to the list of the editions without creating anything.
   */
  @FXML
  void cancel() {
    main.showList();
  }

  // *******************************************************************************************************************
  // Updates
  // *******************************************************************************************************************

  /**
   * Shows the deals of the plan and their totals again.
   */
  private void refreshDeals() {
    dealList.getItems().setAll(form.deals());
    final DealPlan plan = new DealPlan(form.deals());
    summary.setText(main.messages().get("form.summary", plan.size(), plan.blocks(), plan.pointsPerGame()));
    check();
  }

  /**
   * Shows why the edition cannot be created yet, and enables the creation when it can.
   */
  private void check() {
    final String key = form.problem().orElse(null);
    problem.setText(key == null ? "" : main.messages().get(key));
    createButton.setDisable(key != null);
  }

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * A type of deal in the list of the types: its name, the suits ♥ and ♦ in red (style class {@value #RED}).
   *
   * <p>Confined to the JavaFX Application Thread.</p>
   */
  private final class TypeCell extends ListCell<DealType> {

    /**
     * Creates an empty line of the list of the types.
     */
    TypeCell() {
      super();
    }

    /**
     * Shows a type of deal, or nothing for an empty line.
     *
     * @param deal the type of deal.
     * @param empty whether the line is empty.
     */
    @Override
    protected void updateItem(final DealType deal, final boolean empty) {
      super.updateItem(deal, empty);
      getStyleClass().remove(RED);
      if (empty) {
        setText(null);
        return;
      }
      setText(main.messages().get("deal." + deal.name()));
      if (deal == DealType.IMPOSED_HEARTS || deal == DealType.IMPOSED_DIAMONDS) {
        getStyleClass().add(RED);
      }
    }

  }

  /**
   * A line of the plan of the deals: the number of the deal and the list of its types.
   *
   * <p>Confined to the JavaFX Application Thread.</p>
   */
  private final class DealCell extends ListCell<DealType> {

    /** The number of the deal. */
    private final Label number = new Label();
    /** The type of the deal. */
    private final ComboBox<DealType> type = new ComboBox<>();
    /** The content of the line. */
    private final HBox line = new HBox(number, type);

    /**
     * Creates a line, whose list of types changes the plan.
     */
    DealCell() {
      type.getItems().setAll(Arrays.asList(DealType.values()));
      type.setCellFactory(view -> new TypeCell());
      type.setButtonCell(new TypeCell());
      type.setOnAction(event -> form.setDeal(getIndex(), type.getValue()));
      line.getStyleClass().add("deal");
    }

    /**
     * Shows a deal, or nothing for an empty line.
     *
     * @param deal the type of the deal.
     * @param empty whether the line is empty.
     */
    @Override
    protected void updateItem(final DealType deal, final boolean empty) {
      super.updateItem(deal, empty);
      if (empty) {
        setGraphic(null);
        return;
      }
      number.setText(main.messages().get("form.deal", getIndex() + 1));
      type.setValue(deal);
      setGraphic(line);
    }

  }

}
