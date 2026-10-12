package ch.varani.jass.app;

import javafx.application.HostServices;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The dialogs of the system and the message boxes of the application: choice of a file to open or to save,
 * confirmation, offer with details (an update and its notes), progress of a long operation, error, the panel of
 * the settings, and the panel about the application with its main action (a donation).
 *
 * <p><b>Message boxes</b> (DESIGN.md §7): a confirmation shows the question as header ({@code <key>.title}), its
 * consequence as text ({@code <key>.text}), and two buttons in the order of the system ({@link ButtonBar}): the action
 * named by what it does ({@code <key>.action}) and Cancel. The action of a destructive confirmation is not the default
 * button and is shown in red. The boxes take the style sheets and the accent of the window. The information that needs
 * no decision is shown in the status bar instead ({@link MainController#notify(String, Object...)}).</p>
 *
 * <p>The windows that wait for the user ({@link FileChooser}, {@link Alert#showAndWait()}) are reached through
 * {@link Natives}, so that the tests replace them: the native file choosers are not available in the headless tests.
 * The production values ({@link Natives#SYSTEM}) are the methods of JavaFX themselves.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class Dialogs {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The number of lines shown by the details of an offer. */
  private static final int DETAIL_ROWS = 10;

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * Shows a file chooser.
   */
  @FunctionalInterface
  interface Chooser {

    /**
     * Shows a file chooser and waits for the choice of the user.
     *
     * @param chooser the file chooser.
     * @param owner the owner window.
     *
     * @return the chosen file, or {@code null} if the user cancelled.
     */
    File show(FileChooser chooser, Window owner);

  }

  /**
   * Shows a message box and waits for the answer of the user.
   */
  @FunctionalInterface
  interface Question {

    /**
     * Shows a message box and waits for the answer.
     *
     * @param alert the message box.
     *
     * @return the button chosen, or nothing if the box was closed.
     */
    Optional<ButtonType> ask(Alert alert);

  }

  /**
   * Opens a document with the application of the system, e.g. a supporting document of the accounting.
   */
  @FunctionalInterface
  interface Opener {

    /**
     * Opens a document.
     *
     * @param services the services of the host of the application.
     * @param uri the address of the document, e.g. {@code file:///…}.
     */
    void open(HostServices services, String uri);

  }

  /**
   * The windows of the system: the file choosers, the message boxes and the applications opening the documents.
   *
   * @param open shows the chooser of a file to open.
   * @param save shows the chooser of a file to save.
   * @param question shows a message box and waits for the answer.
   * @param notice shows a message box without waiting.
   * @param opener opens a document with the application of the system.
   */
  record Natives(Chooser open, Chooser save, Question question, Consumer<Alert> notice, Opener opener) {

    /** The windows of the system: the methods of JavaFX. */
    static final Natives SYSTEM = new Natives(FileChooser::showOpenDialog, FileChooser::showSaveDialog,
                                              Alert::showAndWait, Alert::show, HostServices::showDocument);

  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The owner window of the dialogs. */
  private final Supplier<Window> owner;
  /** The texts of the user interface. */
  private final Messages messages;
  /** The windows of the system. */
  private final Natives natives;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the dialogs of a window.
   *
   * @param owner gives the owner window of the dialogs.
   * @param messages the texts of the user interface.
   * @param natives the windows of the system.
   */
  Dialogs(final Supplier<Window> owner, final Messages messages, final Natives natives) {
    this.owner = owner;
    this.messages = messages;
    this.natives = natives;
  }

  // *******************************************************************************************************************
  // Files
  // *******************************************************************************************************************

  /**
   * Asks the user for a file to open.
   *
   * @param titleKey the key of the title of the dialog.
   * @param filterKey the key of the description of the files.
   * @param pattern the patterns of the names of the files, separated by semicolons, e.g. {@code *.png;*.jpg}.
   *
   * @return the chosen file, or nothing if the user cancelled.
   */
  Optional<Path> openFile(final String titleKey, final String filterKey, final String pattern) {
    return Optional.ofNullable(natives.open().show(chooser(titleKey, filterKey, pattern), front()))
                   .map(File::toPath);
  }

  /**
   * Asks the user for a file to write.
   *
   * @param titleKey the key of the title of the dialog.
   * @param initialName the proposed name of the file.
   * @param filterKey the key of the description of the files.
   * @param pattern the pattern of the names of the files, e.g. {@code *.zip}.
   *
   * @return the chosen file, or nothing if the user cancelled.
   */
  Optional<Path> saveFile(final String titleKey, final String initialName, final String filterKey,
                          final String pattern) {
    final FileChooser chooser = chooser(titleKey, filterKey, pattern);
    chooser.setInitialFileName(initialName);
    return Optional.ofNullable(natives.save().show(chooser, front())).map(File::toPath);
  }

  /**
   * Returns the owner of a dialog: the window having the focus, e.g. the panel of the settings when a dialog is opened
   * from it, so that this window comes back to the front when the dialog closes; the window of the application when
   * no window has the focus.
   *
   * @return the owner window.
   */
  private Window front() {
    return Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(owner.get());
  }

  /**
   * Creates a file chooser.
   *
   * @param titleKey the key of the title of the dialog.
   * @param filterKey the key of the description of the files.
   * @param pattern the patterns of the names of the files, separated by semicolons: each one is an extension of the
   *                filter, as the file choosers expect.
   *
   * @return the file chooser.
   */
  private FileChooser chooser(final String titleKey, final String filterKey, final String pattern) {
    final FileChooser chooser = new FileChooser();
    chooser.setTitle(messages.get(titleKey));
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get(filterKey),
                                                                             pattern.split(";")));
    return chooser;
  }

  // *******************************************************************************************************************
  // Message boxes
  // *******************************************************************************************************************

  /**
   * Asks the user to confirm an operation that can be undone or that destroys nothing.
   *
   * @param key the prefix of the keys of the question ({@code .title}), of its consequence ({@code .text}) and of the
   *            action ({@code .action}).
   * @param arguments the values inserted in the texts.
   *
   * @return {@code true} if the user chose the action.
   */
  boolean confirm(final String key, final Object... arguments) {
    return ask(key, false, arguments);
  }

  /**
   * Asks the user to confirm a destructive operation: the action is not the default button and is shown in red.
   *
   * @param key the prefix of the keys of the question ({@code .title}), of its consequence ({@code .text}) and of the
   *            action ({@code .action}).
   * @param arguments the values inserted in the texts.
   *
   * @return {@code true} if the user chose the action.
   */
  boolean confirmDestructive(final String key, final Object... arguments) {
    return ask(key, true, arguments);
  }

  /**
   * Shows a confirmation and waits for the answer.
   *
   * @param key the prefix of the keys of the texts.
   * @param destructive {@code true} if the operation destroys something.
   * @param arguments the values inserted in the texts.
   *
   * @return {@code true} if the user chose the action.
   */
  private boolean ask(final String key, final boolean destructive, final Object... arguments) {
    final Alert alert = alert(Alert.AlertType.CONFIRMATION, messages.get(key + ".text", arguments));
    alert.setHeaderText(messages.get(key + ".title", arguments));
    final ButtonType action = new ButtonType(messages.get(key + ".action", arguments),
                                             ButtonBar.ButtonData.OK_DONE);
    alert.getButtonTypes().setAll(action, new ButtonType(messages.get("button.cancel"),
                                                         ButtonBar.ButtonData.CANCEL_CLOSE));
    if (destructive) {
      final Button button = (Button) alert.getDialogPane().lookupButton(action);
      button.setDefaultButton(false);
      button.getStyleClass().add("danger");
    }
    return natives.question().ask(alert).map(ButtonType::getButtonData)
                  .filter(ButtonBar.ButtonData.OK_DONE::equals).isPresent();
  }

  /**
   * Proposes an operation that the user may postpone, with details to read before choosing (e.g. an update and its
   * notes): the question as header ({@code <key>.title}), its consequence as text ({@code <key>.text}), the details
   * under their heading ({@code <key>.details}) in a read-only text, and two buttons in the order of the system: the
   * action ({@code <key>.action}), default button, and the postponement ({@code <key>.decline}).
   *
   * @param key the prefix of the keys of the texts.
   * @param details the details, shown as they are.
   * @param arguments the values inserted in the texts.
   *
   * @return {@code true} if the user chose the action.
   */
  boolean offer(final String key, final String details, final Object... arguments) {
    final Alert alert = alert(Alert.AlertType.CONFIRMATION, null);
    alert.setHeaderText(messages.get(key + ".title", arguments));
    final Label text = new Label(messages.get(key + ".text", arguments));
    text.setWrapText(true);
    text.getStyleClass().add("consequence");
    final TextArea area = new TextArea(details);
    area.setId("details");
    area.setEditable(false);
    area.setWrapText(true);
    area.setPrefRowCount(DETAIL_ROWS);
    final Label heading = new Label(messages.get(key + ".details"));
    heading.getStyleClass().add("section-head");
    heading.setLabelFor(area);
    final VBox content = new VBox(text, heading, area);
    content.getStyleClass().addAll("panel-body", "offer");
    alert.getDialogPane().setContent(content);
    final ButtonType action = new ButtonType(messages.get(key + ".action", arguments), ButtonBar.ButtonData.OK_DONE);
    alert.getButtonTypes().setAll(action, new ButtonType(messages.get(key + ".decline"),
                                                         ButtonBar.ButtonData.CANCEL_CLOSE));
    return natives.question().ask(alert).map(ButtonType::getButtonData)
                  .filter(ButtonBar.ButtonData.OK_DONE::equals).isPresent();
  }

  /**
   * Shows the progress of a long operation without waiting, e.g. a download, with a Cancel button: the operation
   * closes the box when it is done; the result of the box is set when the user cancels or closes it.
   *
   * @param titleKey the key of the title of the operation.
   * @param content the progress of the operation.
   * @param arguments the values inserted in the title.
   *
   * @return the box, shown.
   */
  Alert progress(final String titleKey, final Node content, final Object... arguments) {
    final Alert alert = alert(Alert.AlertType.NONE, null);
    alert.setHeaderText(messages.get(titleKey, arguments));
    alert.getDialogPane().setContent(content);
    alert.getButtonTypes().setAll(new ButtonType(messages.get("button.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
    natives.notice().accept(alert);
    return alert;
  }

  /**
   * Shows an error to the user, without waiting: what happened and how to correct it.
   *
   * @param error the error, explained by {@link Messages#error(Throwable)}.
   */
  void error(final Throwable error) {
    final Alert alert = alert(Alert.AlertType.ERROR, messages.error(error));
    alert.setHeaderText(messages.get("error.title"));
    natives.notice().accept(alert);
  }

  /**
   * Shows a panel, e.g. the settings, and waits until the user closes it; its controls act at once.
   *
   * @param titleKey the key of the title of the panel.
   * @param content the content of the panel.
   */
  void panel(final String titleKey, final Node content) {
    final Alert alert = alert(Alert.AlertType.NONE, null);
    alert.setHeaderText(messages.get(titleKey));
    alert.getDialogPane().setContent(content);
    alert.getButtonTypes().setAll(new ButtonType(messages.get("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
    natives.question().ask(alert);
  }

  /**
   * Shows a panel with a main action, e.g. the window about the application and its donation, and waits until the user
   * closes it: the action ({@code actionKey}) is the default button, in the accent colour, and closes the panel; Close
   * closes it without the action.
   *
   * @param titleKey the key of the title of the panel.
   * @param content the content of the panel.
   * @param actionKey the key of the name of the action.
   *
   * @return {@code true} if the user chose the action.
   */
  boolean panel(final String titleKey, final Node content, final String actionKey) {
    final Alert alert = alert(Alert.AlertType.NONE, null);
    alert.setHeaderText(messages.get(titleKey));
    alert.getDialogPane().setContent(content);
    alert.getButtonTypes().setAll(new ButtonType(messages.get(actionKey), ButtonBar.ButtonData.OK_DONE),
                                  new ButtonType(messages.get("button.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
    return natives.question().ask(alert).map(ButtonType::getButtonData)
                  .filter(ButtonBar.ButtonData.OK_DONE::equals).isPresent();
  }

  /**
   * Creates a message box owned by the window in front ({@link #front()}), titled with the name of the application,
   * with the style sheets and the accent of the window of the application, without the coloured icon of the type of
   * the box (DESIGN.md §3: one accent colour).
   *
   * @param type the type of the box.
   * @param text the text of the box, or {@code null}.
   *
   * @return the message box.
   */
  private Alert alert(final Alert.AlertType type, final String text) {
    final Alert alert = new Alert(type, text);
    final Window window = owner.get();
    alert.initOwner(front());
    alert.setTitle(messages.get("application.title"));
    alert.setHeaderText(null);
    alert.setGraphic(null);
    final Scene scene = window.getScene();
    final DialogPane pane = alert.getDialogPane();
    pane.getStylesheets().setAll(scene.getStylesheets());
    pane.setStyle(scene.getRoot().getStyle());
    return alert;
  }

}
