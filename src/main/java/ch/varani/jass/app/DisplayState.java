package ch.varani.jass.app;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Screen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What the external display shows (F18), set from the main window and followed by the display window: the edition,
 * the mode (round in progress or final ranking), the round, the start signal of its countdown, the number of lines
 * of the final ranking revealed, the start of the rotation of its pages, and the screen of the window.
 *
 * <p>The state belongs to the session of the application, not to the edition: closing and reopening the display
 * keeps it, and it is not saved. When another edition is opened, the state starts again for it: the round in
 * progress ({@link DisplayModel#currentRound}), no start signal, nothing revealed, no rotation of the pages; a change
 * of the same edition (a score entered) keeps the state.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class DisplayState {

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * What the display shows.
   */
  enum Mode {

    /** The round in progress: its countdown and the places of the participants. */
    ROUND,
    /** The final ranking, revealed from the last line to the first. */
    RANKING

  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The edition shown, or {@code null} before an edition is opened. */
  private final ObjectProperty<MainController.OpenEdition> edition = new SimpleObjectProperty<>();
  /** What the display shows. */
  private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.ROUND);
  /** The number of the round shown. */
  private final IntegerProperty round = new SimpleIntegerProperty(1);
  /** The start signal of the countdown of the round, or {@code null} before it. */
  private final ObjectProperty<Instant> start = new SimpleObjectProperty<>();
  /** The number of lines of the final ranking revealed. */
  private final IntegerProperty revealed = new SimpleIntegerProperty();
  /** The start of the rotation of the pages of the final ranking, or {@code null} while they are held. */
  private final ObjectProperty<Instant> rotation = new SimpleObjectProperty<>();
  /** The screen of the window, or {@code null} for the default one. */
  private final ObjectProperty<Screen> screen = new SimpleObjectProperty<>();
  /** Whether the window covers its whole screen. */
  private final BooleanProperty fullScreen = new SimpleBooleanProperty();

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the state of a display that shows nothing yet: no edition, round 1 without start signal, nothing
   * revealed, the default screen in a window.
   */
  DisplayState() {
    super();
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Follows the edition open in the main window: a change of the same edition keeps the state, another edition
   * starts it again.
   *
   * @param open the open edition and its identifier.
   */
  void follow(final MainController.OpenEdition open) {
    final MainController.OpenEdition before = edition.get();
    if (before == null || !before.id().equals(open.id())) {
      mode.set(Mode.ROUND);
      round.set(DisplayModel.currentRound(open.edition()));
      start.set(null);
      revealed.set(0);
      rotation.set(null);
    }
    edition.set(open);
  }

  /**
   * Returns the edition shown.
   *
   * @return the property of the edition, {@code null} before an edition is opened.
   */
  ObjectProperty<MainController.OpenEdition> edition() {
    return edition;
  }

  /**
   * Returns what the display shows.
   *
   * @return the property of the mode.
   */
  ObjectProperty<Mode> mode() {
    return mode;
  }

  /**
   * Returns the round shown.
   *
   * @return the property of the number of the round.
   */
  IntegerProperty round() {
    return round;
  }

  /**
   * Returns the start signal of the countdown.
   *
   * @return the property of the instant of the signal, {@code null} before it.
   */
  ObjectProperty<Instant> start() {
    return start;
  }

  /**
   * Returns the number of lines of the final ranking revealed.
   *
   * @return the property of the number.
   */
  IntegerProperty revealed() {
    return revealed;
  }

  /**
   * Returns the start of the rotation of the pages of the final ranking: until the user starts it, once every line is
   * revealed, the first page is held.
   *
   * @return the property of the instant of the start, {@code null} while the pages are held.
   */
  ObjectProperty<Instant> rotation() {
    return rotation;
  }

  /**
   * Returns the screen of the window.
   *
   * @return the property of the screen, {@code null} for the default one.
   */
  ObjectProperty<Screen> screen() {
    return screen;
  }

  /**
   * Returns the screen where the window goes: the one chosen while it is connected, else the first screen other than
   * the main one (usually the projector), else the main screen.
   *
   * @return the screen.
   */
  Screen target() {
    final Screen chosen = screen.get();
    if (Screen.getScreens().contains(chosen)) {
      return chosen;
    }
    final List<Screen> screens = new ArrayList<>(Screen.getScreens());
    screens.remove(Screen.getPrimary());
    screens.add(Screen.getPrimary());
    return screens.getFirst();
  }

  /**
   * Returns whether the window covers its whole screen.
   *
   * @return the property, {@code true} in full screen.
   */
  BooleanProperty fullScreen() {
    return fullScreen;
  }

}
