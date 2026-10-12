package ch.varani.jass.app;

import ch.varani.jass.update.UpdateError;
import ch.varani.jass.update.UpdateException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios of the automatic update (F17, {@link UpdateController}): offer of a newer release with its notes,
 * postponed or accepted, download with its progress, installation, failure, cancellation, and a check that fails
 * without showing anything; in French, with a replaced update ({@link FakeUpdater}) that never reads the network.
 */
@ExtendWith(ApplicationExtension.class)
final class UpdateTest {

  /** Half of the download. */
  private static final double HALF = 0.5;
  /** The number of bytes of a megabyte. */
  private static final double MEGABYTE = 1_000_000;
  /** The texts of the user interface, in French. */
  private static final Messages MESSAGES = new Messages(Languages.FRENCH);

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** The replaced windows of the system. */
  private final FakeNatives natives = new FakeNatives();
  /** The window. */
  private Stage stage;

  /**
   * Keeps the stage; each test starts the application with its own update.
   *
   * @param primaryStage the stage provided by TestFX.
   */
  @Start
  void start(final Stage primaryStage) {
    stage = primaryStage;
  }

  /**
   * Starts the application with an update.
   *
   * @param robot the TestFX robot.
   * @param updater the update.
   */
  private void start(final FxRobot robot, final FakeUpdater updater) {
    robot.interact(() -> new JassApplication(new AppContext(folder, Languages.FRENCH, natives.natives(),
                                                            UiSupport.CLOCK, Solving::of, false, updater))
        .start(stage));
  }

  /**
   * Returns the box showing the progress of the download.
   *
   * @return the box, once shown.
   *
   * @throws Exception if it is not shown in time.
   */
  private Alert progressBox() throws Exception {
    UiSupport.await(() -> !natives.notices().isEmpty());
    return natives.notices().getFirst();
  }

  /**
   * A newer release is offered with its version, the version of the application and its notes; postponed, nothing
   * is downloaded.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if the offer is not shown in time.
   */
  @Test
  @DisplayName("F17: a newer release is offered with its notes, and can be postponed")
  void postponedTest(final FxRobot robot) throws Exception {
    final FakeUpdater updater = new FakeUpdater(FakeUpdater.release(), null, null);
    start(robot, updater);
    UiSupport.await(() -> !natives.questions().isEmpty());
    final Alert offer = natives.questions().getFirst();
    final List<String> buttons = offer.getButtonTypes().stream().map(ButtonType::getText).toList();
    assertAll(
        () -> assertEquals(MESSAGES.get("update.offer.title", "1.1.0"), offer.getHeaderText()),
        () -> assertTrue(((Label) ((VBox) offer.getDialogPane().getContent()).getChildren().getFirst()).getText()
                             .contains("1.0.0")),
        () -> assertEquals(FakeUpdater.release().notes(),
                           ((TextArea) offer.getDialogPane().lookup("#details")).getText()),
        () -> assertFalse(((TextArea) offer.getDialogPane().lookup("#details")).isEditable()),
        () -> assertEquals(List.of(MESSAGES.get("update.offer.action"), MESSAGES.get("update.offer.decline")),
                           buttons),
        () -> assertTrue(natives.notices().isEmpty()),
        () -> assertTrue(stage.isShowing()));
  }

  /**
   * Accepted, the installer is downloaded, then launched once the saves are done, and the application quits.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if the installer is not launched in time.
   */
  @Test
  @DisplayName("F17: an accepted update is downloaded, its installer launched, and the application quits")
  void installedTest(final FxRobot robot) throws Exception {
    final Path installer = Files.writeString(folder.resolve("jass-tournament-1.1.0-macos-aarch64.dmg"), "fictitious");
    natives.answer(true);
    start(robot, new FakeUpdater(FakeUpdater.release(), installer, null));
    UiSupport.await(() -> !natives.opened().isEmpty());
    assertAll(
        () -> assertEquals(List.of(installer.toUri().toString()), natives.opened()),
        () -> assertEquals(MESSAGES.get("update.download.title", "1.1.0"),
                           natives.notices().getFirst().getHeaderText()),
        () -> assertFalse(stage.isShowing()));
  }

  /**
   * The progress of the download is shown; cancelled, the download stops and nothing else is shown.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if the download does not start or end in time.
   */
  @Test
  @DisplayName("F17: the download shows its progress and can be cancelled")
  void cancelledTest(final FxRobot robot) throws Exception {
    final FakeUpdater updater = new FakeUpdater(FakeUpdater.release(), null, null);
    natives.answer(true);
    start(robot, updater);
    updater.awaitDownload();
    final Alert box = progressBox();
    final ProgressBar bar = (ProgressBar) ((VBox) box.getDialogPane().getContent()).getChildren().getFirst();
    final Label done = (Label) ((VBox) box.getDialogPane().getContent()).getChildren().getLast();
    final String half = MESSAGES.get("update.download.progress", FakeUpdater.SIZE / 2 / MEGABYTE);
    UiSupport.await(() -> half.equals(done.getText()));
    assertEquals(HALF, bar.getProgress());
    robot.interact(() -> box.setResult(box.getButtonTypes().stream()
        .filter(button -> button.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE).findFirst().orElseThrow()));
    updater.awaitEnd();
    WaitForAsyncUtils.waitForFxEvents();
    assertAll(
        () -> assertEquals(1, natives.notices().size()),
        () -> assertTrue(natives.opened().isEmpty()),
        () -> assertTrue(stage.isShowing()));
  }

  /**
   * A download that fails is explained; nothing is installed.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if the error is not shown in time.
   */
  @Test
  @DisplayName("F17: a damaged download is explained, and nothing is installed")
  void failedTest(final FxRobot robot) throws Exception {
    natives.answer(true);
    start(robot, new FakeUpdater(FakeUpdater.release(), null, new UpdateException(UpdateError.DAMAGED)));
    UiSupport.await(() -> natives.notices().size() == 2);
    assertAll(
        () -> assertEquals(MESSAGES.get("error.update.DAMAGED"), natives.texts().getLast()),
        () -> assertTrue(natives.opened().isEmpty()));
  }

  /**
   * Without network, or without a newer release, nothing is shown.
   *
   * @param robot the TestFX robot.
   *
   * @throws Exception if the check does not end in time.
   */
  @Test
  @DisplayName("F17: without network or without a newer release, nothing is shown")
  void silentTest(final FxRobot robot) throws Exception {
    final FakeUpdater offline = new FakeUpdater(null, null, new IOException("offline"));
    start(robot, offline);
    offline.awaitCheck();
    WaitForAsyncUtils.waitForFxEvents();
    start(robot, FakeUpdater.NONE);
    FakeUpdater.NONE.awaitCheck();
    WaitForAsyncUtils.waitForFxEvents();
    assertAll(
        () -> assertTrue(natives.questions().isEmpty()),
        () -> assertTrue(natives.notices().isEmpty()));
  }

}
