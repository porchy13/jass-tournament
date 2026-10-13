package ch.varani.jass.app;

import ch.varani.jass.update.Release;
import ch.varani.jass.update.Updater;
import ch.varani.jass.update.Version;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;

/**
 * An automatic update replaced for the tests of the user interface: it never reads the network. It finds the release
 * given by the test, or nothing ({@link #NONE}), or fails; its download reports a progress, then gives the installer
 * of the test, fails, or waits until it is cancelled.
 *
 * <p>Safe for concurrent use: the methods run on the background tasks of the user interface.</p>
 */
final class FakeUpdater implements Updater {

  /** The version of the running application in the tests. */
  static final Version CURRENT = Version.parse("1.0.0");
  /** An update that never finds a newer release. */
  static final FakeUpdater NONE = new FakeUpdater(null, null, null);
  /** The size of the installer of the tests, in bytes. */
  static final long SIZE = 5_000_000;
  /** The number of hexadecimal digits of a SHA-256 digest. */
  private static final int DIGITS = 64;
  /** The interval between two checks of the cancellation, in milliseconds. */
  private static final long INTERVAL = 10;

  /** The release found, or {@code null} for none. */
  private final Release release;
  /** The installer given by the download, or {@code null} to wait until the download is cancelled. */
  private final Path installer;
  /** The failure of the check or of the download, or {@code null}. */
  private final IOException failure;
  /** The version of the application. */
  private final Version version;
  /** Released when the check is done. */
  private final CountDownLatch checked = new CountDownLatch(1);
  /** Released when the download has started. */
  private final CountDownLatch started = new CountDownLatch(1);
  /** Released when the download has ended. */
  private final CountDownLatch ended = new CountDownLatch(1);

  /**
   * Creates an update.
   *
   * @param release the release found, or {@code null} for none.
   * @param installer the installer given by the download, or {@code null} to wait until it is cancelled.
   * @param failure the failure of the check (without release) or of the download, or {@code null}.
   */
  FakeUpdater(final Release release, final Path installer, final IOException failure) {
    this(CURRENT, release, installer, failure);
  }

  /**
   * Creates an update of another version of the application.
   *
   * @param version the version of the application.
   * @param release the release found, or {@code null} for none.
   * @param installer the installer given by the download, or {@code null} to wait until it is cancelled.
   * @param failure the failure of the check (without release) or of the download, or {@code null}.
   */
  FakeUpdater(final Version version, final Release release, final Path installer, final IOException failure) {
    this.version = version;
    this.release = release;
    this.installer = installer;
    this.failure = failure;
  }

  /**
   * Returns a release 1.1.0 with its notes.
   *
   * @return the release.
   */
  static Release release() {
    return new Release(Version.parse("1.1.0"), "### Added\n- The automatic update.\n",
                       URI.create("https://example.org/jass-tournament-1.1.0-macos-aarch64.dmg"),
                       "jass-tournament-1.1.0-macos-aarch64.dmg", "0".repeat(DIGITS));
  }

  /**
   * Returns the version of the application of the tests.
   *
   * @return the version given at the creation, {@link #CURRENT} by default.
   */
  @Override
  public Version current() {
    return version;
  }

  /**
   * Finds the release of the test.
   *
   * @return the release, or nothing.
   *
   * @throws IOException the failure of the test, if there is no release.
   */
  @Override
  public Optional<Release> check() throws IOException {
    try {
      if (release == null && failure != null) {
        throw failure;
      }
      return Optional.ofNullable(release);
    } finally {
      checked.countDown();
    }
  }

  /**
   * Reports half of the download, then gives the installer, fails, or waits until it is cancelled.
   *
   * @param offered the release.
   * @param progress receives the progress.
   * @param cancelled tells whether the download is cancelled.
   *
   * @return the installer.
   *
   * @throws IOException the failure of the test.
   * @throws InterruptedException if the wait is interrupted.
   */
  @Override
  public Path download(final Release offered, final Progress progress, final BooleanSupplier cancelled)
      throws IOException, InterruptedException {
    try {
      progress.update(SIZE / 2, SIZE);
      started.countDown();
      if (failure != null) {
        throw failure;
      }
      while (installer == null) {
        if (cancelled.getAsBoolean()) {
          throw new CancellationException();
        }
        Thread.sleep(INTERVAL);
      }
      return installer;
    } finally {
      ended.countDown();
    }
  }

  /**
   * Waits until the check is done.
   *
   * @throws InterruptedException if the wait is interrupted.
   */
  void awaitCheck() throws InterruptedException {
    checked.await();
  }

  /**
   * Waits until the download has ended.
   *
   * @throws InterruptedException if the wait is interrupted.
   */
  void awaitEnd() throws InterruptedException {
    ended.await();
  }

  /**
   * Waits until the download has started.
   *
   * @throws InterruptedException if the wait is interrupted.
   */
  void awaitDownload() throws InterruptedException {
    started.await();
  }

}
