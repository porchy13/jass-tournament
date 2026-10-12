package ch.varani.jass.app;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * The JavaFX application of the Jass Tournament: it opens the main window ({@link MainController}) in the language of
 * the system, on the data folder of the user, and completes the pending operations on the files when it stops.
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
public final class JassApplication extends Application {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The environment of the application. */
  private final AppContext context;
  /** The main window, or {@code null} before the start. */
  private MainController main;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the application in the environment of the system; called by the JavaFX runtime, which requires a public
   * constructor without parameter.
   */
  public JassApplication() {
    this(AppContext.fromSystem());
  }

  /**
   * Creates the application in a given environment, e.g. for the tests.
   *
   * @param context the environment.
   */
  JassApplication(final AppContext context) {
    super();
    this.context = context;
  }

  // *******************************************************************************************************************
  // Application
  // *******************************************************************************************************************

  /**
   * Opens the main window.
   *
   * @param stage the primary stage, provided by the JavaFX runtime.
   */
  @Override
  public void start(final Stage stage) {
    main = new MainController(stage, new Messages(context.locale()), context, getHostServices());
    main.start();
  }

  /**
   * Completes the pending operations on the files before the application exits.
   *
   * @throws InterruptedException if the wait is interrupted.
   */
  @Override
  public void stop() throws InterruptedException {
    if (main != null) {
      main.shutdown();
    }
  }

}
