package ch.varani.jass.app;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Dialogs}: the file choosers and message boxes, with the windows of the system replaced.
 */
@ExtendWith(ApplicationExtension.class)
final class DialogsTest {

  /** The index of the destructive confirmation among the questions. */
  private static final int DESTRUCTIVE = 3;

  /** The owner window. */
  private Stage stage;

  /**
   * Keeps the stage provided by TestFX as owner window.
   *
   * @param primaryStage the stage.
   */
  @Start
  void start(final Stage primaryStage) {
    stage = primaryStage;
    stage.setScene(new Scene(new StackPane()));
  }

  /**
   * The file choosers are titled, filtered (one extension per pattern), owned by the window, and give the chosen
   * file or nothing.
   */
  @Test
  @DisplayName("The file choosers give the chosen file or nothing")
  void filesTest() {
    final AtomicReference<FileChooser> shown = new AtomicReference<>();
    final File chosen = new File("chosen.jass");
    final Dialogs.Natives natives = new Dialogs.Natives((chooser, owner) -> {
      shown.set(chooser);
      return owner == stage ? chosen : null;
    }, (chooser, owner) -> {
      shown.set(chooser);
      return null;
    }, alert -> Optional.empty(), alert -> { }, (services, uri) -> { });
    final Dialogs dialogs = new Dialogs(() -> stage, new Messages(Languages.FRENCH), natives);
    final Optional<Path> open = dialogs.openFile("dialog.import", "filter.editions", "*.jass");
    final FileChooser openChooser = shown.get();
    dialogs.openFile("dialog.logo", "filter.images", "*.png;*.jpg;*.jpeg");
    final List<String> images = shown.get().getExtensionFilters().getFirst().getExtensions();
    final Optional<Path> save = dialogs.saveFile("dialog.export", "Match.jass", "filter.editions", "*.jass");
    assertAll(
        () -> assertEquals(Optional.of(chosen.toPath()), open),
        () -> assertEquals("Importer une édition", openChooser.getTitle()),
        () -> assertEquals(List.of("*.jass"), openChooser.getExtensionFilters().getFirst().getExtensions()),
        () -> assertEquals(List.of("*.png", "*.jpg", "*.jpeg"), images),
        () -> assertEquals(Optional.empty(), save),
        () -> assertEquals("Match.jass", shown.get().getInitialFileName()));
  }

  /**
   * A confirmation shows its question, its consequence and its action, given by the action only, a destructive one
   * not being the default button; the errors are shown without waiting; a panel waits until it is closed; all are
   * titled with the name of the application, owned by the window and styled as it.
   *
   * @param robot the TestFX robot.
   */
  @Test
  @DisplayName("A confirmation names its action; errors are shown without waiting; a panel waits")
  void messagesTest(final FxRobot robot) {
    final List<Alert> notices = new ArrayList<>();
    final List<Optional<Integer>> answers = new ArrayList<>(List.of(Optional.of(0), Optional.of(1), Optional.empty(),
                                                                    Optional.of(0), Optional.empty()));
    final List<Alert> questions = new ArrayList<>();
    final Dialogs.Natives natives = new Dialogs.Natives((chooser, owner) -> null, (chooser, owner) -> null, alert -> {
      questions.add(alert);
      return answers.removeFirst().map(index -> alert.getButtonTypes().get(index));
    }, notices::add, (services, uri) -> { });
    final Dialogs dialogs = new Dialogs(() -> stage, new Messages(Languages.FRENCH), natives);
    final List<Boolean> confirmed = new ArrayList<>();
    final Label content = new Label("Contenu");
    robot.interact(() -> {
      confirmed.add(dialogs.confirm("confirm.delete", "Match"));
      confirmed.add(dialogs.confirm("confirm.empty.trash"));
      confirmed.add(dialogs.confirm("confirm.empty.trash"));
      confirmed.add(dialogs.confirmDestructive("confirm.empty.trash"));
      dialogs.error(new IOException("disk"));
      dialogs.panel("settings.title", content);
    });
    final Alert first = questions.getFirst();
    final Button action = (Button) first.getDialogPane().lookupButton(first.getButtonTypes().getFirst());
    final Alert destructive = questions.get(DESTRUCTIVE);
    final Button danger = (Button) destructive.getDialogPane().lookupButton(destructive.getButtonTypes().getFirst());
    assertAll(
        () -> assertEquals(List.of(true, false, false, true), confirmed),
        () -> assertEquals("Mettre « Match » à la corbeille ?", first.getHeaderText()),
        () -> assertEquals("L’édition pourra être restaurée depuis la corbeille.", first.getContentText()),
        () -> assertEquals("Mettre à la corbeille", first.getButtonTypes().getFirst().getText()),
        () -> assertEquals(ButtonBar.ButtonData.OK_DONE, first.getButtonTypes().getFirst().getButtonData()),
        () -> assertEquals("Annuler", first.getButtonTypes().getLast().getText()),
        () -> assertEquals(ButtonBar.ButtonData.CANCEL_CLOSE, first.getButtonTypes().getLast().getButtonData()),
        () -> assertTrue(action.isDefaultButton()),
        () -> assertFalse(danger.isDefaultButton()),
        () -> assertTrue(danger.getStyleClass().contains("danger")),
        () -> assertEquals(Alert.AlertType.CONFIRMATION, first.getAlertType()),
        () -> assertEquals("Tournoi de Jass", first.getTitle()),
        () -> assertSame(stage, first.getOwner()),
        () -> assertEquals(stage.getScene().getStylesheets(), first.getDialogPane().getStylesheets()),
        () -> assertEquals(Alert.AlertType.ERROR, notices.getFirst().getAlertType()),
        () -> assertEquals("L’opération n’a pas abouti", notices.getFirst().getHeaderText()),
        () -> assertTrue(notices.getFirst().getContentText().startsWith("Le fichier ne peut pas")),
        () -> assertSame(content, questions.getLast().getDialogPane().getContent()),
        () -> assertEquals("Options", questions.getLast().getHeaderText()),
        () -> assertEquals("Fermer", questions.getLast().getButtonTypes().getFirst().getText()));
  }

  /**
   * The windows of the system are the methods of JavaFX.
   */
  @Test
  @DisplayName("The windows of the system are those of JavaFX")
  void systemTest() {
    final Dialogs.Natives system = Dialogs.Natives.SYSTEM;
    assertAll(
        () -> assertNotNull(system.open()),
        () -> assertNotNull(system.save()),
        () -> assertNotNull(system.question()),
        () -> assertNotNull(system.notice()));
  }

}
