package ch.varani.jass.app;

import javafx.application.Platform;
import javafx.stage.Window;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Launcher}: the application is launched, shows its window, and {@code main} returns when the
 * application exits.
 *
 * <p>{@code Application.launch} can be called only once per JVM: Surefire runs each test class in its own JVM.</p>
 */
final class LauncherTest {

  /** The maximum time to wait for the application to start or to stop. */
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  /** The delay between two checks of the state of the application, in milliseconds. */
  private static final long POLL_MILLIS = 100;

  /**
   * The launcher opens a window, and {@code main} returns once the application exits.
   *
   * @throws InterruptedException if the test is interrupted while waiting.
   */
  @Test
  @DisplayName("The launcher opens the main window and returns when the application exits")
  void mainTest() throws InterruptedException {
    final Thread application = Thread.ofPlatform().start(() -> {
      try {
        Launcher.main(new String[0]);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      } catch (InterruptedException _) {
        Thread.currentThread().interrupt();
      }
    });
    assertTrue(waitForShowingWindow(), "No window has been shown");
    Platform.runLater(Platform::exit);
    application.join(TIMEOUT);
    assertFalse(application.isAlive(), "The launcher did not return after the exit of the application");
  }

  /**
   * Waits until the JavaFX toolkit is started and a window is showing, or until {@link #TIMEOUT}.
   *
   * @return {@code true} if a window is showing, {@code false} after the time-out.
   *
   * @throws InterruptedException if the test is interrupted while waiting.
   */
  private static boolean waitForShowingWindow() throws InterruptedException {
    final long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (isWindowShowing()) {
        return true;
      }
      Thread.sleep(POLL_MILLIS);
    }
    return false;
  }

  /**
   * Asks the JavaFX Application Thread whether a window is showing.
   *
   * @return {@code true} if a window is showing, {@code false} if none is or if the toolkit is not started yet.
   *
   * @throws InterruptedException if the test is interrupted while waiting for the answer.
   */
  private static boolean isWindowShowing() throws InterruptedException {
    final CompletableFuture<Boolean> showing = new CompletableFuture<>();
    try {
      Platform.runLater(() -> showing.complete(Window.getWindows().stream().anyMatch(Window::isShowing)));
      return showing.get(POLL_MILLIS, TimeUnit.MILLISECONDS);
    } catch (IllegalStateException | ExecutionException | TimeoutException _) {
      return false;
    }
  }

}
