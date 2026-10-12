package ch.varani.jass.app;

import ch.varani.jass.update.Release;
import ch.varani.jass.update.Updater;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The automatic update of the application (F17), as seen by the user.
 *
 * <ol>
 *   <li>At start-up, a background task asks the {@link Updater} whether a newer release is published; the start is
 *       never blocked, and without network or on any error nothing is shown (the kind of the error is logged).</li>
 *   <li>If one is, the user is offered to install it, with its notes; the offer can be postponed until the next
 *       start.</li>
 *   <li>Once accepted, its installer is downloaded by a background task, whose progress is shown in a box with a
 *       Cancel button; the download is checked against its published digest, and a failure is shown.</li>
 *   <li>The installer is then launched once the pending saves are done, and the application quits, so that the
 *       installer can replace it ({@link MainController#install(Path)}, Q27).</li>
 * </ol>
 *
 * <p>Confined to the JavaFX Application Thread, except the tasks.</p>
 */
final class UpdateController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The logger of the update; it logs the kinds of the errors only. */
  private static final Logger LOGGER = System.getLogger(UpdateController.class.getName());
  /** The number of bytes of a megabyte, as the systems show the sizes of the files. */
  private static final double MEGABYTE = 1_000_000;

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The automatic update. */
  private final Updater updater;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the update of a window.
   *
   * @param main the main window.
   * @param updater the automatic update.
   */
  UpdateController(final MainController main, final Updater updater) {
    this.main = main;
    this.updater = updater;
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Looks for a newer release in the background, then offers it to the user; nothing is shown if there is none or if
   * it cannot be known.
   */
  void check() {
    final Task<Optional<Release>> task = new Task<>() {

      /**
       * Asks for the latest release, on the background thread.
       *
       * @return the newer release, or nothing.
       *
       * @throws Exception if the releases cannot be read.
       */
      @Override
      protected Optional<Release> call() throws Exception {
        return updater.check();
      }

    };
    task.setOnSucceeded(event -> task.getValue().ifPresent(this::offer));
    task.setOnFailed(event -> LOGGER.log(Level.INFO, "No update found: {0}",
                                         task.getException().getClass().getSimpleName()));
    Thread.ofPlatform().daemon().name("jass-update").start(task);
  }

  /**
   * Offers a release to the user, with its notes, and downloads it if the user accepts.
   *
   * @param release the release.
   */
  private void offer(final Release release) {
    if (main.dialogs().offer("update.offer", release.notes(), release.version().toString(),
                             updater.current().toString())) {
      download(release);
    }
  }

  /**
   * Downloads the installer of a release in the background, with its progress and a Cancel button, then installs it;
   * a failure is shown, a cancellation is not.
   *
   * @param release the release.
   */
  private void download(final Release release) {
    final Messages messages = main.messages();
    final Task<Path> task = new Task<>() {

      /**
       * Downloads the installer, on the background thread, and reports its progress.
       *
       * @return the installer, checked.
       *
       * @throws Exception if the installer cannot be downloaded or is damaged.
       */
      @Override
      protected Path call() throws Exception {
        return updater.download(release, (done, total) -> {
          updateProgress(done, total);
          updateMessage(messages.get("update.download.progress", done / MEGABYTE));
        }, this::isCancelled);
      }

    };
    final ProgressBar bar = new ProgressBar();
    bar.setMaxWidth(Double.MAX_VALUE);
    bar.progressProperty().bind(task.progressProperty());
    final Label done = new Label();
    done.getStyleClass().add("caption");
    done.textProperty().bind(task.messageProperty());
    final VBox content = new VBox(bar, done);
    content.getStyleClass().add("download");
    final Alert box = main.dialogs().progress("update.download.title", content, release.version().toString());
    box.resultProperty().addListener((property, before, after) -> task.cancel());
    task.setOnSucceeded(event -> {
      box.close();
      main.install(task.getValue());
    });
    task.setOnFailed(event -> {
      box.close();
      main.dialogs().error(task.getException());
    });
    Thread.ofPlatform().daemon().name("jass-update").start(task);
  }

}
