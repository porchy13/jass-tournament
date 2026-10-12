package ch.varani.jass.app;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * The animations of the user interface (DESIGN.md §9): they answer an action of the user and never decorate. The
 * detail panel slides in ({@value #PANEL_MILLIS} ms), a notice fades in ({@value #NOTICE_MILLIS} ms), both with the
 * curve {@code SPLINE(0.2, 0, 0, 1)}. When the user asks to reduce the animations, the change is immediate.
 *
 * <p>Utility class without state, confined to the JavaFX Application Thread.</p>
 */
final class Motion {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The duration of the slide of a panel, in milliseconds. */
  static final double PANEL_MILLIS = 180;
  /** The duration of the fade of a notice, in milliseconds. */
  static final double NOTICE_MILLIS = 120;
  /** The curve of the animations. */
  static final Interpolator CURVE = Interpolator.SPLINE(0.2, 0, 0, 1);

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private Motion() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Fades a node in.
   *
   * @param node the node.
   * @param reduced {@code true} if the animations are reduced: the node is shown at once.
   */
  static void fadeIn(final Node node, final boolean reduced) {
    node.setOpacity(1);
    if (reduced) {
      return;
    }
    final FadeTransition fade = new FadeTransition(Duration.millis(NOTICE_MILLIS), node);
    fade.setFromValue(0);
    fade.setToValue(1);
    fade.setInterpolator(CURVE);
    fade.play();
  }

  /**
   * Slides a panel in from the right.
   *
   * @param panel the panel.
   * @param reduced {@code true} if the animations are reduced: the panel is shown at once.
   */
  static void slideIn(final Region panel, final boolean reduced) {
    panel.setTranslateX(0);
    if (reduced) {
      return;
    }
    final TranslateTransition slide = new TranslateTransition(Duration.millis(PANEL_MILLIS), panel);
    slide.setFromX(panel.prefWidth(-1));
    slide.setToX(0);
    slide.setInterpolator(CURVE);
    slide.play();
  }

}
