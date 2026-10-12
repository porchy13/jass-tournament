package ch.varani.jass.app;

import javafx.css.Styleable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;

import java.util.function.Predicate;

/**
 * The parts shared by the views (DESIGN.md §7, §10): the empty states, the keys of the tables, the rows of a table that
 * need the attention of the user, the shortcuts written for the system.
 *
 * <p>Utility class without state; the nodes it creates are confined to the JavaFX Application Thread.</p>
 */
final class Views {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The style class of a row of a table that needs the attention of the user. */
  static final String WARNED = "warned";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private Views() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Writes a shortcut as the system shows it: {@code Ctrl+N}, or {@code ⌘N} on macOS.
   *
   * @param shortcut the shortcut, e.g. {@code Shortcut+N}.
   *
   * @return its text.
   */
  static String shortcut(final String shortcut) {
    return KeyCombination.valueOf(shortcut).getDisplayText();
  }

  /**
   * Creates an empty state: a sentence telling what to do, and the matching button.
   *
   * @param text the sentence.
   * @param action the button, or {@code null} when there is nothing to do.
   *
   * @return the empty state, e.g. the placeholder of a table.
   */
  static Node emptyState(final String text, final Button action) {
    final VBox state = new VBox(new Label(text));
    state.getStyleClass().add("empty-state");
    if (action != null) {
      state.getChildren().add(action);
    }
    return state;
  }

  /**
   * Gives the keys of a table: Enter opens the selected row, Delete (or Backspace on macOS) deletes it.
   *
   * @param table the table.
   * @param open opens the selected row, or {@code null}.
   * @param delete deletes the selected row, or {@code null}.
   */
  static void keys(final TableView<?> table, final Runnable open, final Runnable delete) {
    table.setOnKeyPressed(event -> {
      if (table.getSelectionModel().isEmpty()) {
        return;
      }
      final KeyCode code = event.getCode();
      if (code == KeyCode.ENTER && open != null) {
        open.run();
        event.consume();
      } else if ((code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) && delete != null) {
        delete.run();
        event.consume();
      }
    });
  }

  /**
   * Gives or removes a style class of a node or of a column of a table, e.g. the highlighting of a field.
   *
   * @param node the node or the column.
   * @param styleClass the style class.
   * @param on {@code true} to give it, {@code false} to remove it.
   */
  static void style(final Styleable node, final String styleClass, final boolean on) {
    node.getStyleClass().remove(styleClass);
    if (on) {
      node.getStyleClass().add(styleClass);
    }
  }

  /**
   * Marks the rows of a table that need the attention of the user, e.g. a score sheet to check: the row is written in
   * the warning colour (style class {@value #WARNED}) and, as a colour is never alone, a column shows a warning icon
   * with its description (tooltip and screen readers) before its text. The marks follow
   * {@link TableView#refresh()}.
   *
   * @param <T> the type of the rows.
   * @param table the table.
   * @param column the column showing the warning icon.
   * @param warned tells whether a row needs the attention of the user.
   * @param description what the icon means.
   */
  static <T> void warnings(final TableView<T> table, final TableColumn<T, String> column, final Predicate<T> warned,
                           final String description) {
    table.setRowFactory(view -> new TableRow<>() {

      /**
       * Gives the row the style class {@value #WARNED} when it needs the attention of the user.
       *
       * @param item the row.
       * @param empty whether the row is empty.
       */
      @Override
      protected void updateItem(final T item, final boolean empty) {
        super.updateItem(item, empty);
        style(this, WARNED, !empty && warned.test(item));
      }

    });
    column.setCellFactory(view -> new TableCell<>() {

      /**
       * Shows the text of the cell, with the warning icon when its row needs the attention of the user.
       *
       * @param text the text.
       * @param empty whether the cell is empty.
       */
      @Override
      protected void updateItem(final String text, final boolean empty) {
        super.updateItem(text, empty);
        setText(empty ? null : text);
        setGraphic(null);
        if (!empty && warned.test(getTableView().getItems().get(getIndex()))) {
          final Label icon = new Label();
          Icon.WARNING.decorate(icon, description);
          setGraphic(icon);
        }
      }

    });
  }

}
