package ch.varani.jass.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * The settings of the user interface in the data folder ({@value #FILE_NAME}): the theme, the size and position of the
 * window, the widths of its panels. They are kept on this computer only, with the data, so that the tests never touch
 * the settings of the user.
 *
 * <p><b>Format</b>: a {@link Properties} file in UTF-8, of at most {@link #MAX_SIZE} bytes. The settings are not
 * precious: a missing, too large or malformed file gives no setting (the defaults of the interface), and is replaced
 * at the next save. The file is written atomically ({@link AtomicFiles}). It never holds personal data.</p>
 *
 * <p>The operations read and write a file: they run on the background queue of the user interface. Immutable, safe
 * for concurrent use; the file itself is not locked.</p>
 */
public final class SettingsStore {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The name of the file of the settings in the data folder. */
  static final String FILE_NAME = "settings.properties";
  /** The largest size of the file, in bytes. */
  static final int MAX_SIZE = 64 * 1024;
  /** The logger; it never logs the content of the file. */
  private static final Logger LOGGER = System.getLogger(SettingsStore.class.getName());

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The file of the settings. */
  private final Path file;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the store of the settings of a data folder.
   *
   * @param folder the data folder.
   */
  public SettingsStore(final Path folder) {
    this.file = folder.toAbsolutePath().resolve(FILE_NAME);
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Reads the settings.
   *
   * @return the settings by key, sorted; empty when the file is missing, too large or malformed.
   *
   * @throws IOException if the file exists but cannot be read.
   */
  public Map<String, String> load() throws IOException {
    final byte[] content;
    try (InputStream in = Files.newInputStream(file)) {
      content = in.readNBytes(MAX_SIZE + 1);
    } catch (NoSuchFileException _) {
      return Map.of();
    }
    if (content.length > MAX_SIZE) {
      LOGGER.log(Level.INFO, "The settings file is too large: the defaults are used.");
      return Map.of();
    }
    final Properties properties = new Properties();
    try {
      properties.load(new StringReader(new String(content, StandardCharsets.UTF_8)));
    } catch (IllegalArgumentException _) {
      LOGGER.log(Level.INFO, "The settings file is malformed: the defaults are used.");
      return Map.of();
    }
    final Map<String, String> settings = new TreeMap<>();
    properties.stringPropertyNames().forEach(key -> settings.put(key, properties.getProperty(key)));
    return settings;
  }

  /**
   * Writes the settings, atomically.
   *
   * @param settings the settings by key.
   *
   * @throws IOException if the file cannot be written; the previous file is then unchanged.
   */
  public void save(final Map<String, String> settings) throws IOException {
    final Properties properties = new Properties();
    properties.putAll(settings);
    AtomicFiles.write(file, out -> {
      final Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
      properties.store(writer, null);
      writer.flush();
    });
  }

}
