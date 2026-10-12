package ch.varani.jass.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link SettingsStore}: the settings of the interface are saved and read back; a missing, too large or
 * malformed file gives no setting; a file that cannot be read is reported.
 */
final class SettingsStoreTest {

  /** The temporary data folder. */
  @TempDir
  private Path folder;

  /**
   * The settings are written atomically and read back, sorted; without file, there is no setting.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("The settings are saved and read back; without file there is none")
  void roundTripTest() throws IOException {
    final SettingsStore store = new SettingsStore(folder);
    final Map<String, String> empty = store.load();
    store.save(Map.of("theme", "DARK", "window.width", "1280.0", "note", "é = ü"));
    assertAll(
        () -> assertEquals(Map.of(), empty),
        () -> assertEquals(Map.of("theme", "DARK", "window.width", "1280.0", "note", "é = ü"), store.load()),
        () -> assertEquals("note", store.load().keySet().iterator().next()));
  }

  /**
   * A file larger than the limit or malformed gives no setting.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("A too large or malformed file gives no setting")
  void invalidTest() throws IOException {
    final SettingsStore store = new SettingsStore(folder);
    Files.write(folder.resolve(SettingsStore.FILE_NAME), new byte[SettingsStore.MAX_SIZE + 1]);
    final Map<String, String> large = store.load();
    Files.writeString(folder.resolve(SettingsStore.FILE_NAME), "theme=\\uZZZZ", StandardCharsets.UTF_8);
    assertAll(
        () -> assertEquals(Map.of(), large),
        () -> assertEquals(Map.of(), store.load()));
  }

  /**
   * A file that cannot be read, here a folder, is reported.
   *
   * @throws IOException never.
   */
  @Test
  @DisplayName("A settings file that cannot be read is reported")
  void unreadableTest() throws IOException {
    Files.createDirectories(folder.resolve(SettingsStore.FILE_NAME));
    assertThrows(IOException.class, () -> new SettingsStore(folder).load());
  }

}
