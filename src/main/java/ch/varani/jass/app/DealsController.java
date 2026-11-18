package ch.varani.jass.app;

import ch.varani.jass.domain.DealType;
import ch.varani.jass.domain.Edition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;

/**
 * The content of the games of an open edition (F15): the plan of the deals, edited in a {@link DealPlanEditor} deal by
 * deal or by blocks of {@link ch.varani.jass.domain.Regulation#DEALS_PER_BLOCK} deals, or back to the plan of the
 * regulation, e.g. to correct an edition created with a wrong plan without typing its registrations again.
 *
 * <p>The plan is changed while the registration is open only: once the schedule is computed, the plan is shown
 * read-only, with a note telling to reopen the registration first, so that the score sheets printed with the schedule
 * keep following the plan.</p>
 *
 * <p>Every change is saved at once, in place ({@link MainController#update(MainController.OpenEdition)}), so that the
 * list keeps the focus; the settings of the edition (number of deals, points of a game) follow it.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class DealsController {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The plan being edited. */
  private final DealPlanDraft draft;
  /** The open edition; changed in place by the plan. */
  private MainController.OpenEdition open;
  /** The editor of the plan. */
  private DealPlanEditor editor;
  /** The name and date of the edition. */
  @FXML
  private Label title;
  /** The settings of the edition. */
  @FXML
  private Label settings;
  /** The number of deals, blocks and points. */
  @FXML
  private Label summary;
  /** Tells that the plan cannot be changed once the schedule is computed. */
  @FXML
  private Label closed;
  /** The deals of the plan. */
  @FXML
  private ListView<DealType> dealList;
  /** The changes by blocks and the plan of the regulation. */
  @FXML
  private Pane blocks;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the view.
   *
   * @param main the main window.
   * @param open the open edition.
   */
  DealsController(final MainController main, final MainController.OpenEdition open) {
    this.main = main;
    this.open = open;
    draft = new DealPlanDraft(open.edition().deals());
  }

  /**
   * Fills the title and the plan, editable while the registration is open; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Edition edition = open.edition();
    final boolean editable = !edition.isScheduled();
    title.setText(main.title(edition));
    settings.setText(main.describe(edition));
    editor = new DealPlanEditor(main.messages(), draft, dealList, summary, this::save);
    editor.setEditable(editable);
    blocks.setDisable(!editable);
    closed.setVisible(!editable);
    closed.setManaged(!editable);
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Restores the plan of the regulation.
   */
  @FXML
  void useRegulation() {
    editor.useRegulation();
  }

  /**
   * Adds a block of deals at the end of the plan.
   */
  @FXML
  void addBlock() {
    editor.addBlock();
  }

  /**
   * Removes the last block of deals of the plan.
   */
  @FXML
  void removeBlock() {
    editor.removeBlock();
  }

  // *******************************************************************************************************************
  // Updates
  // *******************************************************************************************************************

  /**
   * Saves the changed plan in place and shows the settings of the edition again.
   */
  private void save() {
    open = new MainController.OpenEdition(open.id(), open.edition().withDeals(draft.plan()));
    main.update(open);
    settings.setText(main.describe(open.edition()));
  }

}
