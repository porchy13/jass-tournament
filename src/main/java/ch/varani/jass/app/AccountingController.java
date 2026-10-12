package ch.varani.jass.app;

import ch.varani.jass.accounting.AccountingSummary;
import ch.varani.jass.accounting.CategoryTotal;
import ch.varani.jass.domain.Attachment;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.EntryType;
import ch.varani.jass.domain.IndividualRegistrations;
import ch.varani.jass.domain.Ledger;
import ch.varani.jass.domain.LedgerEntry;
import ch.varani.jass.domain.Money;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.Registrations;
import ch.varani.jass.domain.Team;
import ch.varani.jass.domain.TeamRegistrations;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.converter.LocalDateStringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The accounting of an open edition (F8): the financial overview, the registration fee and the players who paid it,
 * the entries (incomes and expenses) and their supporting documents.
 *
 * <p><b>Layout</b> (DESIGN.md §6): at the left, the summary ({@link AccountingSummary}: fees paid, incomes and
 * expenses per category, totals, balance, fees outstanding), the fee per player with a box per player telling
 * whether it paid, and the entries by date; in the detail panel ({@link DetailLayout}), the selected entry or a new
 * one (the primary action of the toolbar, Shortcut+N): date, type, category (proposed from those already used),
 * description and amount, then its supporting documents.</p>
 *
 * <p><b>Amounts</b> ({@link #amount(String)}): in CHF, typed with a dot or a comma as decimal separator, spaces and
 * apostrophes ignored, e.g. {@code 1'234.50} or {@code 1 234,50}; shown in the format of the locale.</p>
 *
 * <p><b>Saving</b>: the fee and the boxes of the players are saved as soon as they change; an entry is saved by its
 * button, or deleted after confirmation (button, or Delete in the table). Every change is saved at once
 * ({@link MainController#update(MainController.OpenEdition)}), without showing the view again.</p>
 *
 * <p><b>Supporting documents</b>: PDF, PNG or JPEG, of a saved entry only, added by the file chooser or dropped from
 * the file manager; they are stored in the edition file and referenced by the entry in one operation that blocks the
 * window ({@link MainController#submitAlone}), so that no other save drops them meanwhile. A document is opened with
 * the application of the system through a temporary copy, and removed from the entry after confirmation.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class AccountingController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The characters ignored in an amount: spaces and the group separators of the Swiss locales. */
  private static final Pattern SEPARATORS = Pattern.compile("[\\s'’\\u202F]");
  /** The style class of the totals of the summary. */
  private static final String TOTAL = "total";
  /** The height of a row of the tables, in pixels (DESIGN.md §5). */
  private static final double ROW_HEIGHT = 44;
  /** The bytes of a kilobyte, for the size of the documents. */
  private static final long KILOBYTE = 1024;

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * A player in the table of the registration fees.
   *
   * @param participant the number of its team, or its number in individual mode.
   * @param index its index in the team, 0 in individual mode.
   * @param player the player.
   */
  record PaymentRow(int participant, int index, Player player) {
  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The identifier of the edition file. */
  private final UUID id;
  /** The edition, replaced at each change. */
  private Edition edition;
  /** The identifier of the entry in the form, or {@code null} for a new entry. */
  private UUID selected;
  /** Whether the table of the entries is being filled by the program, so that its listener ignores the change. */
  private boolean filling;
  /** The layout of the detail panel. */
  private DetailLayout layout;
  /** The name and date of the edition. */
  @FXML
  private Label title;
  /** The settings of the edition. */
  @FXML
  private Label settings;
  /** The summary of the accounting. */
  @FXML
  private GridPane summary;
  /** The registration fee of a player. */
  @FXML
  private TextField fee;
  /** The number of players who paid. */
  @FXML
  private Label feeStatus;
  /** Why the fee typed is rejected. */
  @FXML
  private Label feeProblem;
  /** The players and whether they paid. */
  @FXML
  private TableView<PaymentRow> payments;
  /** The entries of the accounting. */
  @FXML
  private TableView<LedgerEntry> entries;
  /** The title of the form: new entry or entry. */
  @FXML
  private Label entryTitle;
  /** The date of the entry. */
  @FXML
  private DatePicker datePicker;
  /** The type of the entry. */
  @FXML
  private ComboBox<EntryType> typeBox;
  /** The category of the entry, typed or chosen among those already used. */
  @FXML
  private ComboBox<String> categoryBox;
  /** The description of the entry. */
  @FXML
  private TextField description;
  /** The amount of the entry. */
  @FXML
  private TextField amount;
  /** Why the entry cannot be saved. */
  @FXML
  private Label problem;
  /** The button deleting the entry. */
  @FXML
  private Button deleteButton;
  /** The button saving the entry. */
  @FXML
  private Button saveButton;
  /** The supporting documents of the entry, a target of the files dropped. */
  @FXML
  private VBox documents;
  /** How to add documents. */
  @FXML
  private Label documentsHint;
  /** The supporting documents of the entry. */
  @FXML
  private ListView<Attachment> attachments;
  /** The button adding a document. */
  @FXML
  private Button addDocumentButton;
  /** The button opening the selected document. */
  @FXML
  private Button openDocumentButton;
  /** The button removing the selected document. */
  @FXML
  private Button removeDocumentButton;
  /** The button closing the floating detail panel. */
  @FXML
  private Button closeDetail;
  /** The stack pane of the view. */
  @FXML
  private StackPane body;
  /** The split pane of the list and of the detail panel. */
  @FXML
  private SplitPane split;
  /** The detail panel: the entry. */
  @FXML
  private ScrollPane detail;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the accounting of an edition.
   *
   * @param main the main window.
   * @param open the open edition and its identifier.
   */
  AccountingController(final MainController main, final MainController.OpenEdition open) {
    this.main = main;
    this.id = open.id();
    this.edition = open.edition();
  }

  /**
   * Prepares the tables, the form and the documents, then shows the accounting; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    title.setText(main.title(edition));
    settings.setText(main.describe(edition));
    Icon.CLOSE.decorate(closeDetail, messages.get("button.close"));
    layout = new DetailLayout(main, body, split, detail, closeDetail);
    paymentColumns();
    entryColumns();
    datePicker.setConverter(new LocalDateStringConverter(messages.dateFormat(), messages.dateFormat()));
    typeBox.getItems().setAll(EntryType.values());
    typeBox.setConverter(Converters.showing(type -> messages.get("entry." + type.name())));
    attachments.setCellFactory(view -> new DocumentCell());
    openDocumentButton.disableProperty().bind(attachments.getSelectionModel().selectedItemProperty().isNull());
    removeDocumentButton.disableProperty().bind(attachments.getSelectionModel().selectedItemProperty().isNull());
    documents.setOnDragOver(event -> {
      event.acceptTransferModes(TransferMode.COPY);
      event.consume();
    });
    documents.setOnDragDropped(event -> {
      attach(files(event));
      event.setDropCompleted(true);
      event.consume();
    });
    fee.focusedProperty().addListener((property, before, focused) -> {
      if (!focused) {
        changeFee();
      }
    });
    refresh();
    fill(null);
  }

  /**
   * Prepares the table of the registration fees: number, player, and a box telling whether it paid.
   */
  private void paymentColumns() {
    final Messages messages = main.messages();
    payments.setPlaceholder(Views.emptyState(messages.get("accounting.no.player"), null));
    column(payments, messages.get("column.number"), row -> String.valueOf(row.participant()), true);
    column(payments, messages.get("accounting.player"), row -> row.player().fullName(), false);
    final TableColumn<PaymentRow, PaymentRow> paid = new TableColumn<>(messages.get("accounting.paid.column"));
    paid.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
    paid.setCellFactory(column -> new PaidCell());
    payments.getColumns().add(paid);
  }

  /**
   * Prepares the table of the entries: date, type, category, description, amount and number of documents; Enter
   * moves to the form, Delete deletes the entry.
   */
  private void entryColumns() {
    final Messages messages = main.messages();
    final Button create = new Button(messages.get("accounting.new"));
    create.setOnAction(event -> newEntry());
    entries.setPlaceholder(Views.emptyState(messages.get("accounting.empty"), create));
    column(entries, messages.get("column.date"), entry -> messages.date(entry.date()), true);
    column(entries, messages.get("accounting.type"), entry -> messages.get("entry." + entry.type().name()), false);
    column(entries, messages.get("accounting.category"), LedgerEntry::category, false);
    column(entries, messages.get("accounting.description"), LedgerEntry::description, false);
    column(entries, messages.get("accounting.amount"), entry -> messages.money(entry.amount().value()), true);
    column(entries, messages.get("accounting.documents"), entry -> String.valueOf(entry.attachments().size()), true);
    Views.keys(entries, () -> datePicker.requestFocus(), this::deleteEntry);
    entries.getSelectionModel().selectedItemProperty().addListener((property, before, after) -> {
      if (after != null && !filling) {
        fill(after);
        layout.open();
      }
    });
  }

  /**
   * Adds a column of texts to a table.
   *
   * @param <T> the type of the rows.
   * @param table the table.
   * @param heading the title of the column.
   * @param text gives the text of a row.
   * @param number {@code true} for a numeric column, aligned to the right.
   */
  private static <T> void column(final TableView<T> table, final String heading, final Function<T, String> text,
                                 final boolean number) {
    final TableColumn<T, String> column = new TableColumn<>(heading);
    column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
    Views.style(column, Views.NUMBER, number);
    table.getColumns().add(column);
  }

  // *******************************************************************************************************************
  // Display
  // *******************************************************************************************************************

  /**
   * Shows the accounting again: the summary, the fee, the players and the entries; the selected entry stays
   * selected without filling the form again.
   */
  private void refresh() {
    final Messages messages = main.messages();
    final AccountingSummary totals = AccountingSummary.of(edition);
    summary.getChildren().clear();
    line(messages.get("accounting.fees.paid", totals.paidPlayers(), totals.players()), totals.feesPaid(), false);
    totals.incomes().forEach(category -> line(category(category), category.amount(), false));
    line(messages.get("accounting.total.income"), totals.totalIncome(), true);
    totals.expenses().forEach(category -> line(category(category), category.amount(), false));
    line(messages.get("accounting.total.expenses"), totals.totalExpenses(), true);
    line(messages.get("accounting.balance"), totals.balance(), true);
    line(messages.get("accounting.fees.outstanding", totals.players() - totals.paidPlayers()),
         totals.feesOutstanding(), false);
    fee.setText(messages.money(edition.ledger().fee().value()));
    feeStatus.setText(messages.get("accounting.paid", totals.paidPlayers(), totals.players()));
    payments.getItems().setAll(paymentRows());
    payments.setPrefHeight((Math.max(1, payments.getItems().size()) + 1) * ROW_HEIGHT);
    filling = true;
    entries.getItems().setAll(edition.ledger().entries().stream()
                                     .sorted(Comparator.comparing(LedgerEntry::date)).toList());
    entries.getItems().stream().filter(entry -> entry.id().equals(selected)).findFirst()
           .ifPresent(entries.getSelectionModel()::select);
    filling = false;
    entries.setPrefHeight((Math.max(1, entries.getItems().size()) + 1) * ROW_HEIGHT);
    categoryBox.getItems().setAll(edition.ledger().categories());
  }

  /**
   * Describes the total of a category: its name and its number of entries.
   *
   * @param category the total of the category.
   *
   * @return the description.
   */
  private String category(final CategoryTotal category) {
    return main.messages().get("accounting.category.total", category.category(), category.entries());
  }

  /**
   * Adds a line to the summary.
   *
   * @param text the description of the line.
   * @param value the amount, in CHF.
   * @param total {@code true} for a total, in semi-bold.
   */
  private void line(final String text, final BigDecimal value, final boolean total) {
    final Label name = new Label(text);
    final Label money = new Label(main.messages().money(value));
    GridPane.setHalignment(money, HPos.RIGHT);
    Views.style(name, TOTAL, total);
    Views.style(money, TOTAL, total);
    Views.style(money, "error", value.signum() < 0);
    summary.addRow(summary.getRowCount(), name, money);
  }

  /**
   * Lists the players of the registrations, team by team.
   *
   * @return one row per player.
   */
  private List<PaymentRow> paymentRows() {
    final List<PaymentRow> rows = new ArrayList<>();
    final Registrations registrations = edition.registrations();
    for (int participant = 1; participant <= registrations.size(); participant++) {
      final List<Player> players = switch (registrations) {
        case final TeamRegistrations teams -> teams.get(participant).players();
        case final IndividualRegistrations individuals -> List.of(individuals.get(participant));
      };
      for (int index = 0; index < players.size(); index++) {
        rows.add(new PaymentRow(participant, index, players.get(index)));
      }
    }
    return rows;
  }

  /**
   * Fills the form with an entry, or empties it for a new entry (today, an expense); the documents can be added once
   * the entry is saved.
   *
   * @param entry the entry, or {@code null} for a new one.
   */
  private void fill(final LedgerEntry entry) {
    final Messages messages = main.messages();
    final boolean known = entry != null;
    selected = known ? entry.id() : null;
    entryTitle.setText(messages.get(known ? "accounting.entry" : "accounting.new"));
    datePicker.setValue(known ? entry.date() : main.today());
    typeBox.setValue(known ? entry.type() : EntryType.EXPENSE);
    categoryBox.getEditor().setText(known ? entry.category() : "");
    description.setText(known ? entry.description() : "");
    amount.setText(known ? messages.money(entry.amount().value()) : "");
    attachments.getItems().setAll(known ? entry.attachments() : List.of());
    problem.setText("");
    saveButton.setText(messages.get(known ? "accounting.save" : "accounting.add"));
    deleteButton.setDisable(!known);
    documents.setDisable(!known);
    documentsHint.setText(messages.get(known ? "accounting.documents.hint" : "accounting.documents.later"));
  }

  // *******************************************************************************************************************
  // Changes
  // *******************************************************************************************************************

  /**
   * Takes a changed edition: it is saved at once and the accounting shown again.
   *
   * @param changed the edition.
   */
  private void change(final Edition changed) {
    edition = changed;
    main.update(new MainController.OpenEdition(id, edition));
    refresh();
  }

  /**
   * Saves the registration fee typed, if it changed; a fee that is not an amount is rejected with a message.
   */
  @FXML
  void changeFee() {
    try {
      final Money value = amount(fee.getText());
      feeProblem.setText("");
      if (!value.equals(edition.ledger().fee())) {
        change(edition.withLedger(edition.ledger().withFee(value)));
      }
    } catch (final DomainException e) {
      feeProblem.setText(main.messages().error(e));
    }
  }

  /**
   * Records whether a player paid its registration fee.
   *
   * @param row the player.
   * @param paid {@code true} if it paid.
   */
  private void setPaid(final PaymentRow row, final boolean paid) {
    final Player player = row.player().withPaid(paid);
    final Registrations changed = switch (edition.registrations()) {
      case final TeamRegistrations teams -> {
        final List<Player> players = new ArrayList<>(teams.get(row.participant()).players());
        players.set(row.index(), player);
        yield teams.replace(row.participant(), new Team(players));
      }
      case final IndividualRegistrations individuals -> individuals.replace(row.participant(), player);
    };
    change(edition.withRegistrations(changed));
  }

  /**
   * Empties the form for a new entry, opens the detail panel and moves the focus to the date.
   */
  @FXML
  void newEntry() {
    entries.getSelectionModel().clearSelection();
    fill(null);
    layout.open();
    datePicker.requestFocus();
  }

  /**
   * Saves the entry of the form (button or Shortcut+S): a new entry is added, an entry is replaced with its
   * documents; a missing date or a value the domain rejects (blank category, amount) is explained.
   */
  @FXML
  void saveEntry() {
    final Messages messages = main.messages();
    if (datePicker.getValue() == null) {
      problem.setText(messages.get("accounting.date.missing"));
      return;
    }
    try {
      final Money money = amount(amount.getText());
      final String category = categoryBox.getEditor().getText();
      final LedgerEntry entry = selected == null
          ? LedgerEntry.create(datePicker.getValue(), typeBox.getValue(), category, description.getText(), money)
          : new LedgerEntry(selected, datePicker.getValue(), typeBox.getValue(), category, description.getText(),
                            money, edition.ledger().get(selected).attachments());
      final Ledger ledger = selected == null ? edition.ledger().add(entry) : edition.ledger().replace(entry);
      selected = entry.id();
      change(edition.withLedger(ledger));
      fill(entry);
      main.notify("info.entry.saved");
    } catch (final DomainException e) {
      problem.setText(messages.error(e));
    }
  }

  /**
   * Deletes the entry of the form, after confirmation, with its references to its documents.
   */
  @FXML
  void deleteEntry() {
    final LedgerEntry entry = edition.ledger().get(selected);
    final String name = name(entry);
    if (main.dialogs().confirmDestructive("confirm.delete.entry", name, entry.attachments().size())) {
      change(edition.withLedger(edition.ledger().remove(entry.id())));
      newEntry();
      main.notify("info.entry.deleted", name);
    }
  }

  /**
   * Names an entry for the messages: its description, or its category without description.
   *
   * @param entry the entry.
   *
   * @return the name.
   */
  private static String name(final LedgerEntry entry) {
    return entry.description().isEmpty() ? entry.category() : entry.description();
  }

  // *******************************************************************************************************************
  // Supporting documents
  // *******************************************************************************************************************

  /**
   * Adds a document chosen by the user to the entry.
   */
  @FXML
  void addDocument() {
    main.dialogs().openFile("dialog.document", "filter.documents", "*.pdf;*.png;*.jpg;*.jpeg")
        .ifPresent(file -> attach(List.of(file)));
  }

  /**
   * Stores documents in the edition file and attaches them to the entry, in one operation that blocks the window.
   *
   * @param files the documents; nothing happens without document.
   */
  private void attach(final List<Path> files) {
    if (files.isEmpty()) {
      return;
    }
    final UUID entry = selected;
    main.submitAlone(store -> {
      final List<Attachment> added = new ArrayList<>();
      for (final Path file : files) {
        added.add(store.addAttachment(id, file));
      }
      return added;
    }, added -> {
      LedgerEntry changed = edition.ledger().get(entry);
      for (final Attachment attachment : added) {
        changed = changed.withAttachment(attachment);
      }
      change(edition.withLedger(edition.ledger().replace(changed)));
      attachments.getItems().setAll(changed.attachments());
      main.notify("info.documents.added", added.size());
    });
  }

  /**
   * Returns the files dropped.
   *
   * @param event the drop.
   *
   * @return the paths of the files, empty when the drop holds no file.
   */
  static List<Path> files(final DragEvent event) {
    return Optional.ofNullable(event.getDragboard()).map(Dragboard::getFiles).orElse(List.of()).stream()
                   .map(File::toPath).toList();
  }

  /**
   * Opens the selected document with the application of the system, through a temporary copy.
   */
  @FXML
  void openDocument() {
    final Attachment attachment = attachments.getSelectionModel().getSelectedItem();
    main.showDocument(store -> store.openCopy(id, attachment));
  }

  /**
   * Removes the selected document from the entry, after confirmation; it leaves the edition file at the next save.
   */
  @FXML
  void removeDocument() {
    final Attachment attachment = attachments.getSelectionModel().getSelectedItem();
    if (main.dialogs().confirmDestructive("confirm.remove.document", attachment.name())) {
      final LedgerEntry changed = edition.ledger().get(selected).withoutAttachment(attachment.sha256());
      change(edition.withLedger(edition.ledger().replace(changed)));
      attachments.getItems().setAll(changed.attachments());
      main.notify("info.document.removed", attachment.name());
    }
  }

  // *******************************************************************************************************************
  // Helpers
  // *******************************************************************************************************************

  /**
   * Reads an amount typed by the user: a dot or a comma as decimal separator, spaces and apostrophes ignored.
   *
   * @param text the text typed.
   *
   * @return the amount.
   *
   * @throws DomainException with {@link ch.varani.jass.domain.ValidationError#INVALID_AMOUNT} if the text is not an
   *                         amount of CHF.
   */
  static Money amount(final String text) {
    return Money.of(SEPARATORS.matcher(text).replaceAll("").replace(',', '.'));
  }

  // *******************************************************************************************************************
  // Cells
  // *******************************************************************************************************************

  /**
   * The box telling whether a player paid its registration fee; changing it saves the edition.
   */
  private final class PaidCell extends TableCell<PaymentRow, PaymentRow> {

    /** The box. */
    private final CheckBox box = new CheckBox();

    /**
     * Creates the cell; the box records the payment of the player of the row.
     */
    PaidCell() {
      super();
      box.setOnAction(event -> setPaid(getItem(), box.isSelected()));
    }

    /**
     * Shows whether the player of the row paid, or nothing for an empty row.
     *
     * @param row the player.
     * @param empty whether the row is empty.
     */
    @Override
    protected void updateItem(final PaymentRow row, final boolean empty) {
      super.updateItem(row, empty);
      if (empty) {
        setGraphic(null);
        return;
      }
      box.setSelected(row.player().paid());
      box.setAccessibleText(main.messages().get("accounting.paid.box", row.player().fullName()));
      setGraphic(box);
    }

  }

  /**
   * A supporting document in the list: its name, its kind and its size.
   */
  private final class DocumentCell extends ListCell<Attachment> {

    /**
     * Creates an empty line of the list of the documents.
     */
    DocumentCell() {
      super();
    }

    /**
     * Shows a document, or nothing for an empty line.
     *
     * @param attachment the document.
     * @param empty whether the line is empty.
     */
    @Override
    protected void updateItem(final Attachment attachment, final boolean empty) {
      super.updateItem(attachment, empty);
      setText(empty ? null : main.messages().get("accounting.document", attachment.name(),
                                                 attachment.type().extension().toUpperCase(Locale.ROOT),
                                                 Math.max(1, attachment.size() / KILOBYTE)));
    }

  }

}
