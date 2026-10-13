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

import java.util.function.Function;
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
  /** The style class of the tick of a row of a table that is done. */
  static final String DONE = "done";
  /**
   * The style class of a column of numbers or dates, and of a number: aligned to the right (DESIGN.md §6), the head of
   * the column too.
   */
  static final String NUMBER = "number";

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
   * Marks the rows of a table that need the attention of the user, e.g. a participant to check: the row is written in
   * the warning colour (style class {@value #WARNED}) and, as a colour is never alone, a column shows a warning icon
   * with its description (tooltip and screen readers) before its text. The marks follow
   * {@link TableView#refresh()}.
   *
   * @param <T> the type of the rows.
   * @param table the table.
   * @param column the column showing the warning icon.
   * @param warned tells whether a row needs the attention of the user.
   * @param description what the icon of a row means.
   */
  static <T> void warnings(final TableView<T> table, final TableColumn<T, String> column, final Predicate<T> warned,
                           final Function<T, String> description) {
    marks(table, column, new Marks<>(warned, description, null, null));
  }

  /**
   * Marks the rows of a table that need the attention of the user, as {@link #warnings}, and the rows that are done,
   * e.g. a game whose result is entered: the column shows a tick (style class {@value #DONE}, in the success colour)
   * with its description before its text. A row that needs the attention of the user shows the warning only.
   *
   * @param <T> the type of the rows.
   * @param table the table.
   * @param column the column showing the icons.
   * @param warned tells whether a row needs the attention of the user.
   * @param warning what the warning icon of a row means.
   * @param done tells whether a row is done.
   * @param finished what the tick means.
   */
  static <T> void marks(final TableView<T> table, final TableColumn<T, String> column, final Predicate<T> warned,
                        final Function<T, String> warning, final Predicate<T> done, final String finished) {
    marks(table, column, new Marks<>(warned, warning, done, finished));
  }

  /**
   * Gives the rows of a table their style class and the column its icons.
   *
   * @param <T> the type of the rows.
   * @param table the table.
   * @param column the column showing the icons.
   * @param marks the marks of the rows.
   */
  private static <T> void marks(final TableView<T> table, final TableColumn<T, String> column, final Marks<T> marks) {
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
        style(this, WARNED, !empty && marks.warned().test(item));
      }

    });
    column.setCellFactory(view -> new TableCell<>() {

      /**
       * Shows the text of the cell, with the warning icon when its row needs the attention of the user, else with the
       * tick when its row is done.
       *
       * @param text the text.
       * @param empty whether the cell is empty.
       */
      @Override
      protected void updateItem(final String text, final boolean empty) {
        super.updateItem(text, empty);
        setText(empty ? null : text);
        setGraphic(empty ? null : marks.graphic(getTableView().getItems().get(getIndex())));
      }

    });
  }

  // *******************************************************************************************************************
  // Inner classes
  // *******************************************************************************************************************

  /**
   * The marks of the rows of a table: the warning of the rows that need the attention of the user and, optionally,
   * the tick of the rows that are done.
   *
   * <p>Immutable; the nodes it creates are confined to the JavaFX Application Thread.</p>
   *
   * @param <T> the type of the rows.
   * @param warned tells whether a row needs the attention of the user.
   * @param warning what the warning icon of a row means.
   * @param done tells whether a row is done, or {@code null} when the rows have no tick.
   * @param finished what the tick means, or {@code null} when the rows have no tick.
   */
  private record Marks<T>(Predicate<T> warned, Function<T, String> warning, Predicate<T> done, String finished) {

    /**
     * Creates the icon of a row: the warning when it needs the attention of the user, else the tick when it is done.
     *
     * @param item the row.
     *
     * @return the icon, or {@code null} when the row has no mark.
     */
    Node graphic(final T item) {
      final Label icon = new Label();
      if (warned.test(item)) {
        Icon.WARNING.decorate(icon, warning.apply(item));
        return icon;
      }
      if (done == null || !done.test(item)) {
        return null;
      }
      Icon.CHECK.decorate(icon, finished);
      icon.getStyleClass().add(DONE);
      return icon;
    }

  }

}
