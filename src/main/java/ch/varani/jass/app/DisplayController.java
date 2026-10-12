package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.scoring.Ranking;
import ch.varani.jass.scoring.Scoring;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/**
 * The control of the external display (F18), a part of the open edition: the window of the display and its screen,
 * the round in progress with the time of a game and its start signal, and the final ranking revealed line by line.
 *
 * <p><b>Window</b>: opened and closed from the toolbar (or from the menu View), on the screen chosen, in full screen
 * or not; closing it has no effect on the edition. The screens are listed by the system; the first screen other than
 * the main one is proposed, being usually the projector.</p>
 *
 * <p><b>Round in progress</b> (Q21): the round shown (the round in progress by default), the time of a game (a setting
 * of the edition, saved at once), the start signal of the countdown, given by the user round after round, and its
 * cancellation; choosing another round cancels the signal.</p>
 *
 * <p><b>Final ranking</b>: once every game has a result and the ties are broken by drawing lots (rule 15), the
 * ranking is shown on the display and revealed one line at a time, from the last to the first; the lines can be
 * hidden again, and the display can go back to the round.</p>
 *
 * <p>The state of the display ({@link DisplayState}) belongs to the session: it is kept when the user goes to another
 * view.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class DisplayController {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The open edition. */
  private MainController.OpenEdition open;
  /** What the display shows. */
  private final DisplayState state;
  /** The window of the display. */
  private final DisplayWindow window;
  /** The name and date of the edition. */
  @FXML
  private Label title;
  /** The settings of the edition. */
  @FXML
  private Label settings;
  /** The button opening the window. */
  @FXML
  private Button openButton;
  /** The button closing the window. */
  @FXML
  private Button closeButton;
  /** The screen of the window. */
  @FXML
  private ComboBox<Screen> screenChoice;
  /** Whether the window covers its screen. */
  @FXML
  private CheckBox fullScreen;
  /** The message shown before the schedule exists. */
  @FXML
  private VBox empty;
  /** The controls of the round and of the ranking, once the schedule exists. */
  @FXML
  private VBox controls;
  /** The round shown. */
  @FXML
  private ComboBox<Integer> roundChoice;
  /** The time of a game, in minutes. */
  @FXML
  private Spinner<Integer> minutes;
  /** The state of the countdown. */
  @FXML
  private Label roundStatus;
  /** The button giving the start signal. */
  @FXML
  private Button startButton;
  /** The button cancelling the start signal. */
  @FXML
  private Button resetButton;
  /** The button showing the round on the display. */
  @FXML
  private Button roundButton;
  /** The state of the final ranking. */
  @FXML
  private Label rankingStatus;
  /** The button showing the final ranking on the display. */
  @FXML
  private Button rankingButton;
  /** The button revealing the next line of the ranking. */
  @FXML
  private Button revealButton;
  /** The button hiding the lines revealed. */
  @FXML
  private Button hideButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the control of the display of an edition.
   *
   * @param main the main window.
   * @param open the open edition and its identifier.
   */
  DisplayController(final MainController main, final MainController.OpenEdition open) {
    this.main = main;
    this.open = open;
    this.state = main.displayState();
    this.window = main.display();
  }

  /**
   * Fills the controls from the state of the display; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    final Edition edition = open.edition();
    title.setText(main.title(edition));
    settings.setText(main.describe(edition));
    openButton.visibleProperty().bind(window.showing().not());
    openButton.managedProperty().bind(openButton.visibleProperty());
    closeButton.visibleProperty().bind(window.showing());
    closeButton.managedProperty().bind(closeButton.visibleProperty());
    final List<Screen> screens = Screen.getScreens();
    screenChoice.setConverter(Converters.showing(screen -> messages.get(
        "display.screen", screens.indexOf(screen) + 1, (int) screen.getBounds().getWidth(),
        (int) screen.getBounds().getHeight())));
    screenChoice.getItems().setAll(screens);
    screenChoice.getSelectionModel().select(state.target());
    screenChoice.setOnAction(event -> placeWindow());
    fullScreen.setSelected(state.fullScreen().get());
    empty.setVisible(!edition.isScheduled());
    empty.setManaged(!edition.isScheduled());
    controls.setVisible(edition.isScheduled());
    controls.setManaged(edition.isScheduled());
    roundChoice.setConverter(Converters.showing(round -> messages.get("schedule.round", round)));
    roundChoice.getItems().setAll(DisplayModel.rounds(edition));
    roundChoice.getSelectionModel().select(Integer.valueOf(state.round().get()));
    roundChoice.setOnAction(event -> chooseRound());
    minutes.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Edition.MAX_GAME_MINUTES,
                                                                               edition.gameMinutes()));
    minutes.valueProperty().addListener((property, before, after) -> changeMinutes(after));
    refresh();
  }

  // *******************************************************************************************************************
  // Display
  // *******************************************************************************************************************

  /**
   * Shows the state of the countdown and of the final ranking, and enables the actions that apply.
   */
  private void refresh() {
    final Messages messages = main.messages();
    final boolean rankingShown = state.mode().get() == DisplayState.Mode.RANKING;
    roundStatus.setText(state.start().get() == null ? messages.get("display.not.started")
        : messages.get("display.started", DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
                                                           .withLocale(messages.locale())
                                                           .format(LocalTime.ofInstant(state.start().get(),
                                                                                       main.zone()))));
    resetButton.setDisable(state.start().get() == null);
    roundButton.setDisable(!rankingShown);
    final Ranking ranking = new Scoring(open.edition()).ranking();
    final boolean ready = DisplayModel.isFinal(ranking);
    final int size = ranking.entries().size();
    final int shown = Math.min(state.revealed().get(), size);
    rankingStatus.setText(ready ? messages.get("display.revealed", shown, size)
                                : messages.get("display.ranking.unavailable"));
    rankingButton.setDisable(!ready || rankingShown);
    revealButton.setDisable(!ready || shown == size);
    revealButton.setText(shown == size ? messages.get("display.reveal.done")
                                       : messages.get("display.reveal", size - shown));
    hideButton.setDisable(!ready || shown == 0);
  }

  // *******************************************************************************************************************
  // Window
  // *******************************************************************************************************************

  /**
   * Opens the window of the display on the screen chosen.
   */
  @FXML
  void openWindow() {
    window.show(screenChoice.getValue(), fullScreen.isSelected());
  }

  /**
   * Closes the window of the display; its state is kept.
   */
  @FXML
  void closeWindow() {
    window.close();
  }

  /**
   * Remembers the screen and the full screen chosen, and moves the window there when it is open.
   */
  @FXML
  void placeWindow() {
    state.screen().set(screenChoice.getValue());
    state.fullScreen().set(fullScreen.isSelected());
    if (window.showing().get()) {
      window.place(screenChoice.getValue(), fullScreen.isSelected());
    }
  }

  /**
   * Shows the registration, which closes by computing the schedule.
   */
  @FXML
  void showRegistration() {
    main.showPart(MainController.Place.REGISTRATION);
  }

  // *******************************************************************************************************************
  // Round
  // *******************************************************************************************************************

  /**
   * Shows another round; its countdown waits for the start signal.
   */
  private void chooseRound() {
    state.round().set(roundChoice.getValue());
    state.start().set(null);
    state.mode().set(DisplayState.Mode.ROUND);
    refresh();
  }

  /**
   * Saves the time of a game, a setting of the edition.
   *
   * @param value the time, in minutes.
   */
  private void changeMinutes(final int value) {
    open = new MainController.OpenEdition(open.id(), open.edition().withGameMinutes(value));
    main.update(open);
  }

  /**
   * Gives the start signal of the countdown of the round, now, and shows the round.
   */
  @FXML
  void start() {
    state.start().set(main.now());
    state.mode().set(DisplayState.Mode.ROUND);
    refresh();
  }

  /**
   * Cancels the start signal: the countdown shows the whole time of a game again.
   */
  @FXML
  void reset() {
    state.start().set(null);
    refresh();
  }

  /**
   * Shows the round on the display instead of the ranking.
   */
  @FXML
  void showRound() {
    state.mode().set(DisplayState.Mode.ROUND);
    refresh();
  }

  // *******************************************************************************************************************
  // Final ranking
  // *******************************************************************************************************************

  /**
   * Shows the final ranking on the display, with the lines revealed so far.
   */
  @FXML
  void showRanking() {
    state.mode().set(DisplayState.Mode.RANKING);
    refresh();
  }

  /**
   * Reveals the next line of the final ranking, from the last one, and shows the ranking.
   */
  @FXML
  void reveal() {
    state.revealed().set(state.revealed().get() + 1);
    state.mode().set(DisplayState.Mode.RANKING);
    refresh();
  }

  /**
   * Hides the lines revealed, to reveal them again.
   */
  @FXML
  void hideAll() {
    state.revealed().set(0);
    refresh();
  }

}
