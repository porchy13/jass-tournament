package ch.varani.jass.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The automatic update of the application (F17): finds whether a newer release is published, then downloads its
 * installer. {@link GitHubUpdater} looks for the releases of the project on GitHub; the tests of the user interface
 * replace it.
 *
 * <p>The methods read the network and write files: they run on a background thread, never on the JavaFX Application
 * Thread.</p>
 */
public interface Updater {

  /**
   * Receives the progress of a download.
   */
  @FunctionalInterface
  interface Progress {

    /**
     * Receives the number of bytes downloaded so far.
     *
     * @param done the number of bytes downloaded.
     * @param total the size of the installer in bytes, or {@code -1} if it is not known.
     */
    void update(long done, long total);

  }

  /**
   * Returns the version of the running application.
   *
   * @return the version.
   */
  Version current();

  /**
   * Finds whether a release newer than the running application is published, with an installer for this platform.
   *
   * @return the release to propose, or nothing if the application is up to date, is a development version, or no
   *         installer is published for this platform.
   *
   * @throws IOException if the releases cannot be read.
   * @throws InterruptedException if the thread is interrupted while waiting for the network.
   */
  Optional<Release> check() throws IOException, InterruptedException;

  /**
   * Downloads the installer of a release and checks its SHA-256 digest; a partial or damaged file is deleted.
   *
   * @param release the release.
   * @param progress receives the progress of the download, on the thread of the download.
   * @param cancelled tells whether the download is cancelled: it then stops and the partial file is deleted.
   *
   * @return the installer, checked.
   *
   * @throws UpdateException if the installer cannot be downloaded or does not match its digest.
   * @throws IOException if the installer cannot be written.
   * @throws InterruptedException if the thread is interrupted while waiting for the network.
   * @throws java.util.concurrent.CancellationException if the download is cancelled.
   */
  Path download(Release release, Progress progress, BooleanSupplier cancelled)
      throws IOException, InterruptedException;

}
