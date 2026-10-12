package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.TournamentMode;
import ch.varani.jass.scoring.Ranking;
import ch.varani.jass.scoring.RankingEntry;
import ch.varani.jass.scoring.Scoring;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The external display (F18): a window of its own, independent of the main window, to be moved to a projector or a
 * television and shown in full screen; it shows what the {@link DisplayState} says, for both modes.
 *
 * <p><b>Round in progress</b>: the logo of the documents (when one is chosen), the edition, the round, the countdown
 * of the game (the whole time of a game until the start signal, then the time remaining, "time is over" at zero) and
 * the place and table of every participant in the round, by number, those sitting out being marked.</p>
 *
 * <p><b>Final ranking</b>: the lines revealed from the main window, from the last to the first, shown the best first,
 * each with the rank, the participant, its points in every round and its total. While they are being revealed, the
 * first page is held, so that the line revealed last always stays on the screen; its pages alternate once the user
 * starts their rotation.</p>
 *
 * <p><b>Pages</b>: the number of lines that fit is computed from the height of the window ({@value #LINE_HEIGHT}
 * pixels a line); when more lines are needed, the pages alternate ({@link DisplayModel#page}) and a bar next to the
 * number of the page empties until the next page ({@link DisplayModel#left}), computed from the same clock.</p>
 *
 * <p><b>Refresh</b>: every {@value #TICK_MILLIS} ms, the window is refreshed whole (countdown, pages, bar) as soon as
 * the second of the clock changes, so that the countdown and the pages change with the clock, on the second; the
 * pages change on a whole second (from the epoch, or from the start of the rotation, a whole second too), so that the
 * page and the bar always change together. Between two seconds, the bar alone moves, for a continuous movement,
 * unless the animations are reduced (it then moves by steps, once a second). The window is refreshed too on every
 * change of the state or of the edition.</p>
 *
 * <p><b>Privacy</b>: only the names, places and points already printed on the documents are shown, never a contact
 * detail.</p>
 *
 * <p>In full screen, no hint is shown over the display (the audience would see it): the full screen is chosen from
 * the main window, and Escape still leaves it.</p>
 *
 * <p>Closing the window has no effect on the edition; the window can be opened again with the same state.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class DisplayWindow {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The height of a line of the display, in pixels (components.css, {@code .display-grid .label}). */
  static final double LINE_HEIGHT = 56;
  /** The width of the window when it is not in full screen, in pixels. */
  static final double WIDTH = 1280;
  /** The height of the window when it is not in full screen, in pixels. */
  static final double HEIGHT = 720;
  /** The distance of the window from the corner of its screen when it is not in full screen, in pixels. */
  private static final double MARGIN = 32;
  /** The interval of the checks of the clock, and of the moves of the bar of the page, in milliseconds. */
  static final double TICK_MILLIS = 40;
  /** The style class of the countdown once the time is over. */
  static final String OVER = "over";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** What the display shows. */
  private final DisplayState state;
  /** The clock of the countdown and of the pages. */
  private final Clock clock;
  /** The window. */
  private final Stage stage = new Stage();
  /** Checks the clock: refreshes the window at each second, moves the bar of the page between them. */
  private final Timeline ticks = new Timeline(new KeyFrame(Duration.millis(TICK_MILLIS), event -> tick()));
  /** The second of the clock shown, since the epoch. */
  private long second;
  /** The origin of the pages that alternate, or {@code null} when the bar of the page is hidden. */
  private Instant origin;
  /** The logo of the documents. */
  @FXML
  private ImageView logo;
  /** The name and date of the edition. */
  @FXML
  private Label edition;
  /** The round, or the title of the final ranking. */
  @FXML
  private Label heading;
  /** The countdown and its state. */
  @FXML
  private VBox clockBox;
  /** The countdown. */
  @FXML
  private Label timer;
  /** The state of the countdown. */
  @FXML
  private Label status;
  /** The area of the lines, whose height gives the number of lines that fit. */
  @FXML
  private StackPane area;
  /** The lines of the page shown. */
  @FXML
  private GridPane grid;
  /** The number of the page shown. */
  @FXML
  private Label pageLabel;
  /** The time left before the next page. */
  @FXML
  private ProgressBar pageBar;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the window of the external display, hidden.
   *
   * @param main the main window: texts, logo, theme.
   * @param state what the display shows.
   * @param clock the clock of the countdown.
   */
  DisplayWindow(final MainController main, final DisplayState state, final Clock clock) {
    this.main = main;
    this.state = state;
    this.clock = clock;
    final Scene scene = new Scene(main.load("display.fxml", this), WIDTH, HEIGHT);
    stage.setScene(scene);
    stage.getIcons().setAll(MainController.icons());
    main.style(scene);
    stage.setFullScreenExitHint("");
    ticks.setCycleCount(Animation.INDEFINITE);
    stage.setOnShown(event -> ticks.play());
    stage.setOnHidden(event -> ticks.stop());
    state.edition().addListener((property, before, after) -> refresh());
    state.mode().addListener((property, before, after) -> refresh());
    state.round().addListener((property, before, after) -> refresh());
    state.start().addListener((property, before, after) -> refresh());
    state.revealed().addListener((property, before, after) -> refresh());
    state.rotation().addListener((property, before, after) -> refresh());
    area.heightProperty().addListener((property, before, after) -> refresh());
  }

  /**
   * Prepares the parts of the window; called by the FXML loader.
   */
  @FXML
  void initialize() {
    logo.visibleProperty().bind(logo.imageProperty().isNotNull());
    logo.managedProperty().bind(logo.visibleProperty());
    pageBar.managedProperty().bind(pageBar.visibleProperty());
  }

  // *******************************************************************************************************************
  // Window
  // *******************************************************************************************************************

  /**
   * Shows the window on a screen, in full screen or not, with the logo of the documents.
   *
   * @param screen the screen.
   * @param fullScreen {@code true} to cover the whole screen.
   */
  void show(final Screen screen, final boolean fullScreen) {
    place(screen, fullScreen);
    main.readLogo(image -> logo.setImage(image.map(bytes -> new Image(new ByteArrayInputStream(bytes)))
                                             .orElse(null)));
    refresh();
    stage.show();
    stage.toFront();
  }

  /**
   * Moves the window to a screen, in full screen or not.
   *
   * @param screen the screen.
   * @param fullScreen {@code true} to cover the whole screen.
   */
  void place(final Screen screen, final boolean fullScreen) {
    stage.setFullScreen(false);
    final Rectangle2D bounds = screen.getVisualBounds();
    stage.setX(bounds.getMinX() + MARGIN);
    stage.setY(bounds.getMinY() + MARGIN);
    stage.setFullScreen(fullScreen);
  }

  /**
   * Closes the window; the state is kept.
   */
  void close() {
    stage.close();
  }

  /**
   * Tells whether the window is shown.
   *
   * @return the property, {@code true} while the window is shown.
   */
  ReadOnlyBooleanProperty showing() {
    return stage.showingProperty();
  }

  // *******************************************************************************************************************
  // Content
  // *******************************************************************************************************************

  /**
   * Shows the state again: the round with its countdown and places, or the final ranking. The window is created once
   * an edition is open, so the state always has an edition.
   */
  void refresh() {
    final Instant now = clock.instant();
    second = now.getEpochSecond();
    final Edition shown = state.edition().get().edition();
    final Messages messages = main.messages();
    stage.setTitle(messages.get("display.window.title", shown.name()));
    edition.setText(main.title(shown));
    grid.getChildren().clear();
    grid.getColumnConstraints().clear();
    pageLabel.setText("");
    pageBar.setVisible(false);
    origin = null;
    final boolean round = shown.isScheduled() && state.mode().get() == DisplayState.Mode.ROUND;
    clockBox.setVisible(round);
    clockBox.setManaged(round);
    if (!shown.isScheduled()) {
      heading.setText(messages.get("display.unscheduled"));
      return;
    }
    if (round) {
      showRound(shown, now);
    } else {
      showRanking(shown, now);
    }
  }

  /**
   * Shows the round: its countdown and the page of the places of the moment.
   *
   * @param shown the edition.
   * @param now the current instant.
   */
  private void showRound(final Edition shown, final Instant now) {
    final Messages messages = main.messages();
    final java.time.Duration left = DisplayModel.remaining(state.start().get(), shown.gameMinutes(), now);
    heading.setText(messages.get("schedule.round", state.round().get()));
    timer.setText(DisplayModel.clock(left));
    final boolean over = state.start().get() != null && left.isZero();
    Views.style(timer, OVER, over);
    status.setText(messages.get(statusKey(over)));
    final DisplayModel.Page<DisplayModel.PlaceLine> page =
        DisplayModel.page(DisplayModel.places(shown, state.round().get()), fitting(0), Instant.EPOCH, now);
    grid.getColumnConstraints().setAll(fixed(), growing(), fixed());
    for (final DisplayModel.PlaceLine line : page.lines()) {
      final String place = line.place() == 0 ? messages.get("display.sitting.out")
                                             : messages.get("display.place", line.place(), line.table());
      grid.addRow(grid.getRowCount(), cell(participant(shown, line.participant())),
                  cell(MainController.names(shown, line.participant())), cell(place));
    }
    showPage(page, Instant.EPOCH, now);
  }

  /**
   * Chooses the text of the state of the round: waiting for its start, running, or over.
   *
   * @param over {@code true} if the countdown of the round reached zero.
   *
   * @return the key of the text.
   */
  private String statusKey(final boolean over) {
    if (state.start().get() == null) {
      return "display.waiting";
    }
    return over ? "display.over" : "display.running";
  }

  /**
   * Shows the lines of the final ranking revealed so far, the best first, with a head of columns.
   *
   * @param shown the edition.
   * @param now the current instant.
   */
  private void showRanking(final Edition shown, final Instant now) {
    final Messages messages = main.messages();
    final Scoring scoring = new Scoring(shown);
    final Ranking ranking = scoring.ranking();
    final List<Integer> rounds = DisplayModel.rounds(shown);
    heading.setText(messages.get("display.ranking"));
    final List<ColumnConstraints> columns = new ArrayList<>(List.of(numeric(), fixed(), growing()));
    rounds.forEach(number -> columns.add(numeric()));
    columns.add(numeric());
    grid.getColumnConstraints().setAll(columns);
    final List<Label> head = new ArrayList<>(List.of(cell(messages.get("ranking.column.rank")), cell(""),
                                                     cell("")));
    rounds.forEach(number -> head.add(cell(messages.get("ranking.column.game", number))));
    head.add(cell(messages.get("ranking.column.total")));
    head.forEach(label -> label.getStyleClass().add("display-column"));
    grid.addRow(0, head.toArray(Label[]::new));
    final List<RankingEntry> revealed = DisplayModel.revealed(ranking, state.revealed().get());
    final Instant rotation = state.rotation().get();
    final DisplayModel.Page<RankingEntry> page = rotation == null ? DisplayModel.firstPage(revealed, fitting(1))
                                                                  : DisplayModel.page(revealed, fitting(1), rotation,
                                                                                      now);
    for (final RankingEntry entry : page.lines()) {
      final List<Label> row = new ArrayList<>(List.of(cell(String.valueOf(entry.rank())),
                                                      cell(participant(shown, entry.participant())),
                                                      cell(MainController.names(shown, entry.participant()))));
      rounds.forEach(number -> row.add(cell(scoring.points(entry.participant(), number)
                                                   .map(messages::number).orElse(""))));
      row.add(cell(messages.number(entry.points())));
      grid.addRow(grid.getRowCount(), row.toArray(Label[]::new));
    }
    showPage(page, rotation, now);
  }

  /**
   * Shows the number of the page when there are several, and the time left before the next page while they
   * alternate.
   *
   * @param page the page shown.
   * @param from the origin of the pages, used only when they alternate.
   * @param now the current instant.
   */
  private void showPage(final DisplayModel.Page<?> page, final Instant from, final Instant now) {
    pageLabel.setText(page.count() > 1 ? main.messages().get("display.page", page.number(), page.count()) : "");
    if (page.turning()) {
      origin = from;
      pageBar.setVisible(true);
      pageBar.setProgress(DisplayModel.left(from, now));
    }
  }

  /**
   * Checks the clock: refreshes the window when its second changed, else moves the bar of the page, unless it is
   * hidden or the animations are reduced.
   */
  private void tick() {
    final Instant now = clock.instant();
    if (now.getEpochSecond() != second) {
      refresh();
      return;
    }
    if (origin != null && !main.reducedMotion()) {
      pageBar.setProgress(DisplayModel.left(origin, now));
    }
  }

  /**
   * Returns the number of lines that fit in the area.
   *
   * @param heads the number of lines taken by the head of the columns.
   *
   * @return the number of lines, at least 1.
   */
  private int fitting(final int heads) {
    return Math.max(1, (int) (area.getHeight() / LINE_HEIGHT) - heads);
  }

  /**
   * Names a participant by its number: the team, or the player.
   *
   * @param shown the edition.
   * @param participant the number of the participant.
   *
   * @return the label of the participant, e.g. "Team 3".
   */
  private String participant(final Edition shown, final int participant) {
    return main.messages().get(shown.mode() == TournamentMode.TEAM ? "display.team" : "display.player", participant);
  }

  /**
   * Creates a cell of the lines.
   *
   * @param text the text.
   *
   * @return the label.
   */
  private static Label cell(final String text) {
    return new Label(text);
  }

  /**
   * Creates a column that takes the width of its content.
   *
   * @return the constraints.
   */
  private static ColumnConstraints fixed() {
    final ColumnConstraints column = new ColumnConstraints();
    column.setMinWidth(Region.USE_PREF_SIZE);
    return column;
  }

  /**
   * Creates a column of numbers, aligned to the right, that takes the width of its content.
   *
   * @return the constraints.
   */
  private static ColumnConstraints numeric() {
    final ColumnConstraints column = fixed();
    column.setHalignment(HPos.RIGHT);
    return column;
  }

  /**
   * Creates a column that takes the width left, for the names.
   *
   * @return the constraints.
   */
  private static ColumnConstraints growing() {
    final ColumnConstraints column = new ColumnConstraints();
    column.setHgrow(Priority.ALWAYS);
    return column;
  }

}
