package ch.varani.jass.app;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Views} and {@link Icon} (DESIGN.md §7, §8, §10): empty states, keys of the tables, shortcuts and
 * icons.
 */
@ExtendWith(ApplicationExtension.class)
final class ViewsTest {

  /**
   * An empty state says what to do, with its button when there is one; a shortcut is written as the system does.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("An empty state says what to do, with its button")
  void emptyStateTest(final FxRobot robot) {
    final List<Node> states = new ArrayList<>();
    final Button action = new Button("Créer");
    robot.interact(() -> {
      states.add(Views.emptyState("Rien.", null));
      states.add(Views.emptyState("Rien.", action));
    });
    final String shortcut = Views.shortcut("Shortcut+N");
    assertAll(
        () -> assertEquals(1, ((VBox) states.getFirst()).getChildren().size()),
        () -> assertEquals("Rien.", ((Label) ((VBox) states.getFirst()).getChildren().getFirst()).getText()),
        () -> assertEquals(action, ((VBox) states.getLast()).getChildren().getLast()),
        () -> assertTrue(states.getFirst().getStyleClass().contains("empty-state")),
        () -> assertTrue(shortcut.endsWith("N"), shortcut));
  }

  /**
   * Enter opens and Delete or Backspace deletes the selected row; nothing happens without selection, without action
   * or for another key.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("Enter opens and Delete deletes the selected row")
  void keysTest(final FxRobot robot) {
    final List<String> done = new ArrayList<>();
    final List<Boolean> consumed = new ArrayList<>();
    robot.interact(() -> {
      final TableView<String> table = new TableView<>();
      table.getItems().setAll("A", "B");
      final TableView<String> bare = new TableView<>();
      bare.getItems().setAll("A");
      Views.keys(table, () -> done.add("open"), () -> done.add("delete"));
      Views.keys(bare, null, null);
      new StackPane(table, bare);
      consumed.add(press(table, KeyCode.ENTER));
      table.getSelectionModel().select(0);
      for (final KeyCode code : List.of(KeyCode.ENTER, KeyCode.DELETE, KeyCode.BACK_SPACE, KeyCode.A)) {
        consumed.add(press(table, code));
      }
      bare.getSelectionModel().select(0);
      consumed.add(press(bare, KeyCode.ENTER));
      consumed.add(press(bare, KeyCode.DELETE));
    });
    assertAll(
        () -> assertEquals(List.of("open", "delete", "delete"), done),
        () -> assertEquals(List.of(false, true, true, true, false, false, false), consumed));
  }

  /**
   * Presses a key on a table.
   *
   * @param table the table.
   * @param code the key.
   *
   * @return whether the key was consumed.
   */
  private static boolean press(final TableView<String> table, final KeyCode code) {
    final KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    table.getOnKeyPressed().handle(event);
    return event.isConsumed();
  }

  /**
   * Every icon is a line drawing in a square of 16 pixels; a decorated button shows it with a tooltip and an
   * accessible text.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("The icons are line drawings of 16 pixels, never without a text")
  void iconsTest(final FxRobot robot) {
    final List<Node> icons = new ArrayList<>();
    final Button button = new Button();
    robot.interact(() -> {
      for (final Icon icon : Icon.values()) {
        icons.add(icon.graphic());
      }
      Icon.TRASH.decorate(button, "Supprimer");
    });
    assertAll(
        () -> assertTrue(icons.stream().allMatch(icon -> ((StackPane) icon).getPrefWidth() == Icon.SIZE
            && ((SVGPath) ((StackPane) icon).getChildren().getFirst()).getStyleClass().contains("icon")
            && !((SVGPath) ((StackPane) icon).getChildren().getFirst()).getContent().isEmpty())),
        () -> assertEquals("Supprimer", button.getTooltip().getText()),
        () -> assertEquals("Supprimer", button.getAccessibleText()),
        () -> assertNotNull(button.getGraphic()));
  }

}
