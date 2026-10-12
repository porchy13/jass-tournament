package ch.varani.jass.app;

import ch.varani.jass.persistence.DataFolder;
import ch.varani.jass.update.GitHubUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link AppContext}: data folder and language of the application.
 */
final class AppContextTest {

  /**
   * The data folder is the one of the user unless another one is named; the language and the kind of system follow
   * the system; the windows are those of the system; the update looks for the releases on GitHub.
   */
  @Test
  @DisplayName("The data folder is the one of the user unless another one is named")
  void contextTest() {
    final AppContext user = AppContext.of(null, Locale.GERMANY, "Mac OS X");
    final AppContext other = AppContext.of("/tmp/jass", Locale.JAPAN, "Windows 11");
    assertAll(
        () -> assertEquals(DataFolder.ofCurrentUser(), user.dataFolder()),
        () -> assertEquals(Languages.GERMAN, user.locale()),
        () -> assertEquals(Dialogs.Natives.SYSTEM, user.natives()),
        () -> assertEquals(Path.of("/tmp/jass"), other.dataFolder()),
        () -> assertEquals(Languages.ENGLISH, other.locale()),
        () -> assertTrue(user.mac()),
        () -> assertFalse(other.mac()),
        () -> assertTrue(other.updater() instanceof GitHubUpdater),
        () -> assertEquals(Path.of(System.getProperty(AppContext.DATA_FOLDER)), AppContext.fromSystem().dataFolder()));
  }

}
