package ch.varani.jass.app;

import ch.varani.jass.directory.ContactStatus;
import ch.varani.jass.directory.Csv;
import ch.varani.jass.directory.Directory;
import ch.varani.jass.directory.DirectoryExport;
import ch.varani.jass.directory.Participation;
import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.DomainException;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The directory (F14): the persons who took part in the editions, searched by name (accents and case ignored) and
 * filtered by edition; the form of a person (names, optional e-mail address, phone number and notes, contact status);
 * its participations; and the actions: new person, save, delete, erase the contact details, merge two persons, copy
 * the e-mail addresses or phone numbers, import, export.
 *
 * <p><b>Rules</b> (§6 of the development plan): a new person that may duplicate another one (same names, e-mail or
 * phone) is created only after confirmation; marking a person <i>do not contact</i> offers to erase its contact
 * details; the copies of the contact details leave out the persons who are not contactable; a deleted person is gone
 * with its contact details. When the directory could not be read at start-up, it is shown empty and cannot be
 * changed.</p>
 *
 * <p><b>Layout</b> (DESIGN.md §6): the persons at the left with their number, the selected person in the detail
 * panel ({@link DetailLayout}), floating when the window is narrow; the new person is the primary action of the
 * toolbar, the other actions its tools, with the filter by edition and the search (Shortcut+F); the status bar shows
 * the selection. Without person, the view says how to add or import some.</p>
 *
 * <p><b>Keyboard</b>: Shortcut+N starts a new person, Shortcut+S saves the person of the form, so that persons can be
 * typed one after the other without the mouse.</p>
 *
 * <p>Every change is applied and saved at once on the background queue
 * ({@link MainController#updateDirectory(java.util.function.UnaryOperator, java.util.function.Consumer)}); once saved,
 * the changed person is selected, unless the user started a new person meanwhile, whose form is then kept.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class DirectoryController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The separator of the copied contact details. */
  static final String SEPARATOR = ", ";
  /** The keys of the titles of the columns of the export. */
  private static final List<String> EXPORT_COLUMNS = List.of("export.first.name", "export.last.name", "export.email",
                                                             "export.phone", "export.notes", "export.status",
                                                             "export.status.date", "export.editions");

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The directory shown, or {@code null} if it could not be read. */
  private Directory directory;
  /** The person in the form, or {@code null} for a new person. */
  private Person editing;
  /** The number of new persons started, to know whether the user started one while a change was being saved. */
  private int started;
  /** The layout of the detail panel. */
  private DetailLayout layout;
  /** The toolbar. */
  @FXML
  private HBox toolbar;
  /** The button of a new person. */
  @FXML
  private Button newButton;
  /** The button copying the e-mail addresses. */
  @FXML
  private Button emailsButton;
  /** The button copying the phone numbers. */
  @FXML
  private Button phonesButton;
  /** The button importing persons. */
  @FXML
  private Button importButton;
  /** The button exporting the persons. */
  @FXML
  private Button exportButton;
  /** The button closing the floating detail panel. */
  @FXML
  private Button closeDetail;
  /** The stack pane of the view. */
  @FXML
  private StackPane body;
  /** The split pane of the list and of the detail panel. */
  @FXML
  private SplitPane split;
  /** The detail panel: the form of a person. */
  @FXML
  private ScrollPane detail;
  /** The name of the person of the form. */
  @FXML
  private Label personTitle;
  /** The search. */
  @FXML
  private TextField search;
  /** The filter by edition. */
  @FXML
  private ChoiceBox<Participation> editionFilter;
  /** The number of persons shown. */
  @FXML
  private Label count;
  /** The persons. */
  @FXML
  private TableView<Person> table;
  /** The column of the last names. */
  @FXML
  private TableColumn<Person, String> lastColumn;
  /** The column of the first names. */
  @FXML
  private TableColumn<Person, String> firstColumn;
  /** The column of the e-mail addresses. */
  @FXML
  private TableColumn<Person, String> emailColumn;
  /** The column of the phone numbers. */
  @FXML
  private TableColumn<Person, String> phoneColumn;
  /** The column of the contact statuses. */
  @FXML
  private TableColumn<Person, String> statusColumn;
  /** The first name of the form. */
  @FXML
  private TextField firstField;
  /** The last name of the form. */
  @FXML
  private TextField lastField;
  /** The e-mail address of the form. */
  @FXML
  private TextField emailField;
  /** The phone number of the form. */
  @FXML
  private TextField phoneField;
  /** The notes of the form. */
  @FXML
  private TextField notesField;
  /** The contact status of the form. */
  @FXML
  private ChoiceBox<ContactStatus> statusBox;
  /** Why the form cannot be saved. */
  @FXML
  private Label problem;
  /** The participations of the person of the form. */
  @FXML
  private ListView<String> participations;
  /** The warning shown when the directory could not be read. */
  @FXML
  private Label unavailable;
  /** The button deleting the person of the form. */
  @FXML
  private Button deleteButton;
  /** The button erasing the contact details of the person of the form. */
  @FXML
  private Button eraseButton;
  /** The button merging two selected persons. */
  @FXML
  private Button mergeButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the screen of the directory.
   *
   * @param main the main window.
   */
  DirectoryController(final MainController main) {
    this.main = main;
    this.directory = main.directory();
  }

  /**
   * Fills the table, the filters and the form; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    unavailable.setVisible(directory == null);
    unavailable.setManaged(directory == null);
    toolbar.setDisable(directory == null);
    body.setDisable(directory == null);
    newButton.setGraphic(Icon.PLUS.graphic());
    Icon.MERGE.decorate(mergeButton, messages.get("directory.merge"));
    Icon.MAIL.decorate(emailsButton, messages.get("directory.copy.emails"));
    Icon.PHONE.decorate(phonesButton, messages.get("directory.copy.phones"));
    Icon.IMPORT.decorate(importButton, messages.get("directory.import"));
    Icon.EXPORT.decorate(exportButton, messages.get("directory.export"));
    Icon.CLOSE.decorate(closeDetail, messages.get("button.close"));
    search.setPromptText(messages.get("directory.search", Views.shortcut("Shortcut+F")));
    layout = new DetailLayout(main, body, split, detail, closeDetail);
    Views.keys(table, null, () -> {
      if (editing != null) {
        delete();
      }
    });
    column(lastColumn, Person::lastName);
    column(firstColumn, Person::firstName);
    column(emailColumn, person -> person.email() == null ? "" : person.email().value());
    column(phoneColumn, person -> person.phone() == null ? "" : person.phone().value());
    column(statusColumn, person -> messages.get("status." + person.status().name()));
    table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    table.getSelectionModel().getSelectedItems().addListener((ListChangeListener<Person>) change -> selected());
    participations.setPlaceholder(new Label(messages.get("directory.no.participation")));
    participations.setCellFactory(view -> new ParticipationCell());
    statusBox.getItems().setAll(ContactStatus.values());
    statusBox.setConverter(Converters.showing(status -> messages.get("status." + status.name())));
    editionFilter.setConverter(Converters.showing(participation -> participation == null ? messages.get("directory.all")
                                         : messages.get("directory.edition", participation.editionName(),
                                                        messages.date(participation.editionDate()))));
    search.textProperty().addListener((property, before, after) -> showPersons(null));
    editionFilter.valueProperty().addListener((property, before, after) -> showPersons(null));
    fill(null);
    refresh(null);
  }

  /**
   * Shows a text of the persons in a column.
   *
   * @param column the column.
   * @param text gives the text of a person.
   */
  private static void column(final TableColumn<Person, String> column, final Function<Person, String> text) {
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
  }


  // *******************************************************************************************************************
  // Display
  // *******************************************************************************************************************

  /**
   * Shows a new version of the directory: the filter of the editions and the persons, a person selected.
   *
   * @param select the identifier of the person to select, or {@code null}.
   */
  private void refresh(final UUID select) {
    final Participation chosen = editionFilter.getValue();
    final List<Participation> editions = new ArrayList<>();
    editions.add(null);
    if (directory != null) {
      directory.persons().stream().flatMap(person -> person.participations().stream())
               .collect(Collectors.toMap(Participation::edition, participation -> participation, (a, b) -> a))
               .values().stream().sorted(Comparator.comparing(Participation::editionDate).reversed())
               .forEach(editions::add);
    }
    editionFilter.getItems().setAll(editions);
    editionFilter.setValue(chosen == null ? null : editions.stream().filter(Objects::nonNull)
                                                           .filter(participation -> participation.edition()
                                                                                    .equals(chosen.edition()))
                                                           .findFirst().orElse(null));
    showPersons(select);
  }

  /**
   * Shows the persons matching the search and the filter.
   *
   * @param select the identifier of the person to select, or {@code null}.
   */
  private void showPersons(final UUID select) {
    final List<Person> shown = shown();
    table.getItems().setAll(shown);
    count.setText(main.messages().get("directory.count", shown.size()));
    table.setPlaceholder(placeholder());
    shown.stream().filter(person -> person.id().equals(select)).findFirst()
         .ifPresent(person -> table.getSelectionModel().select(person));
  }

  /**
   * Returns the empty state of the table: how to add persons when the directory is empty, or that none matches the
   * search and the filter.
   *
   * @return the empty state.
   */
  private Node placeholder() {
    final Messages messages = main.messages();
    if (directory != null && !directory.persons().isEmpty()) {
      return Views.emptyState(messages.get("directory.no.match"), null);
    }
    final Button add = new Button(messages.get("directory.import"));
    add.setOnAction(event -> importPersons());
    return Views.emptyState(messages.get("directory.empty", Views.shortcut("Shortcut+N")), add);
  }

  /**
   * Returns the persons matching the search and the filter of the edition.
   *
   * @return the persons, sorted by last name.
   */
  private List<Person> shown() {
    if (directory == null) {
      return List.of();
    }
    final Participation edition = editionFilter.getValue();
    final Stream<Person> found = directory.search(search.getText()).stream();
    return edition == null ? found.toList() : found.filter(person -> person.participations().stream()
        .anyMatch(participation -> participation.edition().equals(edition.edition()))).toList();
  }

  /**
   * Shows the selected person in the form, and enables the actions on the selection.
   */
  private void selected() {
    final List<Person> selection = table.getSelectionModel().getSelectedItems();
    mergeButton.setDisable(selection.size() != 2);
    fill(selection.size() == 1 ? selection.getFirst() : null);
    main.showSelection(selection.isEmpty() ? "" : main.messages().get("directory.selection", selection.size(),
                                                                        table.getItems().size()));
    if (selection.size() == 1) {
      layout.open();
    }
  }

  /**
   * Fills the form with a person, or empties it for a new person.
   *
   * @param person the person, or {@code null}.
   */
  private void fill(final Person person) {
    editing = person;
    personTitle.setText(person == null ? main.messages().get("directory.new.person") : person.fullName());
    final PersonForm form = person == null ? new PersonForm() : PersonForm.of(person);
    firstField.setText(form.firstName());
    lastField.setText(form.lastName());
    emailField.setText(form.email());
    phoneField.setText(form.phone());
    notesField.setText(form.notes());
    statusBox.setValue(form.status());
    deleteButton.setDisable(person == null);
    eraseButton.setDisable(person == null);
    problem.setText("");
    participations.getItems().setAll(person == null ? List.of() : person.participations().stream()
        .sorted(Comparator.comparing(Participation::editionDate).reversed())
        .map(this::participation).toList());
  }

  /**
   * Describes a participation: date and name of the edition, and the team mate in team mode.
   *
   * @param participation the participation.
   *
   * @return the description.
   */
  private String participation(final Participation participation) {
    final Messages messages = main.messages();
    final String edition = messages.get("directory.edition", participation.editionName(),
                                        messages.date(participation.editionDate()));
    if (participation.teamMate() == null) {
      return edition;
    }
    return directory.find(participation.teamMate())
                    .map(mate -> messages.get("directory.team.mate", edition, mate.fullName()))
                    .orElse(edition);
  }

  /**
   * Applies a change to the directory, saved at once, then shows the new directory; when the user started a new
   * person meanwhile, its form is kept and nothing is selected.
   *
   * @param change the change.
   * @param select the identifier of the person to select afterwards, or {@code null}.
   */
  private void change(final UnaryOperator<Directory> change, final UUID select) {
    final int before = started;
    main.updateDirectory(change, latest -> {
      directory = latest;
      if (started != before) {
        refresh(null);
        return;
      }
      fill(null);
      refresh(select);
    });
  }

  // *******************************************************************************************************************
  // Actions on a person
  // *******************************************************************************************************************

  /**
   * Empties the form, to type a new person.
   */
  @FXML
  void newPerson() {
    started++;
    table.getSelectionModel().clearSelection();
    fill(null);
    layout.open();
    firstField.requestFocus();
  }

  /**
   * Saves the person of the form (button or Shortcut+S): a new person after a confirmation when it may duplicate
   * another one; a person marked not to be contacted, after the offer to erase its contact details.
   */
  @FXML
  void save() {
    final PersonForm form = new PersonForm();
    form.setFirstName(firstField.getText());
    form.setLastName(lastField.getText());
    form.setEmail(emailField.getText());
    form.setPhone(phoneField.getText());
    form.setNotes(notesField.getText());
    form.setStatus(statusBox.getValue());
    final Person person;
    try {
      person = form.apply(editing, main.today());
    } catch (final DomainException e) {
      problem.setText(main.messages().error(e));
      return;
    }
    if (editing == null) {
      final List<Person> duplicates = directory.duplicatesOf(person);
      if (duplicates.isEmpty() || main.dialogs().confirm("confirm.duplicate", names(duplicates))) {
        change(latest -> latest.add(person), person.id());
      }
      return;
    }
    final boolean blocked = person.status() == ContactStatus.DO_NOT_CONTACT && editing.isContactable()
                            && !person.withoutContactDetails().equals(person);
    final Person saved = blocked && main.dialogs().confirmDestructive("confirm.erase.contact", person.fullName())
                         ? person.withoutContactDetails() : person;
    change(latest -> latest.replace(saved), saved.id());
  }

  /**
   * Deletes the person of the form, after confirmation: its names, contact details and participations are gone.
   */
  @FXML
  void delete() {
    final Person person = editing;
    if (main.dialogs().confirmDestructive("confirm.delete.person", person.fullName())) {
      change(latest -> latest.remove(person.id()), null);
      main.notify("info.person.deleted", person.fullName());
    }
  }

  /**
   * Erases the contact details of the person of the form, after confirmation.
   */
  @FXML
  void erase() {
    final Person person = editing;
    if (main.dialogs().confirmDestructive("confirm.erase.contact", person.fullName())) {
      change(latest -> latest.replace(latest.get(person.id()).withoutContactDetails()), person.id());
    }
  }

  /**
   * Merges the two selected persons, after confirmation: the person with the more participations is kept.
   */
  @FXML
  void merge() {
    final List<Person> selection = new ArrayList<>(table.getSelectionModel().getSelectedItems());
    selection.sort(Comparator.comparingInt((Person person) -> person.participations().size()).reversed());
    final Person kept = selection.getFirst();
    final Person removed = selection.getLast();
    if (main.dialogs().confirmDestructive("confirm.merge", removed.fullName(), kept.fullName())) {
      change(latest -> latest.merge(kept.id(), removed.id()), kept.id());
    }
  }

  // *******************************************************************************************************************
  // Contact, import and export
  // *******************************************************************************************************************

  /**
   * Copies the e-mail addresses of the selected persons, or of all the persons shown, leaving out the persons not to
   * be contacted.
   */
  @FXML
  void copyEmails() {
    final List<String> emails = Directory.emailsOf(contacts());
    main.copy(String.join(SEPARATOR, emails));
    main.notify("info.copied.emails", emails.size());
  }

  /**
   * Copies the phone numbers of the selected persons, or of all the persons shown, leaving out the persons not to be
   * contacted.
   */
  @FXML
  void copyPhones() {
    final List<String> phones = Directory.phonesOf(contacts());
    main.copy(String.join(SEPARATOR, phones));
    main.notify("info.copied.phones", phones.size());
  }

  /**
   * Returns the persons whose contact details are copied: the selection, or all the persons shown.
   *
   * @return the persons.
   */
  private List<Person> contacts() {
    final List<Person> selection = table.getSelectionModel().getSelectedItems();
    return selection.isEmpty() ? table.getItems() : List.copyOf(selection);
  }

  /**
   * Shows the import of persons.
   */
  @FXML
  void importPersons() {
    main.showImport();
  }

  /**
   * Exports the persons shown, with every value held about them, to a CSV file chosen by the user.
   */
  @FXML
  void export() {
    final List<Person> persons = List.copyOf(table.getItems());
    final List<String> header = EXPORT_COLUMNS.stream().map(key -> main.messages().get(key)).toList();
    main.dialogs().saveFile("dialog.export.directory", main.messages().get("directory.file") + ".csv", "filter.csv",
                            "*.csv")
        .ifPresent(target -> main.submit(store -> {
          store.writeText(target, Csv.format(DirectoryExport.table(persons, header), Csv.SEMICOLON));
          return target;
        }, done -> main.notify("info.directory.exported")));
  }

  /**
   * Joins the full names of persons.
   *
   * @param persons the persons.
   *
   * @return their full names, separated by commas.
   */
  private static String names(final List<Person> persons) {
    return persons.stream().map(Person::fullName).collect(Collectors.joining(SEPARATOR));
  }

  // *******************************************************************************************************************
  // Cells
  // *******************************************************************************************************************

  /**
   * A participation in the list of the form: its text wrapped on several lines, never cut, so that the edition and the
   * team mate stay readable in the narrow detail panel.
   *
   * <p>Its preferred width is zero: the list then gives it the width of the list, not the width of its text on one
   * line, and the list has no horizontal scroll bar.</p>
   */
  private static final class ParticipationCell extends ListCell<String> {

    /**
     * Creates an empty line of the list of the participations.
     */
    ParticipationCell() {
      super();
      setWrapText(true);
      setPrefWidth(0);
    }

    /**
     * Shows a participation, or nothing for an empty line.
     *
     * @param participation the text of the participation.
     * @param empty whether the line is empty.
     */
    @Override
    protected void updateItem(final String participation, final boolean empty) {
      super.updateItem(participation, empty);
      setText(empty ? null : participation);
    }

  }

}
