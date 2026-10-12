package ch.varani.jass.persistence;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Locates the data folder of the application, where it stores the editions (and later the directory) without ever
 * asking the user for a path.
 *
 * <ul>
 *   <li>macOS: {@code ~/Library/Application Support/Jass Tournament};</li>
 *   <li>Windows: {@code %APPDATA%\Jass Tournament} ({@code ~\AppData\Roaming\Jass Tournament} when {@code APPDATA} is
 *       not set);</li>
 *   <li>Linux and the other systems: {@code $XDG_DATA_HOME/jass-tournament}, or {@code ~/.local/share/jass-tournament}
 *       when {@code XDG_DATA_HOME} is not set, empty or relative (XDG Base Directory Specification).</li>
 * </ul>
 *
 * <p>The folder is not created here; {@link EditionStore} creates it.</p>
 *
 * <p>Utility class without state, safe for concurrent use.</p>
 */
public final class DataFolder {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The name of the folder on macOS and Windows. */
  static final String NAME = "Jass Tournament";
  /** The name of the folder on Linux, following the conventions of the system. */
  static final String LINUX_NAME = "jass-tournament";
  /** The environment variable of the roaming application data folder of Windows. */
  static final String APPDATA = "APPDATA";
  /** The environment variable of the base data folder of the XDG Base Directory Specification. */
  static final String XDG_DATA_HOME = "XDG_DATA_HOME";

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Prevents the instantiation of this utility class.
   */
  private DataFolder() {
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns the data folder of the current user on the current system.
   *
   * @return the path of the data folder, which may not exist yet.
   */
  public static Path ofCurrentUser() {
    return resolve(System.getProperty("os.name"), System.getenv(), Path.of(System.getProperty("user.home")));
  }

  /**
   * Returns the data folder for a system, an environment and a home folder.
   *
   * @param osName the name of the operating system (system property {@code os.name}).
   * @param environment the environment variables.
   * @param home the home folder of the user.
   *
   * @return the path of the data folder.
   */
  static Path resolve(final String osName, final Map<String, String> environment, final Path home) {
    final String os = osName.toLowerCase(Locale.ROOT);
    if (os.startsWith("mac")) {
      return home.resolve("Library").resolve("Application Support").resolve(NAME);
    }
    if (os.startsWith("windows")) {
      final String appData = environment.get(APPDATA);
      final Path base = isSet(appData) ? Path.of(appData) : home.resolve("AppData").resolve("Roaming");
      return base.resolve(NAME);
    }
    final String dataHome = environment.get(XDG_DATA_HOME);
    final Path base = isSet(dataHome) && Path.of(dataHome).isAbsolute()
                      ? Path.of(dataHome) : home.resolve(".local").resolve("share");
    return base.resolve(LINUX_NAME);
  }

  /**
   * Tells whether an environment variable is set to a non-blank value.
   *
   * @param value the value of the variable, or {@code null}.
   *
   * @return {@code true} if the value is neither {@code null} nor blank.
   */
  private static boolean isSet(final String value) {
    return value != null && !value.isBlank();
  }

}
