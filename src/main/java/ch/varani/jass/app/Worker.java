package ch.varani.jass.app;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The background queue of the user interface: the operations on the files run one at a time, in the order of their
 * submission, on one background thread, never on the JavaFX Application Thread; their result, or their error, is
 * then handed to the JavaFX Application Thread.
 *
 * <p>As the operations run in order, a save submitted before a listing is always done before it. {@link #finish(long)}
 * lets the pending operations finish (e.g. the saves before the application exits). {@link #pending()} counts the
 * operations not finished yet, for the state of the saves in the status bar.</p>
 *
 * <p>Safe for concurrent use: the operations may be submitted from any thread; {@link #pending()} changes on the
 * JavaFX Application Thread.</p>
 */
final class Worker {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The longest wait for the pending operations when the application closes, in seconds. */
  static final long CLOSE_TIMEOUT = 60;
  /** The logger of the queue; it logs the kinds of the errors only, never their content. */
  private static final Logger LOGGER = System.getLogger(Worker.class.getName());

  // *******************************************************************************************************************
  // Inner types
  // *******************************************************************************************************************

  /**
   * An operation of the queue.
   *
   * @param <T> the type of its result.
   */
  @FunctionalInterface
  interface Operation<T> {

    /**
     * Runs the operation, on the background thread.
     *
     * @return the result.
     *
     * @throws Exception if the operation fails.
     */
    T run() throws Exception;

  }

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The background thread. */
  private final ExecutorService executor = Executors.newSingleThreadExecutor(
      runnable -> Thread.ofPlatform().name("jass-worker").unstarted(runnable));
  /** Receives the errors of the operations, on the JavaFX Application Thread. */
  private final Consumer<Exception> errors;
  /** The number of operations not finished yet. */
  private final ReadOnlyIntegerWrapper pending = new ReadOnlyIntegerWrapper();

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the queue.
   *
   * @param errors receives the errors of the operations, on the JavaFX Application Thread.
   */
  Worker(final Consumer<Exception> errors) {
    this.errors = errors;
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Submits an operation; its result is handed to the JavaFX Application Thread, or its error to the error receiver.
   *
   * @param <T> the type of the result.
   * @param operation the operation, run on the background thread.
   * @param done receives the result, on the JavaFX Application Thread.
   */
  <T> void submit(final Operation<T> operation, final Consumer<T> done) {
    Platform.runLater(() -> pending.set(pending.get() + 1));
    executor.execute(() -> {
      try {
        final T result = operation.run();
        Platform.runLater(() -> {
          pending.set(pending.get() - 1);
          done.accept(result);
        });
      } catch (final Exception e) {
        LOGGER.log(Level.WARNING, "An operation on the files failed: {0}", e.getClass().getSimpleName());
        Platform.runLater(() -> {
          pending.set(pending.get() - 1);
          errors.accept(e);
        });
      }
    });
  }

  /**
   * Returns the number of operations submitted and not finished yet.
   *
   * @return the number, changed on the JavaFX Application Thread.
   */
  ReadOnlyIntegerProperty pending() {
    return pending.getReadOnlyProperty();
  }

  /**
   * Lets the pending operations finish, then stops the background thread; a time-out is logged.
   *
   * @param seconds the longest wait, in seconds ({@link #CLOSE_TIMEOUT} when the application exits).
   *
   * @return {@code true} if every operation has finished in time.
   *
   * @throws InterruptedException if the wait is interrupted.
   */
  boolean finish(final long seconds) throws InterruptedException {
    executor.shutdown();
    final boolean finished = executor.awaitTermination(seconds, TimeUnit.SECONDS);
    if (!finished) {
      LOGGER.log(Level.WARNING, "The pending operations on the files did not finish in time.");
    }
    return finished;
  }

}
