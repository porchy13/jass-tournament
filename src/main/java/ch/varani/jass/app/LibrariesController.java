package ch.varani.jass.app;

import javafx.fxml.FXML;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The window of the open-source libraries shipped with the application, shown from the window about the application
 * ({@link MainController#showLibraries()}): one line per {@link Library}, its name as a link to its site, then its
 * role in the application and its licence.
 *
 * <p>The links are opened in the browser of the system only when the user clicks on them
 * ({@link MainController#browse(java.net.URI)}): the window itself calls no network.</p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class LibrariesController {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The lines of the libraries. */
  @FXML
  private VBox librariesBox;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the window of the libraries.
   *
   * @param main the main window.
   */
  LibrariesController(final MainController main) {
    this.main = main;
  }

  /**
   * Adds the line of every library, in the order of {@link Library}; called by the FXML loader.
   */
  @FXML
  void initialize() {
    for (final Library library : Library.values()) {
      librariesBox.getChildren().add(line(library));
    }
  }

  /**
   * Creates the line of a library: its name, a link that opens its site, above its role and its licence, in a
   * caption.
   *
   * @param library the library.
   *
   * @return the line, with the identifier of the name of the constant.
   */
  private VBox line(final Library library) {
    final Hyperlink name = new Hyperlink(library.title());
    name.setOnAction(event -> main.browse(library.site()));
    final Label details = new Label(main.messages().get("libraries.details", main.messages().get(library.roleKey()),
                                                        library.licence()));
    details.getStyleClass().add("caption");
    details.setWrapText(true);
    final VBox line = new VBox(name, details);
    line.setId(library.name());
    line.getStyleClass().add("library");
    return line;
  }

}
