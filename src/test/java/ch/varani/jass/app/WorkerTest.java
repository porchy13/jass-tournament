package ch.varani.jass.app;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Worker}: the operations run in the background, in order, their results and errors reach the
 * JavaFX Application Thread, and the pending operations are completed when the queue finishes.
 */
@ExtendWith(ApplicationExtension.class)
final class WorkerTest {

  /** The longest wait of the tests, in seconds. */
  private static final long WAIT = 10;

  /**
   * Starts the JavaFX toolkit.
   *
   * @param stage the stage provided by TestFX, unused.
   */
  @Start
  void start(final Stage stage) {
    stage.hide();
  }

  /**
   * The result of an operation, run off the JavaFX Application Thread, is handed to it.
   */
  @Test
  @DisplayName("A result reaches the JavaFX Application Thread")
  void resultTest() {
    final CompletableFuture<Boolean> background = new CompletableFuture<>();
    final CompletableFuture<String> result = new CompletableFuture<>();
    final Worker worker = new Worker(result::completeExceptionally);
    worker.submit(() -> {
      background.complete(!Platform.isFxApplicationThread());
      return "done";
    }, value -> result.complete(Platform.isFxApplicationThread() + value));
    assertAll(
        () -> assertTrue(background.get(WAIT, TimeUnit.SECONDS)),
        () -> assertEquals("truedone", result.get(WAIT, TimeUnit.SECONDS)),
        () -> assertTrue(worker.finish(WAIT)));
  }

  /**
   * The error of an operation reaches the error receiver on the JavaFX Application Thread.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("An error reaches the error receiver")
  void errorTest() throws Exception {
    final CompletableFuture<Exception> received = new CompletableFuture<>();
    final Worker worker = new Worker(received::complete);
    worker.submit(() -> {
      throw new IOException("disk full");
    }, value -> received.complete(null));
    assertInstanceOf(IOException.class, received.get(WAIT, TimeUnit.SECONDS));
    worker.finish(WAIT);
  }

  /**
   * The queue waits for the pending operations, and reports a time-out.
   *
   * @throws Exception never.
   */
  @Test
  @DisplayName("The queue waits for the pending operations and reports a time-out")
  void finishTest() throws Exception {
    final CountDownLatch release = new CountDownLatch(1);
    final Worker worker = new Worker(error -> { });
    worker.submit(() -> release.await(WAIT, TimeUnit.SECONDS), value -> { });
    final boolean early = worker.finish(0);
    release.countDown();
    assertAll(
        () -> assertFalse(early),
        () -> assertTrue(worker.finish(WAIT)));
  }

}
