package ch.varani.jass.app;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Motion} (DESIGN.md §9): the notices fade in and the panels slide in, at once when the animations
 * are reduced.
 */
@ExtendWith(ApplicationExtension.class)
final class MotionTest {

  /** The width of the panel, in pixels. */
  private static final double WIDTH = 100;

  /**
   * An animated node starts hidden or outside, a reduced one is shown at once.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("The animations start from hidden, the reduced ones are immediate")
  void animationsTest(final FxRobot robot) {
    final List<Double> values = new ArrayList<>();
    robot.interact(() -> {
      final Label notice = new Label("Notice");
      final StackPane panel = new StackPane();
      panel.setPrefWidth(WIDTH);
      Motion.fadeIn(notice, true);
      values.add(notice.getOpacity());
      Motion.slideIn(panel, true);
      values.add(panel.getTranslateX());
      Motion.fadeIn(notice, false);
      values.add(notice.getOpacity());
      Motion.slideIn(panel, false);
      values.add(panel.getTranslateX());
    });
    assertAll(
        () -> assertEquals(List.of(1.0, 0.0), values.subList(0, 2)),
        () -> assertTrue(values.get(2) <= 1.0),
        () -> assertTrue(values.getLast() >= 0.0));
  }

}
