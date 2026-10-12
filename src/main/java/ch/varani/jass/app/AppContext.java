package ch.varani.jass.app;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.persistence.DataFolder;
import ch.varani.jass.update.GitHubUpdater;
import ch.varani.jass.update.Updater;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.function.Function;

/**
 * The environment of the application: its data folder, its language, the windows of the system, the clock, the
 * solvers, the kind of system (macOS or another one) and the automatic update.
 *
 * <p>In production ({@link #fromSystem()}), the data folder is the one of the user ({@link DataFolder}), unless the
 * system property {@value #DATA_FOLDER} names another one (the tests use it so that they never touch the data of the
 * user); the language follows the one of the system ({@link Languages#resolve(Locale)}); the kind of system comes from
 * the system property {@code os.name} (DESIGN.md §2: global menu bar, place of the settings); the update looks for the
 * releases of the project on GitHub ({@link GitHubUpdater}, F17).</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param dataFolder the data folder.
 * @param locale the locale of the application, one of the {@link Languages}.
 * @param natives the windows of the system.
 * @param clock the clock, for the proposed dates and names.
 * @param solving creates the computation of the schedule of an edition ({@link Solving#of(Edition)}).
 * @param mac {@code true} on macOS: the settings are in the File menu and the application menu quits.
 * @param updater the automatic update, checked at start-up.
 */
record AppContext(Path dataFolder, Locale locale, Dialogs.Natives natives, Clock clock,
                  Function<Edition, Solving> solving, boolean mac, Updater updater) {

  /** The system property naming another data folder. */
  static final String DATA_FOLDER = "ch.varani.jass.dataFolder";

  /**
   * Returns the environment of the running system.
   *
   * @return the environment.
   */
  static AppContext fromSystem() {
    return of(System.getProperty(DATA_FOLDER), Locale.getDefault(), System.getProperty("os.name", ""));
  }

  /**
   * Returns the environment for a data folder and the locale of a system.
   *
   * @param dataFolder the path of the data folder, or {@code null} for the data folder of the user.
   * @param system the locale of the system.
   * @param osName the name of the operating system, as the system property {@code os.name}.
   *
   * @return the environment, with the windows of the system, its clock, the solvers and the update from GitHub.
   */
  static AppContext of(final String dataFolder, final Locale system, final String osName) {
    final Path folder = dataFolder == null ? DataFolder.ofCurrentUser() : Path.of(dataFolder);
    return new AppContext(folder, Languages.resolve(system), Dialogs.Natives.SYSTEM, Clock.systemDefaultZone(),
                          Solving::of, osName.startsWith("Mac"), GitHubUpdater.of(folder));
  }

}
