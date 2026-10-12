package ch.varani.jass.app;

import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link MainController} beyond the scenarios of {@link JassApplicationTest}: a view that cannot be loaded,
 * the proposed file names.
 */
@ExtendWith(ApplicationExtension.class)
final class MainControllerTest {

  /** The temporary data folder. */
  @TempDir
  private Path folder;
  /** The window. */
  private Stage stage;

  /**
   * Keeps the stage provided by TestFX.
   *
   * @param primaryStage the stage.
   */
  @Start
  void start(final Stage primaryStage) {
    stage = primaryStage;
  }

  /**
   * A view that cannot be loaded is a programming error, reported with its name.
   */
  @Test
  @DisplayName("A view that cannot be loaded is reported")
  void brokenViewTest() {
    final AppContext context = new AppContext(folder, Languages.ENGLISH, new FakeNatives().natives(),
                                              Clock.systemUTC(), Solving::of, false, FakeUpdater.NONE);
    final MainController main = new MainController(stage, new Messages(Languages.ENGLISH), context,
                                                   new JassApplication(context).getHostServices());
    assertEquals("The view broken.fxml cannot be loaded.",
                 assertThrows(UncheckedIOException.class, () -> main.load("broken.fxml", new Object())).getMessage());
  }

  /**
   * The proposed file names replace the characters that some systems forbid.
   */
  @Test
  @DisplayName("The proposed file names replace the forbidden characters")
  void fileNameTest() {
    assertAll(
        () -> assertEquals("Match d’hiver 2026", MainController.fileName("Match d’hiver 2026")),
        () -> assertEquals("A_B_C_D_E_F_G_H_I_", MainController.fileName("A/B\\C:D*E?F\"G<H>I|")));
  }

}
