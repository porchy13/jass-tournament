package ch.varani.jass.app;

import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealType;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;

import java.util.Arrays;

/**
 * The editor of the plan of the deals (F15), shared by the form of a new edition and the content of the games of an
 * open edition: one line per deal, its number and the list of its types (the suits shown as ♥ ♣ ♦ ♠, the red suits in
 * the style class {@value #RED}), the number of deals, of blocks and the points of a game, and the changes by blocks
 * of 4 deals or back to the plan of the regulation.
 *
 * <p>The plan is kept in a {@link DealPlanDraft}; every change is reported to the view, which saves it or checks it. A
 * read-only editor shows the plan without allowing any change.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class DealPlanEditor {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The style class of the red suits ♥ and ♦. */
  static final String RED = "red-suit";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The texts of the user interface. */
  private final Messages messages;
  /** The plan being edited. */
  private final DealPlanDraft draft;
  /** The lines of the deals. */
  private final ListView<DealType> list;
  /** The number of deals, blocks and points. */
  private final Label summary;
  /** Called after every change of the plan. */
  private final Runnable changed;
  /** Whether the plan can be changed. */
  private final BooleanProperty editable = new SimpleBooleanProperty(true);

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the editor and shows the plan.
   *
   * @param messages the texts of the user interface.
   * @param draft the plan being edited.
   * @param list the list showing the deals.
   * @param summary the label of the number of deals, blocks and points.
   * @param changed called after every change of the plan, on the JavaFX Application Thread.
   */
  DealPlanEditor(final Messages messages, final DealPlanDraft draft, final ListView<DealType> list,
                 final Label summary, final Runnable changed) {
    this.messages = messages;
    this.draft = draft;
    this.list = list;
    this.summary = summary;
    this.changed = changed;
    list.setCellFactory(view -> new DealCell());
    show();
  }

  // *******************************************************************************************************************
  // Getters & Setters
  // *******************************************************************************************************************

  /**
   * Allows or forbids the changes of the types of the deals; the view enables its block actions itself.
   *
   * @param value {@code true} to allow the changes.
   */
  void setEditable(final boolean value) {
    editable.set(value);
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Restores the plan of the regulation.
   */
  void useRegulation() {
    draft.useRegulation();
    refresh();
  }

  /**
   * Adds a block of deals at the end of the plan.
   */
  void addBlock() {
    draft.addBlock();
    refresh();
  }

  /**
   * Removes the last block of deals of the plan, keeping at least one block.
   */
  void removeBlock() {
    draft.removeBlock();
    refresh();
  }

  // *******************************************************************************************************************
  // Updates
  // *******************************************************************************************************************

  /**
   * Shows the deals of the plan and their totals again, then reports the change.
   */
  private void refresh() {
    show();
    changed.run();
  }

  /**
   * Shows the deals of the plan and their totals.
   */
  private void show() {
    list.getItems().setAll(draft.deals());
    showSummary();
  }

  /**
   * Shows the number of deals, of blocks and the points of a game.
   */
  private void showSummary() {
    final DealPlan plan = draft.plan();
    summary.setText(messages.get("form.summary", plan.size(), plan.blocks(), plan.pointsPerGame()));
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
      setText(messages.get("deal." + deal.name()));
      if (deal == DealType.IMPOSED_HEARTS || deal == DealType.IMPOSED_DIAMONDS) {
        getStyleClass().add(RED);
      }
    }

  }

  /**
   * A line of the plan of the deals: the number of the deal and the list of its types, disabled while the plan cannot
   * be changed.
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
      type.setOnAction(event -> choose(type.getValue()));
      type.disableProperty().bind(editable.not());
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
      number.setText(messages.get("form.deal", getIndex() + 1));
      type.setValue(deal);
      setGraphic(line);
    }

    /**
     * Changes the type of the deal of the line, chosen in its list; a list showing the type the deal already has, e.g.
     * when the line shows a deal, changes nothing.
     *
     * @param chosen the chosen type.
     */
    private void choose(final DealType chosen) {
      if (draft.setDeal(getIndex(), chosen)) {
        showSummary();
        changed.run();
      }
    }

  }

}
