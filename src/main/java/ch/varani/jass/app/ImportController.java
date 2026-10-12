package ch.varani.jass.app;

import ch.varani.jass.directory.ColumnMapping;
import ch.varani.jass.directory.Csv;
import ch.varani.jass.directory.ImportRow;
import ch.varani.jass.directory.Person;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The import of persons into the directory (Q17): from a CSV file (UTF-8) or from rows pasted from a spreadsheet;
 * the user chooses the separator, tells whether the first row is a header and maps the columns; the preview reports
 * every invalid value and the possible duplicates; nothing is added before the user confirms, and the possible
 * duplicates only when asked. The values are kept in an {@link ImportModel}.
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class ImportController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The separators offered. */
  private static final List<Character> SEPARATORS = List.of(Csv.SEMICOLON, Csv.COMMA, Csv.TAB);

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The values of the import. */
  private final ImportModel model = new ImportModel();
  /** The preview of the import. */
  private List<ImportRow> preview = List.of();
  /** Whether the controls are being filled by the program, so that their listeners ignore the change. */
  private boolean filling;
  /** The text of the table: chosen file or pasted rows. */
  @FXML
  private TextArea text;
  /** The separator of the cells. */
  @FXML
  private ChoiceBox<Character> separatorBox;
  /** Whether the first row is a header. */
  @FXML
  private CheckBox headerBox;
  /** The column of the first names. */
  @FXML
  private ChoiceBox<Integer> firstBox;
  /** The column of the last names. */
  @FXML
  private ChoiceBox<Integer> lastBox;
  /** The column of the e-mail addresses. */
  @FXML
  private ChoiceBox<Integer> emailBox;
  /** The column of the phone numbers. */
  @FXML
  private ChoiceBox<Integer> phoneBox;
  /** The preview. */
  @FXML
  private TableView<ImportRow> table;
  /** The summary of the preview, or the reason why there is none. */
  @FXML
  private Label summary;
  /** Whether the possible duplicates are imported too. */
  @FXML
  private CheckBox duplicatesBox;
  /** The button importing the persons. */
  @FXML
  private Button importButton;
  /** The button choosing a CSV file. */
  @FXML
  private Button fileButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the import.
   *
   * @param main the main window.
   */
  ImportController(final MainController main) {
    this.main = main;
  }

  /**
   * Prepares the controls and the columns of the preview; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    fileButton.setGraphic(Icon.IMPORT.graphic());
    separatorBox.getItems().setAll(SEPARATORS);
    separatorBox.setConverter(Converters.showing(separator -> messages.get("separator." + (int) separator)));
    headerBox.setSelected(model.header());
    column("import.line", row -> String.valueOf(row.line()), true);
    column("import.person", row -> row.person() == null ? "" : row.person().fullName(), false);
    column("import.email", row -> row.person() == null || row.person().email() == null ? ""
                                  : row.person().email().value(), false);
    column("import.phone", row -> row.person() == null || row.person().phone() == null ? ""
                                  : row.person().phone().value(), false);
    column("import.remark", this::remark, false);
    text.textProperty().addListener((property, before, after) -> {
      model.setText(after);
      update();
    });
    separatorBox.valueProperty().addListener((property, before, after) -> changed(() -> model.setSeparator(after)));
    headerBox.selectedProperty().addListener((property, before, after) -> changed(() -> model.setHeader(after)));
    mapping(firstBox, ImportModel.FIRST_NAME);
    mapping(lastBox, ImportModel.LAST_NAME);
    mapping(emailBox, ImportModel.EMAIL);
    mapping(phoneBox, ImportModel.PHONE);
    duplicatesBox.selectedProperty().addListener((property, before, after) -> update());
    update();
  }

  /**
   * Adds a column to the preview.
   *
   * @param key the key of the title of the column.
   * @param value gives the text of a row.
   * @param number {@code true} for a column of numbers, aligned to the right.
   */
  private void column(final String key, final Function<ImportRow, String> value, final boolean number) {
    final TableColumn<ImportRow, String> column = new TableColumn<>(main.messages().get(key));
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
    Views.style(column, Views.NUMBER, number);
    table.getColumns().add(column);
  }

  /**
   * Follows the choice of the column of a value.
   *
   * @param box the list of the columns.
   * @param field the value: {@link ImportModel#FIRST_NAME}…
   */
  private void mapping(final ChoiceBox<Integer> box, final int field) {
    box.valueProperty().addListener((property, before, after) -> {
      if (after != null) {
        changed(() -> model.setColumn(field, after));
      }
    });
  }

  /**
   * Applies a change of a control to the values, unless the controls are being filled by the program.
   *
   * @param change the change.
   */
  private void changed(final Runnable change) {
    if (!filling) {
      change.run();
      update();
    }
  }

  // *******************************************************************************************************************
  // Preview
  // *******************************************************************************************************************

  /**
   * Shows the controls and the preview again from the values.
   */
  private void update() {
    filling = true;
    separatorBox.setValue(model.separator());
    final List<String> names = model.columnNames();
    final List<Integer> choices = new ArrayList<>(List.of(ColumnMapping.NONE));
    IntStream.range(0, names.size()).forEach(choices::add);
    for (final ChoiceBox<Integer> box : List.of(firstBox, lastBox, emailBox, phoneBox)) {
      box.getItems().clear();
      box.setConverter(Converters.showing(column -> columnName(column, names)));
      box.getItems().setAll(choices);
    }
    firstBox.setValue(valid(model.column(ImportModel.FIRST_NAME), names.size()));
    lastBox.setValue(valid(model.column(ImportModel.LAST_NAME), names.size()));
    emailBox.setValue(valid(model.column(ImportModel.EMAIL), names.size()));
    phoneBox.setValue(valid(model.column(ImportModel.PHONE), names.size()));
    filling = false;
    preview = model.preview(main.directory());
    table.getItems().setAll(preview);
    final String problem = model.problem().map(main.messages()::get).orElse(null);
    final long valid = preview.stream().filter(ImportRow::isValid).count();
    final long duplicates = preview.stream().filter(row -> !row.duplicates().isEmpty()).count();
    summary.setText(problem != null ? problem
                    : main.messages().get("import.summary", ImportModel.persons(preview, duplicatesBox.isSelected())
                                                                      .size(), preview.size() - valid, duplicates));
    importButton.setDisable(ImportModel.persons(preview, duplicatesBox.isSelected()).isEmpty());
  }

  /**
   * Names a column in the lists of the columns: its number and its title, or "none".
   *
   * @param column the column, {@link ColumnMapping#NONE}, or {@code null} while the list is being filled again.
   * @param names the titles of the columns.
   *
   * @return the text of the column, empty for {@code null}.
   */
  private String columnName(final Integer column, final List<String> names) {
    if (column == null) {
      return "";
    }
    return column == ColumnMapping.NONE ? main.messages().get("import.none")
           : main.messages().get("import.column", column + 1, names.get(column));
  }

  /**
   * Keeps the column of a value only if it exists.
   *
   * @param column the column, or {@link ColumnMapping#NONE}.
   * @param width the number of columns.
   *
   * @return the column, or {@link ColumnMapping#NONE} if it is beyond the table.
   */
  private static int valid(final int column, final int width) {
    return column < width ? column : ColumnMapping.NONE;
  }

  /**
   * Explains a row of the preview: its errors, or its possible duplicates.
   *
   * @param row the row.
   *
   * @return the remark, empty for a row to import without question.
   */
  private String remark(final ImportRow row) {
    if (!row.isValid()) {
      return row.errors().stream().map(error -> main.messages().get("error.validation." + error.name()))
                .collect(Collectors.joining(" "));
    }
    return row.duplicates().isEmpty() ? "" : main.messages().get("import.duplicate", row.duplicates().stream()
        .map(Person::fullName).collect(Collectors.joining(DirectoryController.SEPARATOR)));
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Reads a CSV file chosen by the user into the text.
   */
  @FXML
  void chooseFile() {
    main.dialogs().openFile("dialog.import.directory", "filter.csv", "*.csv;*.txt")
        .ifPresent(source -> main.submit(store -> store.readText(source), text::setText));
  }

  /**
   * Adds the valid persons of the preview to the directory, then goes back to the directory.
   */
  @FXML
  void importPersons() {
    final List<Person> persons = ImportModel.persons(preview, duplicatesBox.isSelected());
    main.updateDirectory(directory -> directory.addAll(persons), directory -> {
      main.showDirectory();
      main.notify("info.imported", persons.size());
    });
  }

  /**
   * Goes back to the directory without importing anything.
   */
  @FXML
  void cancel() {
    main.showDirectory();
  }

}
