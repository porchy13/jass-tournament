package ch.varani.jass.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link DataFolder}: location of the data folder on each system.
 */
final class DataFolderTest {

  /** A fictitious home folder. */
  private static final Path HOME = Path.of("/home/user");
  /** An absolute data folder on the system running the tests ({@code /data} is not absolute on Windows). */
  private static final Path DATA = Path.of("/data").toAbsolutePath();

  /**
   * macOS: the folder is in the application support folder of the user.
   */
  @Test
  @DisplayName("macOS: Library/Application Support/Jass Tournament")
  void macTest() {
    assertEquals(HOME.resolve("Library/Application Support/Jass Tournament"),
                 DataFolder.resolve("Mac OS X", Map.of(), HOME));
  }

  /**
   * Windows: the folder is in the roaming application data folder, given by {@code APPDATA} when it is set.
   */
  @Test
  @DisplayName("Windows: %APPDATA%\\Jass Tournament, or AppData/Roaming by default")
  void windowsTest() {
    final Path roaming = HOME.resolve("AppData/Roaming/Jass Tournament");
    assertAll(
        () -> assertEquals(Path.of("/roaming").resolve(DataFolder.NAME),
                           DataFolder.resolve("Windows 11", Map.of(DataFolder.APPDATA, "/roaming"), HOME)),
        () -> assertEquals(roaming, DataFolder.resolve("Windows 11", Map.of(), HOME)),
        () -> assertEquals(roaming, DataFolder.resolve("Windows 11", Map.of(DataFolder.APPDATA, " "), HOME)));
  }

  /**
   * Linux: the folder is in {@code XDG_DATA_HOME} when it is set and absolute, in {@code ~/.local/share} otherwise.
   */
  @Test
  @DisplayName("Linux: $XDG_DATA_HOME/jass-tournament, or ~/.local/share by default")
  void linuxTest() {
    final Path share = HOME.resolve(".local/share/jass-tournament");
    assertAll(
        () -> assertEquals(DATA.resolve("jass-tournament"),
                           DataFolder.resolve("Linux", Map.of(DataFolder.XDG_DATA_HOME, DATA.toString()), HOME)),
        () -> assertEquals(share, DataFolder.resolve("Linux", Map.of(DataFolder.XDG_DATA_HOME, "data"), HOME)),
        () -> assertEquals(share, DataFolder.resolve("Linux", Map.of(DataFolder.XDG_DATA_HOME, ""), HOME)),
        () -> assertEquals(share, DataFolder.resolve("Linux", Map.of(), HOME)));
  }

  /**
   * The data folder of the current user is an absolute path ending with the name of the application.
   */
  @Test
  @DisplayName("The data folder of the current user is absolute")
  void currentUserTest() {
    final Path path = DataFolder.ofCurrentUser();
    assertAll(
        () -> assertTrue(path.isAbsolute()),
        () -> assertTrue(path.endsWith(DataFolder.NAME) || path.endsWith(DataFolder.LINUX_NAME), path::toString));
  }

}
