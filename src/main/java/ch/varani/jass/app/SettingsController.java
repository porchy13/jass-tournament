package ch.varani.jass.app;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * The settings of the application (DESIGN.md §2, §9), shown in a panel from the File menu on macOS ("Settings…") and
 * from the Tools menu elsewhere ("Options…"): the theme (system, light, dark), the accent colour of the system, the
 * reduced animations, and the logo of the documents.
 *
 * <p><b>Logo</b>: the panel says which images are accepted (PNG or JPEG of at most 2 MiB, printed at the top left of
 * every document) and shows the logo of the moment, or that there is none; it can be chosen or removed.</p>
 *
 * <p>Every change applies at once and is saved ({@link MainController#changeAppearance(ThemeMode, boolean, boolean)}).
 * </p>
 *
 * <p>Confined to the JavaFX Application Thread.</p>
 */
final class SettingsController {

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The main window. */
  private final MainController main;
  /** The theme. */
  @FXML
  private ChoiceBox<ThemeMode> themeBox;
  /** The accent colour of the system. */
  @FXML
  private CheckBox accentBox;
  /** The reduced animations. */
  @FXML
  private CheckBox motionBox;
  /** The logo of the documents. */
  @FXML
  private ImageView logoView;
  /** The text shown without logo. */
  @FXML
  private Label noLogo;
  /** The button removing the logo. */
  @FXML
  private Button removeLogoButton;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the settings.
   *
   * @param main the main window.
   */
  SettingsController(final MainController main) {
    this.main = main;
  }

  /**
   * Shows the settings of the moment and follows their changes; reads the logo in the background; called by the
   * FXML loader.
   */
  @FXML
  void initialize() {
    final Messages messages = main.messages();
    final Settings settings = main.settings();
    themeBox.getItems().setAll(ThemeMode.values());
    themeBox.setConverter(Converters.showing(mode -> messages.get("theme." + mode.name())));
    themeBox.setValue(settings.theme());
    accentBox.setSelected(settings.systemAccent());
    motionBox.setSelected(settings.reducedMotion());
    themeBox.valueProperty().addListener((property, before, after) -> changed());
    accentBox.selectedProperty().addListener((property, before, after) -> changed());
    motionBox.selectedProperty().addListener((property, before, after) -> changed());
    refreshLogo();
  }

  /**
   * Applies and saves the appearance chosen.
   */
  private void changed() {
    main.changeAppearance(themeBox.getValue(), accentBox.isSelected(), motionBox.isSelected());
  }

  /**
   * Reads the logo in the background, then shows it, or says that there is none.
   */
  private void refreshLogo() {
    main.readLogo(this::showLogo);
  }

  /**
   * Shows the logo, or says that there is none.
   *
   * @param logo the content of the logo, or nothing.
   */
  private void showLogo(final Optional<byte[]> logo) {
    logoView.setImage(logo.map(content -> new Image(new ByteArrayInputStream(content))).orElse(null));
    noLogo.setVisible(logo.isEmpty());
    removeLogoButton.setDisable(logo.isEmpty());
  }

  // *******************************************************************************************************************
  // Actions
  // *******************************************************************************************************************

  /**
   * Replaces the logo by an image chosen by the user.
   */
  @FXML
  void chooseLogo() {
    main.chooseLogo(this::refreshLogo);
  }

  /**
   * Removes the logo, after confirmation.
   */
  @FXML
  void removeLogo() {
    main.removeLogo(this::refreshLogo);
  }

}
