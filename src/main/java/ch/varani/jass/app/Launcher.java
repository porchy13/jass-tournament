package ch.varani.jass.app;

import javafx.application.Application;

import java.io.IOException;
import java.util.List;

/**
 * Entry point of the application.
 *
 * <p>This class deliberately does not extend {@link Application}: when the main class is an {@link Application}, the
 * Java launcher starts the JavaFX toolkit before {@code main} (see {@code doc/phase-1-spikes.md}). {@link #main} only
 * launches {@link JassApplication}, and returns when the application exits; with the argument
 * {@value SmokeTest#ARGUMENT}, it runs the smoke test of the packaged application instead ({@link SmokeTest}), without
 * window, and returns once it passed.</p>
 *
 * <p>Stateless; {@link #main} must be called at most once per JVM, as {@link Application#launch(Class, String...)}.</p>
 */
public final class Launcher {

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this entry point.
   */
  private Launcher() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Launches the JavaFX application and waits until it exits, or runs the smoke test of the packaged application.
   *
   * @param args the command line arguments, passed to the application; {@value SmokeTest#ARGUMENT} runs the smoke
   *             test.
   *
   * @throws IOException if the smoke test cannot write its document.
   * @throws InterruptedException if the smoke test is interrupted.
   */
  public static void main(final String[] args) throws IOException, InterruptedException {
    if (List.of(args).contains(SmokeTest.ARGUMENT)) {
      SmokeTest.run(SmokeTest.TEAMS);
      return;
    }
    Application.launch(JassApplication.class, args);
  }

}
