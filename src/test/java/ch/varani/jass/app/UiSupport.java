package ch.varani.jass.app;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputControl;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Helpers of the scenarios of the user interface: waiting for the background operations, pressing buttons, firing
 * menu items, typing and reading texts, by the identifiers of the controls.
 */
final class UiSupport {

  /** The longest wait for the background operations, in seconds. */
  static final long WAIT = 20;
  /** The date of today in the tests. */
  static final LocalDate TODAY = LocalDate.of(2026, Month.NOVEMBER, 20);
  /** The clock of the tests, fixed at {@link #TODAY}. */
  static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                                         ZoneId.systemDefault());

  /**
   * Prevents the instantiation of this utility class.
   */
  private UiSupport() {
  }

  /**
   * Waits until a condition holds, then until the events are processed.
   *
   * @param condition the condition.
   *
   * @throws Exception if the condition does not hold in time.
   */
  static void await(final Callable<Boolean> condition) throws Exception {
    WaitForAsyncUtils.waitFor(WAIT, TimeUnit.SECONDS, condition);
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Tells whether a control is shown.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the control.
   *
   * @return {@code true} if a control has this identifier.
   */
  static boolean shown(final FxRobot robot, final String id) {
    return robot.lookup("#" + id).tryQuery().isPresent();
  }

  /**
   * Fires a button.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the button.
   */
  static void press(final FxRobot robot, final String id) {
    final Button button = robot.lookup("#" + id).queryButton();
    robot.interact(button::fire);
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Tells whether a control is disabled.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the control.
   *
   * @return {@code true} if the control is disabled.
   */
  static boolean disabled(final FxRobot robot, final String id) {
    final Node node = robot.lookup("#" + id).query();
    return node.isDisabled();
  }

  /**
   * Fires an item of the menu.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the item.
   */
  static void menu(final FxRobot robot, final String id) {
    final MenuItem item = menuItem(robot, id);
    robot.interact(item::fire);
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Returns an item of the menu.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the item.
   *
   * @return the item.
   */
  static MenuItem menuItem(final FxRobot robot, final String id) {
    return robot.lookup(".menu-bar").queryAs(MenuBar.class).getMenus().stream()
                .flatMap(menu -> menu.getItems().stream())
                .filter(candidate -> id.equals(candidate.getId())).findFirst().orElseThrow();
  }

  /**
   * Types a text into a field.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the field.
   * @param value the text.
   */
  static void type(final FxRobot robot, final String id, final String value) {
    final TextInputControl field = robot.lookup("#" + id).queryAs(TextInputControl.class);
    robot.interact(() -> field.setText(value));
    WaitForAsyncUtils.waitForFxEvents();
  }

  /**
   * Returns the text of a label or of a field.
   *
   * @param robot the TestFX robot.
   * @param id the identifier of the control.
   *
   * @return the text.
   */
  static String text(final FxRobot robot, final String id) {
    final Node node = robot.lookup("#" + id).query();
    return node instanceof final Label label ? label.getText() : ((TextInputControl) node).getText();
  }

}
