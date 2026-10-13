package ch.varani.jass.app;

import ch.varani.jass.update.GitHubUpdater;
import ch.varani.jass.update.Version;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.net.URI;

/**
 * The window about the application, shown from the Help menu: the icon and the name of the application, its version
 * (marked as a development version when it has a qualifier), its author with a link to the site of the author, its
 * licence, a link to its source code, a link to the open-source libraries it ships
 * ({@link LibrariesController}), and the button of the donations ({@link MainController#showAbout()}).
 *
 * <p>The links are opened in the browser of the system only when the user clicks on them
 * ({@link MainController#browse(URI)}): the window itself calls no network.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class AboutController {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The site of the author. */
  static final URI WEBSITE = URI.create("https://www.varani.ch");
  /** The page of the donations. */
  static final URI DONATION = URI.create("https://www.paypal.me/porchy13");

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The version of the application. */
  @FXML
  private Label versionLabel;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the window about the application.
   *
   * @param main the main window.
   */
  AboutController(final MainController main) {
    this.main = main;
  }

  /**
   * Shows the version of the application; called by the FXML loader.
   */
  @FXML
  void initialize() {
    final Version version = main.version();
    versionLabel.setText(main.messages().get(versionKey(version), version));
  }

  /**
   * Returns the key of the text of a version: a development version is marked as such.
   *
   * @param version the version.
   *
   * @return the key of its text.
   */
  static String versionKey(final Version version) {
    return version.development() ? "about.version.development" : "about.version";
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Opens the site of the author.
   */
  @FXML
  void openWebsite() {
    main.browse(WEBSITE);
  }

  /**
   * Opens the source code of the application.
   */
  @FXML
  void openSource() {
    main.browse(GitHubUpdater.REPOSITORY_URI);
  }

  /**
   * Shows the open-source libraries shipped with the application.
   */
  @FXML
  void openLibraries() {
    main.showLibraries();
  }

}
