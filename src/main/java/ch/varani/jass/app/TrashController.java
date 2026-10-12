package ch.varani.jass.app;

import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.EditionList;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * The trash of the application (F3): the deleted editions and their number; the selected edition can be restored,
 * and the trash emptied, which deletes the editions for good after confirmation (a destructive action, in red).
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class TrashController {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The editions of the trash. */
  private final EditionList editions;
  /** The table of the deleted editions. */
  @FXML
  private TableView<EditionHeader> table;
  /** The column of the names. */
  @FXML
  private TableColumn<EditionHeader, String> nameColumn;
  /** The column of the dates. */
  @FXML
  private TableColumn<EditionHeader, String> dateColumn;
  /** The number of deleted editions. */
  @FXML
  private Label count;
  /** The button restoring the selected edition. */
  @FXML
  private Button restoreButton;
  /** The button emptying the trash. */
  @FXML
  private Button emptyButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the screen of the trash.
   *
   * @param main the main window.
   * @param editions the editions of the trash.
   */
  TrashController(final MainController main, final EditionList editions) {
    this.main = main;
    this.editions = editions;
  }

  /**
   * Fills the table and enables the actions; called by the FXML loader.
   */
  @FXML
  void initialize() {
    nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
    dateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(main.messages().date(cell.getValue().date())));
    table.getItems().setAll(editions.editions());
    count.setText(main.messages().get("editions.count", editions.editions().size()));
    restoreButton.setGraphic(Icon.RESTORE.graphic());
    restoreButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    emptyButton.setDisable(editions.editions().isEmpty() && editions.unreadable().isEmpty());
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Brings the selected edition back to the list of the editions.
   */
  @FXML
  void restore() {
    final EditionHeader header = table.getSelectionModel().getSelectedItem();
    main.submit(store -> {
      store.restore(header.id());
      return header.id();
    }, id -> {
      main.showTrash();
      main.notify("info.restored", header.name());
    });
  }

  /**
   * Deletes the editions of the trash for good, after confirmation.
   */
  @FXML
  void empty() {
    if (main.dialogs().confirmDestructive("confirm.empty.trash")) {
      main.submit(store -> {
        store.emptyTrash();
        return Boolean.TRUE;
      }, done -> {
        main.showTrash();
        main.notify("info.trash.emptied");
      });
    }
  }

}
