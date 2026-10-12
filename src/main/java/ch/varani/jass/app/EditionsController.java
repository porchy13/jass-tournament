package ch.varani.jass.app;

import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.EditionList;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;

/**
 * The start view (F2): the editions of the data folder, the most recent first, with their name, mode and date, and
 * their number; the unreadable files are reported, not hidden; without edition, the view says how to create one.
 *
 * <p>Actions: new edition (the primary action of the toolbar), then the tools: opening (button, double click or
 * Enter), duplication of the selected edition (F16), import of an edition file, deletion to the trash (button or
 * Delete). The actions on a selected edition are disabled without selection.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class EditionsController {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The editions of the data folder. */
  private final EditionList editions;
  /** The table of the editions. */
  @FXML
  private TableView<EditionHeader> table;
  /** The column of the names. */
  @FXML
  private TableColumn<EditionHeader, String> nameColumn;
  /** The column of the dates. */
  @FXML
  private TableColumn<EditionHeader, String> dateColumn;
  /** The column of the modes. */
  @FXML
  private TableColumn<EditionHeader, String> modeColumn;
  /** The number of editions. */
  @FXML
  private Label count;
  /** The button creating an edition. */
  @FXML
  private Button newButton;
  /** The button importing an edition. */
  @FXML
  private Button importButton;
  /** The report of the unreadable files. */
  @FXML
  private Label unreadable;
  /** The button duplicating the selected edition. */
  @FXML
  private Button duplicateButton;
  /** The button opening the selected edition. */
  @FXML
  private Button openButton;
  /** The button deleting the selected edition. */
  @FXML
  private Button deleteButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the start screen.
   *
   * @param main the main window.
   * @param editions the editions of the data folder.
   */
  EditionsController(final MainController main, final EditionList editions) {
    this.main = main;
    this.editions = editions;
  }

  /**
   * Fills the table and its count, reports the unreadable files, decorates the tools and enables them according to
   * the selection; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
    dateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(messages.date(cell.getValue().date())));
    modeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
        messages.get("mode." + cell.getValue().mode().name())));
    table.getItems().setAll(editions.editions());
    table.setRowFactory(view -> {
      final TableRow<EditionHeader> row = new TableRow<>();
      row.setOnMouseClicked(event -> {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
          main.open(row.getItem().id());
        }
      });
      return row;
    });
    count.setText(messages.get("editions.count", editions.editions().size()));
    newButton.setGraphic(Icon.PLUS.graphic());
    Icon.OPEN.decorate(openButton, messages.get("editions.open"));
    Icon.COPY.decorate(duplicateButton, messages.get("editions.duplicate"));
    Icon.IMPORT.decorate(importButton, messages.get("editions.import"));
    Icon.TRASH.decorate(deleteButton, messages.get("editions.delete"));
    final Button create = new Button(messages.get("editions.new"));
    create.setOnAction(event -> newEdition());
    table.setPlaceholder(Views.emptyState(messages.get("editions.empty", Views.shortcut("Shortcut+N")), create));
    Views.keys(table, this::open, this::delete);
    unreadable.setText(messages.get("editions.unreadable", editions.unreadable().size()));
    unreadable.setVisible(!editions.unreadable().isEmpty());
    unreadable.setManaged(unreadable.isVisible());
    for (final Button button : new Button[] {duplicateButton, openButton, deleteButton}) {
      button.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
    }
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Shows the form of a new edition.
   */
  @FXML
  void newEdition() {
    main.newEdition();
  }

  /**
   * Shows the form of the duplication of the selected edition.
   */
  @FXML
  void duplicate() {
    main.duplicate(selected().id());
  }

  /**
   * Opens the selected edition.
   */
  @FXML
  void open() {
    main.open(selected().id());
  }

  /**
   * Moves the selected edition to the trash, after confirmation.
   */
  @FXML
  void delete() {
    main.delete(selected());
  }

  /**
   * Imports an edition file.
   */
  @FXML
  void importEdition() {
    main.importEdition();
  }

  /**
   * Returns the selected edition; the actions using it are disabled without selection.
   *
   * @return the header of the selected edition.
   */
  private EditionHeader selected() {
    return table.getSelectionModel().getSelectedItem();
  }

}
