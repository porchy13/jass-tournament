package ch.varani.jass.app;

import ch.varani.jass.directory.Directory;
import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.Player;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.persistence.EditionHeader;
import ch.varani.jass.persistence.FileFormatException;
import ch.varani.jass.report.DocumentOptions;
import ch.varani.jass.service.StoreService;
import ch.varani.jass.update.Version;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The main window of the application (DESIGN.md §6): the menu bar, the side navigation, the view of the moment and
 * the status bar.
 *
 * <p><b>Navigation</b>: the side navigation lists the editions, the directory and the trash, with their counts, then,
 * while an edition is open, its parts: the registration, the schedule, the scores, the ranking, the external display
 * and the accounting. The views are the
 * list of the editions (start screen), the form of a new or duplicated edition, the parts of an open edition, the
 * directory, the import of persons and the trash.</p>
 *
 * <p><b>Menus</b> (DESIGN.md §2, §10): File (new element of the view, save of the form of the view when it has
 * one, import, export and deletion of the open edition, backup of the whole data folder, settings on macOS, quit
 * elsewhere), View (editions, directory, trash, search, side navigation), Tools (options, except on macOS) and Help
 * (about the application, {@link AboutController});
 * shortcuts with the key of the system (Ctrl, or ⌘ on macOS); F6 and Shift+F6 move the focus from a panel to the
 * next. The menu bar is the global one on macOS.</p>
 *
 * <p><b>Changes</b>: every change of an edition is saved at once ({@link #save(OpenEdition)}, or
 * {@link #update(OpenEdition)} when the view shows the change itself, e.g. while scores are typed), and every change of
 * the directory is applied and saved at once on the background queue
 * ({@link #updateDirectory(UnaryOperator, Consumer)}), so that it never overwrites the participations recorded
 * meanwhile; the views show the copy of the directory returned by the queue ({@link #directory()}). The status bar
 * shows whether operations are pending, and the notices that need no decision ({@link #notify(String, Object...)}),
 * for {@value #NOTICE_SECONDS} seconds.</p>
 *
 * <p><b>External display</b> (F18): a window of its own ({@link DisplayWindow}), opened from the part of the edition
 * or from the menu View, closed with the main window; what it shows ({@link DisplayState}) follows the open edition
 * and belongs to the session.</p>
 *
 * <p><b>Update</b> (F17): at start-up, a newer release published on GitHub is looked for in the background and
 * offered to the user ({@link UpdateController}); its installer is launched once the pending saves are done, then the
 * application quits ({@link #install(Path)}).</p>
 *
 * <p><b>Settings</b>: the theme, the accent, the reduced animations, the size, position and state of the window and
 * the positions of the dividers are read at start-up and saved in the data folder ({@link Settings}); the minimum
 * size of the window is {@value #MIN_WIDTH} × {@value #MIN_HEIGHT}.</p>
 *
 * <p><b>Files</b>: every operation on the data folder ({@link StoreService}) runs on the background queue
 * ({@link Worker}), in order; the views are updated on the JavaFX Application Thread when it is done, and the errors
 * are shown in a message box. The pending operations are completed before the application exits
 * ({@link #shutdown()}).</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class MainController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The minimum width of the window, in pixels. */
  static final double MIN_WIDTH = 960;
  /** The minimum height of the window, in pixels. */
  static final double MIN_HEIGHT = 600;
  /** How long a notice stays in the status bar, in seconds. */
  static final double NOTICE_SECONDS = 6;
  /** The pattern of the edition files. */
  static final String EDITION_FILES = "*.jass";
  /** The characters that cannot appear in a file name on some systems, replaced in the proposed names. */
  private static final Pattern FORBIDDEN = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
  /** The width of the side navigation at the first start, in pixels (DESIGN.md §5). */
  static final double SIDEBAR_WIDTH = 224;
  /** The horizontal padding of an item of the side navigation, both sides (components.css). */
  private static final double NAV_PADDING = 16;
  /** The controls of a panel that take the focus on F6, in the order they are tried. */
  private static final List<String> FOCUSABLE = List.of(".nav-item", ".text-field", ".table-view", ".list-view");
  /** The new element of the views that have none of their own: a new edition. */
  private static final String NEW_EDITION = "menu.new.edition";
  /** The key of the name of the application, title of the window and of the window about the application. */
  private static final String APPLICATION_TITLE = "application.title";
  /** The sizes of the icon of the windows ({@code icon-<size>.png} in this package), in pixels. */
  static final List<Integer> ICON_SIZES = List.of(16, 32, 64, 128, 256);

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * The places of the side navigation.
   */
  enum Place {

    /** The list of the editions. */
    EDITIONS,
    /** The directory. */
    DIRECTORY,
    /** The trash. */
    TRASH,
    /** The registration of the open edition. */
    REGISTRATION,
    /** The schedule of the open edition. */
    SCHEDULE,
    /** The score sheets of the open edition. */
    SCORES,
    /** The ranking of the open edition. */
    RANKING,
    /** The external display of the open edition. */
    DISPLAY,
    /** The accounting of the open edition. */
    ACCOUNTING

  }

  /**
   * The new element of a view, created by the item New of the menu File (Shortcut+N).
   *
   * @param key the key of the text of the item.
   * @param action creates the element.
   */
  record Creation(String key, Runnable action) {
  }

  /**
   * Writes a PDF document of an edition, e.g. {@code ScheduleDocument::write}.
   */
  @FunctionalInterface
  interface DocumentWriter {

    /**
     * Writes the document.
     *
     * @param edition the edition.
     * @param options the options of the documents.
     * @param out the stream of the file.
     *
     * @throws IOException if the document cannot be written.
     */
    void write(Edition edition, DocumentOptions options, OutputStream out) throws IOException;

  }

  /**
   * An operation on the data folder.
   *
   * @param <T> the type of its result.
   */
  @FunctionalInterface
  interface StoreOperation<T> {

    /**
     * Runs the operation on the background thread.
     *
     * @param store the data folder.
     *
     * @return the result.
     *
     * @throws IOException if a file cannot be read or written.
     * @throws FileFormatException if a file read is not valid.
     */
    T run(StoreService store) throws IOException, FileFormatException;

  }

  /**
   * The outcome of an operation that blocks the window: its result, or its failure.
   *
   * @param <T> the type of the result.
   * @param value the result, or {@code null} after a failure.
   * @param error the failure, or {@code null}.
   */
  private record Outcome<T>(T value, Exception error) {
  }

  /**
   * An open edition and the identifier of its file.
   *
   * @param id the identifier of the file.
   * @param edition the edition.
   */
  record OpenEdition(UUID id, Edition edition) {
  }

  /**
   * The numbers shown in the side navigation.
   *
   * @param editions the number of editions.
   * @param trash the number of editions in the trash.
   */
  private record Counts(int editions, int trash) {
  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The window. */
  private final Stage stage;
  /** The texts of the user interface. */
  private final Messages messages;
  /** The dialogs of the window. */
  private final Dialogs dialogs;
  /** The environment of the application: data folder, clock, solvers. */
  private final AppContext context;
  /** The services of the host, to open the documents with the applications of the system. */
  private final HostServices host;
  /** The background queue of the operations on the files. */
  private final Worker worker;
  /** The open edition, or {@code null} when no edition is open. */
  private final ObjectProperty<OpenEdition> current = new SimpleObjectProperty<>();
  /** The items of the side navigation. */
  private final Map<Place, ToggleButton> places = new EnumMap<>(Place.class);
  /** The counts of the side navigation. */
  private final Map<Place, Label> counts = new EnumMap<>(Place.class);
  /** The items of the side navigation, selected one at a time. */
  private final ToggleGroup navigation = new ToggleGroup();
  /** Clears the notice of the status bar. */
  private final PauseTransition noticeTimer = new PauseTransition(Duration.seconds(NOTICE_SECONDS));
  /** The data folder, or {@code null} if it could not be opened. */
  private StoreService service;
  /** The latest copy of the directory, or {@code null} if it could not be read. */
  private Directory directory;
  /** The settings of the user interface. */
  private Settings settings = Settings.DEFAULT;
  /** The themes of the windows: the main window, then the display once opened. */
  private final List<Theme> themes = new ArrayList<>();
  /** What the external display shows. */
  private final DisplayState displayState = new DisplayState();
  /** The window of the external display, or {@code null} before it is first needed. */
  private DisplayWindow displayWindow;
  /** The part of the open edition shown. */
  private Place section = Place.REGISTRATION;
  /** The new element of the view shown. */
  private Creation creation;
  /** Saves the form of the view shown (Shortcut+S), or {@code null} when the view has no form to save. */
  private Runnable saving;
  /** The root of the window. */
  @FXML
  private BorderPane root;
  /** The menu bar. */
  @FXML
  private MenuBar menuBar;
  /** The item creating a new element of the view. */
  @FXML
  private MenuItem newItem;
  /** The item saving the form of the view. */
  @FXML
  private MenuItem saveItem;
  /** The item exporting the open edition. */
  @FXML
  private MenuItem exportItem;
  /** The item deleting the open edition. */
  @FXML
  private MenuItem deleteItem;
  /** The item opening the external display. */
  @FXML
  private MenuItem displayItem;
  /** The item focusing the search of the view. */
  @FXML
  private MenuItem searchItem;
  /** The item showing the settings, in the File menu on macOS. */
  @FXML
  private MenuItem settingsItem;
  /** The separator before the settings. */
  @FXML
  private SeparatorMenuItem settingsSeparator;
  /** The item quitting, except on macOS where the application menu quits. */
  @FXML
  private MenuItem quitItem;
  /** The separator before the quit item. */
  @FXML
  private SeparatorMenuItem quitSeparator;
  /** The menu of the tools, except on macOS. */
  @FXML
  private Menu toolsMenu;
  /** The item showing or hiding the side navigation. */
  @FXML
  private CheckMenuItem sidebarItem;
  /** The split pane of the side navigation and of the view. */
  @FXML
  private SplitPane split;
  /** The side navigation. */
  @FXML
  private VBox sidebar;
  /** The place of the view. */
  @FXML
  private StackPane viewArea;
  /** The state of the saves. */
  @FXML
  private Label saveState;
  /** The dot of the state of the saves. */
  @FXML
  private Circle saveDot;
  /** The selection of the view. */
  @FXML
  private Label selection;
  /** The notice of the moment. */
  @FXML
  private Label notice;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the main window.
   *
   * @param stage the window.
   * @param messages the texts of the user interface.
   * @param context the environment of the application.
   * @param host the services of the host of the application.
   */
  MainController(final Stage stage, final Messages messages, final AppContext context, final HostServices host) {
    this.stage = stage;
    this.messages = messages;
    this.context = context;
    this.host = host;
    this.dialogs = new Dialogs(() -> stage, messages, context.natives());
    this.worker = new Worker(dialogs::error);
    this.creation = new Creation(NEW_EDITION, this::newEdition);
  }

  /**
   * Opens the window: opens the data folder and reads the settings, loads the window with its theme and size, looks
   * for an update in the background, then shows the list of the editions; reports a damaged directory or a data
   * folder that cannot be opened.
   */
  void start() {
    IOException failure = null;
    try {
      service = new StoreService(context.dataFolder());
      settings = Settings.of(service.settings());
    } catch (final IOException e) {
      failure = e;
    }
    stage.setTitle(messages.get(APPLICATION_TITLE));
    stage.getIcons().setAll(icons());
    stage.setScene(new Scene(load("main.fxml", this), Math.max(settings.width(), MIN_WIDTH),
                             Math.max(settings.height(), MIN_HEIGHT)));
    stage.setMinWidth(MIN_WIDTH);
    stage.setMinHeight(MIN_HEIGHT);
    place();
    style(stage.getScene());
    current.addListener((property, before, after) -> {
      if (after != null) {
        displayState.follow(after);
      }
    });
    stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> closeDisplay());
    menus();
    sidebar();
    statusBar();
    stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
    stage.show();
    new UpdateController(this, context.updater()).check();
    if (failure != null) {
      dialogs.error(failure);
      return;
    }
    directory = service.directory().orElse(null);
    service.directoryError().ifPresent(dialogs::error);
    showList();
  }

  /**
   * Places the window where it was, if it is still on a screen; centres it otherwise.
   */
  private void place() {
    stage.setMaximized(settings.maximized());
    final boolean known = !Double.isNaN(settings.x() + settings.y());
    if (known && !Screen.getScreensForRectangle(settings.x(), settings.y(), settings.width(), settings.height())
                        .isEmpty()) {
      stage.setX(settings.x());
      stage.setY(settings.y());
    } else {
      stage.centerOnScreen();
    }
  }

  /**
   * Prepares the menus for the system: the global menu bar on macOS, the settings in the File menu on macOS and in
   * the Tools menu elsewhere, the quit item except on macOS, the items needing an open edition.
   */
  private void menus() {
    menuBar.setUseSystemMenuBar(true);
    settingsItem.setVisible(context.mac());
    settingsSeparator.setVisible(context.mac());
    quitItem.setVisible(!context.mac());
    quitSeparator.setVisible(!context.mac());
    toolsMenu.setVisible(!context.mac());
    exportItem.disableProperty().bind(current.isNull());
    deleteItem.disableProperty().bind(current.isNull());
    displayItem.disableProperty().bind(current.isNull());
    sidebarItem.selectedProperty().addListener((property, before, after) -> showSidebar(after));
  }

  /**
   * Fills the side navigation: the editions, the directory and the trash, then the parts of the open edition.
   */
  private void sidebar() {
    SplitPane.setResizableWithParent(sidebar, false);
    final Label edition = new Label();
    edition.getStyleClass().add("group-head");
    edition.textProperty().bind(current.map(open -> open.edition().name()).orElse(""));
    sidebar.getChildren().addAll(item(Place.EDITIONS, Icon.CALENDAR, "nav.editions"),
                                 item(Place.DIRECTORY, Icon.PEOPLE, "nav.directory"),
                                 item(Place.TRASH, Icon.TRASH, "nav.trash"), edition,
                                 item(Place.REGISTRATION, Icon.LIST, "nav.registration"),
                                 item(Place.SCHEDULE, Icon.GRID, "nav.schedule"),
                                 item(Place.SCORES, Icon.PENCIL, "nav.scores"),
                                 item(Place.RANKING, Icon.PODIUM, "nav.ranking"),
                                 item(Place.DISPLAY, Icon.SCREEN, "nav.display"),
                                 item(Place.ACCOUNTING, Icon.WALLET, "nav.accounting"));
    for (final Node node : List.of(edition, places.get(Place.REGISTRATION), places.get(Place.SCHEDULE),
                                   places.get(Place.SCORES), places.get(Place.RANKING), places.get(Place.DISPLAY),
                                   places.get(Place.ACCOUNTING))) {
      node.visibleProperty().bind(current.isNotNull());
      node.managedProperty().bind(current.isNotNull());
    }
    navigation.selectedToggleProperty().addListener((property, before, after) -> {
      if (after == null) {
        before.setSelected(true);
      }
    });
    split.setDividerPositions(Double.isNaN(settings.sidebar())
                              ? SIDEBAR_WIDTH / Math.max(settings.width(), MIN_WIDTH) : settings.sidebar());
  }

  /**
   * Creates an item of the side navigation: its icon, its name and its count.
   *
   * @param place the place shown by the item.
   * @param icon the icon of the item.
   * @param key the key of the name of the item.
   *
   * @return the item.
   */
  private ToggleButton item(final Place place, final Icon icon, final String key) {
    final Label name = new Label(messages.get(key));
    name.getStyleClass().add("nav-name");
    final Label count = new Label();
    count.getStyleClass().add("nav-count");
    final Region space = new Region();
    HBox.setHgrow(space, Priority.ALWAYS);
    final HBox line = new HBox(icon.graphic(), name, space, count);
    line.getStyleClass().add("nav-line");
    final ToggleButton item = new ToggleButton();
    item.getStyleClass().setAll("nav-item");
    item.setId(place.name().toLowerCase(Locale.ROOT) + "Nav");
    item.setGraphic(line);
    item.setAccessibleText(name.getText());
    item.setMaxWidth(Double.MAX_VALUE);
    line.prefWidthProperty().bind(item.widthProperty().subtract(NAV_PADDING));
    item.setToggleGroup(navigation);
    item.setOnAction(event -> go(place));
    places.put(place, item);
    counts.put(place, count);
    return item;
  }

  /**
   * Binds the status bar: the state of the saves.
   */
  private void statusBar() {
    saveState.textProperty().bind(worker.pending().map(pending -> messages.get(
        pending.intValue() > 0 ? "bar.saving" : "bar.saved")));
    worker.pending().addListener((property, before, after) -> {
      saveDot.getStyleClass().remove("busy");
      if (after.intValue() > 0) {
        saveDot.getStyleClass().add("busy");
      }
    });
    noticeTimer.setOnFinished(event -> notice.setText(""));
  }

  /**
   * Loads the icon of the windows, in each of its sizes: the system picks the one it needs (title bar, task bar…).
   *
   * @return the images of the icon, from the smallest.
   */
  static List<Image> icons() {
    return ICON_SIZES.stream().map(size -> new Image(MainController.class.getResource("icon-" + size + ".png")
                                                                         .toExternalForm())).toList();
  }

  /**
   * Loads a view.
   *
   * @param fxml the name of the FXML file, in this package.
   * @param controller the controller of the view.
   *
   * @return the root of the view.
   */
  Parent load(final String fxml, final Object controller) {
    final FXMLLoader loader = new FXMLLoader(MainController.class.getResource(fxml), messages.bundle());
    loader.setController(controller);
    try {
      return loader.load();
    } catch (final IOException e) {
      throw new UncheckedIOException("The view " + fxml + " cannot be loaded.", e);
    }
  }

  /**
   * Saves the settings with the state of the window, deletes the copies of the documents opened, and completes the
   * pending operations, e.g. the saves, before the application exits.
   *
   * @throws InterruptedException if the wait is interrupted.
   */
  void shutdown() throws InterruptedException {
    if (service != null) {
      settings = settings.withWindow(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(),
                                     stage.isMaximized());
      if (sidebarItem.isSelected()) {
        settings = settings.withDividers(split.getDividerPositions()[0], settings.detail());
      }
      saveSettings();
      submit(store -> {
        store.deleteCopies();
        return Boolean.TRUE;
      }, done -> { });
    }
    worker.finish(Worker.CLOSE_TIMEOUT);
  }

  // *******************************************************************************************************************
  // Shared with the views
  // *******************************************************************************************************************

  /**
   * Returns the texts of the user interface.
   *
   * @return the texts.
   */
  Messages messages() {
    return messages;
  }

  /**
   * Returns the dialogs of the window.
   *
   * @return the dialogs.
   */
  Dialogs dialogs() {
    return dialogs;
  }

  /**
   * Returns the current instant, e.g. for the start signal of a game.
   *
   * @return the instant of the clock of the application.
   */
  Instant now() {
    return context.clock().instant();
  }

  /**
   * Returns the time zone of the clock of the application, to show the times.
   *
   * @return the time zone.
   */
  ZoneId zone() {
    return context.clock().getZone();
  }

  /**
   * Applies the theme of the settings to the scene of a window, and follows its changes.
   *
   * @param scene the scene.
   */
  void style(final Scene scene) {
    final Theme theme = new Theme(scene, Platform.getPreferences().colorSchemeProperty(),
                                  Platform.getPreferences().accentColorProperty());
    theme.configure(settings.theme(), settings.systemAccent());
    themes.add(theme);
  }

  /**
   * Returns what the external display shows.
   *
   * @return the state of the display.
   */
  DisplayState displayState() {
    return displayState;
  }

  /**
   * Returns the window of the external display, created the first time.
   *
   * @return the window, hidden or shown.
   */
  DisplayWindow display() {
    if (displayWindow == null) {
      displayWindow = new DisplayWindow(this, displayState, context.clock());
    }
    return displayWindow;
  }

  /**
   * Closes the window of the external display, if it was created; called when the main window closes, so that the
   * application ends.
   */
  private void closeDisplay() {
    if (displayWindow != null) {
      displayWindow.close();
    }
  }

  /**
   * Returns today, for the proposed dates.
   *
   * @return the date of today.
   */
  LocalDate today() {
    return LocalDate.now(context.clock());
  }

  /**
   * Tells whether the animations are reduced.
   *
   * @return {@code true} if the changes are immediate.
   */
  boolean reducedMotion() {
    return settings.reducedMotion();
  }

  /**
   * Returns the settings of the user interface.
   *
   * @return the settings.
   */
  Settings settings() {
    return settings;
  }

  /**
   * Remembers the position of the divider of the detail panel, saved with the other settings.
   *
   * @param position the position, from 0 to 1.
   */
  void setDetailDivider(final double position) {
    settings = settings.withDividers(settings.sidebar(), position);
  }

  /**
   * Runs an operation on the data folder in the background, then hands its result to the JavaFX Application Thread.
   *
   * @param <T> the type of the result.
   * @param operation the operation.
   * @param done receives the result.
   */
  <T> void submit(final StoreOperation<T> operation, final Consumer<T> done) {
    worker.submit(() -> operation.run(service), done);
  }

  /**
   * Runs an operation on the data folder in the background while the window is disabled, so that no other change can
   * be saved meanwhile (e.g. a supporting document stored in the edition file, then referenced by the accounting);
   * the window is enabled again when the operation is done or has failed.
   *
   * @param <T> the type of the result.
   * @param operation the operation.
   * @param done receives the result, unless the operation fails (the error is then shown).
   */
  <T> void submitAlone(final StoreOperation<T> operation, final Consumer<T> done) {
    root.setDisable(true);
    submit(store -> {
      try {
        return new Outcome<>(operation.run(store), null);
      } catch (final Exception e) {
        return new Outcome<T>(null, e);
      }
    }, outcome -> {
      root.setDisable(false);
      if (outcome.error() != null) {
        dialogs.error(outcome.error());
        return;
      }
      done.accept(outcome.value());
    });
  }

  /**
   * Opens a document of the data folder with the application of the system, e.g. a supporting document: the document
   * is copied in the background, then opened.
   *
   * @param copy copies the document out of the data folder and returns the copy.
   */
  void showDocument(final StoreOperation<Path> copy) {
    submit(copy, file -> context.natives().opener().open(host, file.toUri().toString()));
  }

  /**
   * Installs an update: once the pending operations on the files are done, e.g. the saves, launches its installer
   * with the application of the system, then quits, so that the installer can replace the application (F17, Q27).
   *
   * @param installer the installer, downloaded and checked.
   */
  void install(final Path installer) {
    submit(store -> installer, file -> {
      context.natives().opener().open(host, file.toUri().toString());
      quit();
    });
  }

  /**
   * Creates the computation of the schedule of an edition.
   *
   * @param edition the edition.
   *
   * @return the computation.
   */
  Solving solving(final Edition edition) {
    return context.solving().apply(edition);
  }

  /**
   * Returns the latest copy of the directory.
   *
   * @return the directory, or {@code null} if it could not be read at start-up.
   */
  Directory directory() {
    return directory;
  }

  /**
   * Changes the directory on the background queue and saves it at once, then hands the new directory to a view.
   *
   * @param change the change, applied to the latest version of the directory.
   * @param done receives the new directory, on the JavaFX Application Thread.
   */
  void updateDirectory(final UnaryOperator<Directory> change, final Consumer<Directory> done) {
    submit(store -> store.updateDirectory(change), latest -> {
      directory = latest;
      showCounts();
      done.accept(latest);
    });
  }

  /**
   * Saves a changed edition at once, and shows it.
   *
   * @param edition the edition and its identifier.
   */
  void save(final OpenEdition edition) {
    showEdition(edition);
    write(edition);
  }

  /**
   * Saves a changed edition at once, without showing it again: the view that changed it shows the change itself, and
   * keeps the focus of the user.
   *
   * @param edition the edition and its identifier.
   */
  void update(final OpenEdition edition) {
    current.set(edition);
    write(edition);
  }

  /**
   * Saves an edition in the background, without showing it, then takes the directory with the participations
   * recorded by the save; used directly when the view that changed it is no longer shown.
   *
   * @param edition the edition and its identifier.
   */
  void write(final OpenEdition edition) {
    submit(store -> {
      store.save(edition.id(), edition.edition());
      return store.directory();
    }, latest -> directory = latest.orElse(null));
  }

  /**
   * Chooses the part of the open edition shown the next time it is shown.
   *
   * @param place a part of the edition: {@link Place#REGISTRATION}, {@link Place#SCHEDULE}, {@link Place#SCORES},
   *              {@link Place#RANKING}, {@link Place#DISPLAY} or {@link Place#ACCOUNTING}.
   */
  void setSection(final Place place) {
    section = place;
  }

  /**
   * Copies a text into the clipboard of the system.
   *
   * @param text the text.
   */
  void copy(final String text) {
    final ClipboardContent clip = new ClipboardContent();
    clip.putString(text);
    Clipboard.getSystemClipboard().setContent(clip);
  }

  /**
   * Shows a notice in the status bar for {@value #NOTICE_SECONDS} seconds: an information that needs no decision.
   *
   * @param key the key of the notice.
   * @param arguments the values inserted in the notice.
   */
  void notify(final String key, final Object... arguments) {
    notice.setText(messages.get(key, arguments));
    Motion.fadeIn(notice, settings.reducedMotion());
    noticeTimer.playFromStart();
  }

  /**
   * Shows the selection of the view in the status bar.
   *
   * @param text the text of the selection, empty without selection.
   */
  void showSelection(final String text) {
    selection.setText(text);
  }

  /**
   * Writes a PDF document of an edition to a file chosen by the user, with the logo of the application.
   *
   * @param titleKey the key of the title of the document, part of the proposed file name.
   * @param edition the edition.
   * @param document writes the document.
   */
  void exportDocument(final String titleKey, final Edition edition, final DocumentWriter document) {
    final String name = fileName(edition.name() + " – " + messages.get(titleKey)) + ".pdf";
    dialogs.saveFile("dialog.pdf", name, "filter.pdf", "*.pdf").ifPresent(target -> submit(store -> {
      final DocumentOptions options = new DocumentOptions(messages.locale(), store.logo().orElse(null));
      store.writeDocument(target, out -> document.write(edition, options, out));
      return target;
    }, done -> notify("info.document")));
  }

  /**
   * Describes an open edition for the title of its views: its name and date.
   *
   * @param edition the edition.
   *
   * @return the title.
   */
  String title(final Edition edition) {
    return messages.get("edition.title", edition.name(), messages.date(edition.date()));
  }

  /**
   * Describes the settings of an edition: mode, games, deals and points.
   *
   * @param edition the edition.
   *
   * @return the description.
   */
  String describe(final Edition edition) {
    return messages.get("edition.settings", messages.get("mode." + edition.mode().name()), edition.games(),
                        edition.deals().size(), edition.deals().pointsPerGame());
  }

  /**
   * Describes a side of a game: the team with its number and players, or the players with their numbers.
   *
   * @param edition the edition.
   * @param side the numbers of the participants of the side.
   *
   * @return the description.
   */
  String side(final Edition edition, final List<Integer> side) {
    if (edition.mode() == TournamentMode.TEAM) {
      final int team = side.getFirst();
      return messages.get("schedule.team", team, edition.teamRegistrations().get(team).players().stream()
          .map(Player::fullName).collect(Collectors.joining(" & ")));
    }
    return side.stream().map(player -> edition.individualRegistrations().get(player).fullName() + " (" + player + ")")
               .collect(Collectors.joining(" & "));
  }

  // *******************************************************************************************************************
  // Views
  // *******************************************************************************************************************

  /**
   * Shows a view in the window.
   *
   * @param view the root of the view.
   * @param edition the open edition, or {@code null} when the view is not a part of an edition.
   * @param place the item of the side navigation selected, or {@code null} for a form.
   * @param newElement the new element of the view, created by Shortcut+N.
   */
  private void show(final Parent view, final OpenEdition edition, final Place place, final Creation newElement) {
    show(view, edition, place, newElement, null);
  }

  /**
   * Shows a view with a form in the window: Shortcut+S saves it.
   *
   * @param view the root of the view.
   * @param edition the open edition, or {@code null} when the view is not a part of an edition.
   * @param place the item of the side navigation selected, or {@code null} for a form.
   * @param newElement the new element of the view, created by Shortcut+N.
   * @param save saves the form of the view, or {@code null} when the view has no form to save.
   */
  private void show(final Parent view, final OpenEdition edition, final Place place, final Creation newElement,
                    final Runnable save) {
    current.set(edition);
    stage.setTitle(edition == null ? messages.get(APPLICATION_TITLE)
                                   : messages.get("window.title", edition.edition().name()));
    viewArea.getChildren().setAll(view);
    creation = newElement;
    newItem.setText(messages.get(newElement.key()));
    saving = save;
    saveItem.setDisable(save == null);
    searchItem.setDisable(view.lookup("#search") == null);
    selection.setText("");
    if (place != null) {
      places.get(place).setSelected(true);
    }
    refreshCounts();
  }

  /**
   * Goes to a place of the side navigation.
   *
   * @param place the place.
   */
  private void go(final Place place) {
    final Runnable action = switch (place) {
      case EDITIONS -> this::showList;
      case DIRECTORY -> this::showDirectory;
      case TRASH -> this::showTrash;
      case REGISTRATION, SCHEDULE, SCORES, RANKING, DISPLAY, ACCOUNTING -> () -> showPart(place);
    };
    action.run();
  }

  /**
   * Shows a part of the open edition.
   *
   * @param place a part of the edition: {@link Place#REGISTRATION}, {@link Place#SCHEDULE}, {@link Place#SCORES},
   *              {@link Place#RANKING}, {@link Place#DISPLAY} or {@link Place#ACCOUNTING}.
   */
  void showPart(final Place place) {
    section = place;
    showEdition(current.get());
  }

  /**
   * Reads the numbers of editions in the list and in the trash in the background, then shows them with the number
   * of persons of the directory.
   */
  private void refreshCounts() {
    if (service == null) {
      return;
    }
    submit(store -> new Counts(store.list().editions().size(), store.listTrash().editions().size()), numbers -> {
      counts.get(Place.EDITIONS).setText(String.valueOf(numbers.editions()));
      counts.get(Place.TRASH).setText(String.valueOf(numbers.trash()));
      showCounts();
    });
  }

  /**
   * Shows the number of persons of the directory.
   */
  private void showCounts() {
    counts.get(Place.DIRECTORY).setText(directory == null ? "" : String.valueOf(directory.persons().size()));
  }

  /**
   * Shows the list of the editions, once read in the background.
   */
  @FXML
  void showList() {
    submit(StoreService::list, list -> show(load("editions.fxml", new EditionsController(this, list)), null,
                                            Place.EDITIONS, new Creation(NEW_EDITION, this::newEdition)));
  }

  /**
   * Shows the form of a new edition.
   */
  void newEdition() {
    showForm(EditionForm.create(today()));
  }

  /**
   * Shows the form of a new or duplicated edition.
   *
   * @param form the values of the form.
   */
  void showForm(final EditionForm form) {
    show(load("edition-form.fxml", new EditionFormController(this, form)), null, null,
         new Creation(NEW_EDITION, this::newEdition));
  }

  /**
   * Shows the form of the duplication of an edition, once read in the background.
   *
   * @param id the identifier of the duplicated edition.
   */
  void duplicate(final UUID id) {
    submit(store -> store.open(id), edition -> showForm(EditionForm.duplicate(edition)));
  }

  /**
   * Creates an edition in the background, then opens it.
   *
   * @param edition the new edition.
   */
  void create(final Edition edition) {
    section = Place.REGISTRATION;
    submit(store -> store.create(edition), id -> showEdition(new OpenEdition(id, edition)));
  }

  /**
   * Opens an edition, once read in the background.
   *
   * @param id the identifier of the edition.
   */
  void open(final UUID id) {
    section = Place.REGISTRATION;
    submit(store -> store.open(id), edition -> showEdition(new OpenEdition(id, edition)));
  }

  /**
   * Shows the part of an open edition chosen in the side navigation.
   *
   * @param edition the edition and its identifier.
   */
  private void showEdition(final OpenEdition edition) {
    switch (section) {
      case SCHEDULE -> show(load("schedule.fxml", new ScheduleController(this, edition.edition())), edition,
                            Place.SCHEDULE, new Creation(NEW_EDITION, this::newEdition));
      case SCORES -> show(load("scores.fxml", new ScoresController(this, edition)), edition, Place.SCORES,
                          new Creation(NEW_EDITION, this::newEdition));
      case RANKING -> show(load("ranking.fxml", new RankingController(this, edition)), edition, Place.RANKING,
                           new Creation(NEW_EDITION, this::newEdition));
      case DISPLAY -> show(load("display-control.fxml", new DisplayController(this, edition)), edition,
                           Place.DISPLAY, new Creation(NEW_EDITION, this::newEdition));
      case ACCOUNTING -> showAccounting(edition);
      default -> showRegistration(edition);
    }
  }

  /**
   * Shows the accounting of an open edition; Shortcut+N starts a new entry, Shortcut+S saves the entry of the form.
   *
   * @param edition the edition and its identifier.
   */
  private void showAccounting(final OpenEdition edition) {
    final AccountingController accounting = new AccountingController(this, edition);
    show(load("accounting.fxml", accounting), edition, Place.ACCOUNTING,
         new Creation("menu.new.entry", accounting::newEntry), accounting::saveEntry);
  }

  /**
   * Shows the registration of an open edition; Shortcut+N starts a new registration, Shortcut+S registers the entry.
   *
   * @param edition the edition and its identifier.
   */
  private void showRegistration(final OpenEdition edition) {
    final RegistrationController registration = new RegistrationController(this, edition);
    show(load("registration.fxml", registration), edition, Place.REGISTRATION,
         new Creation("menu.new.registration", registration::clear), registration::save);
  }

  /**
   * Shows the directory; Shortcut+N starts a new person, Shortcut+S saves the person of the form, unless the
   * directory could not be read.
   */
  @FXML
  void showDirectory() {
    final DirectoryController controller = new DirectoryController(this);
    show(load("directory.fxml", controller), null, Place.DIRECTORY,
         new Creation("menu.new.person", controller::newPerson), directory == null ? null : controller::save);
  }

  /**
   * Shows the import of persons into the directory.
   */
  void showImport() {
    show(load("import.fxml", new ImportController(this)), null, Place.DIRECTORY,
         new Creation(NEW_EDITION, this::newEdition));
  }

  /**
   * Shows the trash, once read in the background.
   */
  @FXML
  void showTrash() {
    submit(StoreService::listTrash, list -> show(load("trash.fxml", new TrashController(this, list)), null,
                                                 Place.TRASH, new Creation(NEW_EDITION, this::newEdition)));
  }

  /**
   * Shows the control of the external display of the open edition, and opens its window.
   */
  @FXML
  void openDisplay() {
    showPart(Place.DISPLAY);
    display().show(displayState.target(), displayState.fullScreen().get());
  }

  /**
   * Shows or hides the side navigation; the position of its divider is kept while it is hidden.
   *
   * @param shown {@code true} to show it.
   */
  private void showSidebar(final boolean shown) {
    if (shown) {
      split.getItems().addFirst(sidebar);
      split.setDividerPositions(settings.sidebar());
      return;
    }
    settings = settings.withDividers(split.getDividerPositions()[0], settings.detail());
    split.getItems().remove(sidebar);
  }

  // *******************************************************************************************************************
  // Keyboard
  // *******************************************************************************************************************

  /**
   * Creates the new element of the view shown.
   */
  @FXML
  void createNew() {
    creation.action().run();
  }

  /**
   * Saves the form of the view shown; the item is disabled when the view has no form to save.
   */
  @FXML
  void saveView() {
    saving.run();
  }

  /**
   * Moves the focus to the search of the view shown.
   */
  @FXML
  void search() {
    viewArea.lookup("#search").requestFocus();
  }

  /**
   * Moves the focus to the next panel on F6, to the previous one on Shift+F6.
   *
   * @param event the key pressed.
   */
  private void keyPressed(final KeyEvent event) {
    if (event.getCode() != KeyCode.F6) {
      return;
    }
    event.consume();
    final List<Node> panels = new ArrayList<>(root.lookupAll(".panel").stream().filter(Node::isVisible).toList());
    final Node owner = stage.getScene().getFocusOwner();
    int index = 0;
    for (int i = 0; i < panels.size(); i++) {
      if (contains(panels.get(i), owner)) {
        index = i;
      }
    }
    final int step = event.isShiftDown() ? panels.size() - 1 : 1;
    final Node next = panels.get((index + step) % panels.size());
    FOCUSABLE.stream().flatMap(selector -> next.lookupAll(selector).stream()).filter(Node::isFocusTraversable)
             .findFirst().ifPresent(Node::requestFocus);
  }

  /**
   * Tells whether a node is inside another one.
   *
   * @param parent the outer node.
   * @param node the node, or {@code null}.
   *
   * @return {@code true} if the node is the outer node or one of its descendants.
   */
  private static boolean contains(final Node parent, final Node node) {
    for (Node ancestor = node; ancestor != null; ancestor = ancestor.getParent()) {
      if (ancestor == parent) {
        return true;
      }
    }
    return false;
  }

  // *******************************************************************************************************************
  // About
  // *******************************************************************************************************************

  /**
   * Shows the window about the application; opens the page of the donations when the user chooses to make one.
   */
  @FXML
  void showAbout() {
    if (dialogs.panel(APPLICATION_TITLE, load("about.fxml", new AboutController(this)), "about.donate")) {
      browse(AboutController.DONATION);
    }
  }

  /**
   * Returns the version of the running application.
   *
   * @return the version.
   */
  Version version() {
    return context.updater().current();
  }

  /**
   * Opens a page in the browser of the system, only when the user asks for it (the application itself never calls
   * the network for it).
   *
   * @param address the address of the page.
   */
  void browse(final URI address) {
    context.natives().opener().open(host, address.toString());
  }

  // *******************************************************************************************************************
  // Settings
  // *******************************************************************************************************************

  /**
   * Shows the settings: theme, accent, animations and logo of the documents.
   */
  @FXML
  void showSettings() {
    dialogs.panel(context.mac() ? "settings.title.mac" : "settings.title",
                  load("settings.fxml", new SettingsController(this)));
  }

  /**
   * Changes the appearance of the window, then saves the settings.
   *
   * @param mode the theme chosen.
   * @param systemAccent whether the accent colour of the system is used.
   * @param reduced whether the animations are reduced.
   */
  void changeAppearance(final ThemeMode mode, final boolean systemAccent, final boolean reduced) {
    settings = settings.withAppearance(mode, systemAccent, reduced);
    themes.forEach(theme -> theme.configure(mode, systemAccent));
    saveSettings();
  }

  /**
   * Saves the settings in the background.
   */
  private void saveSettings() {
    final Map<String, String> values = settings.values();
    submit(store -> {
      store.saveSettings(values);
      return values;
    }, done -> { });
  }

  /**
   * Reads the logo of the documents in the background.
   *
   * @param done receives the content of the logo, or nothing without logo.
   */
  void readLogo(final Consumer<Optional<byte[]>> done) {
    submit(StoreService::logo, done);
  }

  /**
   * Replaces the logo of the documents by an image chosen by the user (PNG or JPEG of at most 2 MiB).
   *
   * @param done called once the logo is replaced.
   */
  void chooseLogo(final Runnable done) {
    dialogs.openFile("dialog.logo", "filter.images", "*.png;*.jpg;*.jpeg")
           .ifPresent(source -> submit(store -> {
             store.setLogo(source);
             return source;
           }, path -> {
             notify("info.logo");
             done.run();
           }));
  }

  /**
   * Removes the logo of the documents, after confirmation.
   *
   * @param done called once the logo is removed.
   */
  void removeLogo(final Runnable done) {
    if (dialogs.confirm("confirm.logo.remove")) {
      submit(store -> {
        store.removeLogo();
        return Boolean.TRUE;
      }, removed -> {
        notify("info.logo.removed");
        done.run();
      });
    }
  }

  // *******************************************************************************************************************
  // Operations on the editions
  // *******************************************************************************************************************

  /**
   * Moves an edition to the trash, after confirmation, then shows the list.
   *
   * @param header the header of the edition.
   */
  void delete(final EditionHeader header) {
    if (dialogs.confirm("confirm.delete", header.name())) {
      submit(store -> {
        store.delete(header.id());
        return header.id();
      }, id -> {
        notify("info.deleted", header.name());
        showList();
      });
    }
  }

  /**
   * Moves the open edition to the trash, after confirmation.
   */
  @FXML
  void deleteEdition() {
    final OpenEdition open = current.get();
    delete(new EditionHeader(open.id(), open.edition().name(), open.edition().date(), open.edition().mode(),
                             open.edition().games()));
  }

  /**
   * Imports an edition file chosen by the user, then opens it.
   */
  @FXML
  void importEdition() {
    dialogs.openFile("dialog.import", "filter.editions", EDITION_FILES)
           .ifPresent(source -> submit(store -> store.importFrom(source), this::open));
  }

  /**
   * Exports the open edition to a file chosen by the user.
   */
  @FXML
  void exportEdition() {
    final OpenEdition open = current.get();
    dialogs.saveFile("dialog.export", fileName(open.edition().name()) + ".jass", "filter.editions", EDITION_FILES)
           .ifPresent(target -> submit(store -> {
             store.exportTo(open.id(), target);
             return target;
           }, done -> notify("info.exported")));
  }

  /**
   * Writes an archive of the whole data folder to a file chosen by the user.
   */
  @FXML
  void backUp() {
    dialogs.saveFile("dialog.backup", "jass-tournament-" + today() + ".zip", "filter.archives", "*.zip")
           .ifPresent(target -> submit(store -> {
             store.backUpEverything(target);
             return target;
           }, done -> notify("info.backed.up")));
  }

  /**
   * Closes the window, which ends the application once the pending operations are completed.
   */
  @FXML
  void quit() {
    stage.close();
  }

  /**
   * Proposes a file name from the name of an edition: the characters that some systems forbid are replaced.
   *
   * @param name the name of the edition.
   *
   * @return the proposed name, without extension.
   */
  static String fileName(final String name) {
    return FORBIDDEN.matcher(name).replaceAll("_");
  }

}
