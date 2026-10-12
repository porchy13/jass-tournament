package ch.varani.jass.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link GitHubUpdater} on a fake network ({@link FakeHttpClient}): check of the latest release, download and
 * check of the installer, failures.
 */
final class GitHubUpdaterTest {

  /** The repository of the tests. */
  private static final URI REPOSITORY = URI.create("https://example.org/jass/");
  /** The checksum file of the latest release. */
  private static final URI SUMS = REPOSITORY.resolve("releases/latest/download/SHA256SUMS");
  /** The files of the release 1.2.0. */
  private static final URI FILES = REPOSITORY.resolve("releases/download/v1.2.0/");
  /** The name of the installer of macOS Apple Silicon, version 1.2.0. */
  private static final String INSTALLER = "jass-tournament-1.2.0-macos-aarch64.dmg";
  /** The content of the installer. */
  private static final byte[] CONTENT = "fictitious installer".getBytes(StandardCharsets.US_ASCII);
  /** The notes of the release. */
  private static final byte[] NOTES = "### Added\n- Something new.\n".getBytes(StandardCharsets.UTF_8);
  /** The largest installer accepted by the tests, in bytes. */
  private static final long MAX = 64;

  /** The folder of the downloads. */
  @TempDir
  private Path folder;
  /** The fake network. */
  private final FakeHttpClient client = new FakeHttpClient();

  /**
   * Returns the checksum file of the release 1.2.0, as written by {@code sha256sum}.
   *
   * @param installer the content of the installer of macOS Apple Silicon.
   *
   * @return the content of the file.
   */
  private static byte[] sums(final byte[] installer) {
    final String text = GitHubUpdater.sha256(installer) + "  " + INSTALLER + "\n"
                        + GitHubUpdater.sha256(new byte[] {1}) + " *jass-tournament-1.2.0-windows-x64.msi\n"
                        + GitHubUpdater.sha256(NOTES) + "  release-notes.md\n";
    return text.getBytes(StandardCharsets.US_ASCII);
  }

  /**
   * Publishes the release 1.2.0 on the fake network.
   *
   * @return the fake network.
   */
  private FakeHttpClient published() {
    return client.answer(SUMS, FakeHttpClient.Answer.ok(sums(CONTENT)))
                 .answer(FILES.resolve("release-notes.md"), FakeHttpClient.Answer.ok(NOTES))
                 .answer(FILES.resolve(INSTALLER), FakeHttpClient.Answer.ok(CONTENT));
  }

  /**
   * Returns the update of an application.
   *
   * @param version the version of the application.
   * @param target its platform, or {@code null}.
   *
   * @return the update.
   */
  private GitHubUpdater updater(final String version, final Target target) {
    return new GitHubUpdater(client, REPOSITORY, Version.parse(version), target, folder.resolve("updates"), MAX);
  }

  /**
   * Returns the release 1.2.0 found by an application 1.1.0 on macOS Apple Silicon.
   *
   * @return the release.
   *
   * @throws Exception if it is not found.
   */
  private Release release() throws Exception {
    return updater("1.1.0", Target.MACOS_AARCH64).check().orElseThrow();
  }

  /**
   * A newer release is found, with its notes and the installer of the platform, through HTTPS only.
   *
   * @throws Exception if the check fails.
   */
  @Test
  @DisplayName("F17: a newer release is proposed with its notes and the installer of the platform")
  void newerTest() throws Exception {
    published();
    final Release release = release();
    assertAll(
        () -> assertEquals(Version.parse("1.2.0"), release.version()),
        () -> assertEquals("### Added\n- Something new.\n", release.notes()),
        () -> assertEquals(FILES.resolve(INSTALLER), release.installer()),
        () -> assertEquals(INSTALLER, release.fileName()),
        () -> assertEquals(GitHubUpdater.sha256(CONTENT), release.sha256()),
        () -> assertTrue(client.requests().stream().allMatch(request -> "https".equals(request.uri().getScheme()))),
        () -> assertTrue(client.requests().stream().allMatch(request -> "GET".equals(request.method()))));
  }

  /**
   * Nothing is proposed when the application is up to date, newer, a development version, or when no installer is
   * published for its platform; a development version or an unknown platform asks nothing to the network.
   */
  @Test
  @DisplayName("F17: nothing is proposed to an application up to date or without installer")
  void upToDateTest() {
    published();
    assertAll(
        () -> assertEquals(Optional.empty(), updater("1.2.0", Target.MACOS_AARCH64).check()),
        () -> assertEquals(Optional.empty(), updater("2.0.0", Target.MACOS_AARCH64).check()),
        () -> assertEquals(Optional.empty(), updater("1.1.0", Target.LINUX_X64).check()));
    final int requests = client.requests().size();
    assertAll(
        () -> assertEquals(Optional.empty(), updater("1.1.0-SNAPSHOT", Target.MACOS_AARCH64).check()),
        () -> assertEquals(Optional.empty(), updater("1.1.0", null).check()),
        () -> assertEquals(requests, client.requests().size()));
  }

  /**
   * The check deletes the installers of a previous update; it fails if they cannot be deleted.
   *
   * @throws Exception if a file cannot be written.
   */
  @Test
  @DisplayName("F17: the installers of a previous update are deleted at the next check")
  void cleanTest() throws Exception {
    final Path updates = Files.createDirectories(folder.resolve("updates"));
    final Path old = Files.writeString(updates.resolve("jass-tournament-1.1.0-macos-aarch64.dmg"), "old");
    assertEquals(Optional.empty(), updater("1.1.0", null).check());
    assertFalse(Files.exists(old));
    Files.writeString(Files.createDirectories(updates.resolve("folder")).resolve("file"), "x");
    assertThrows(IOException.class, () -> updater("1.1.0", null).check());
  }

  /**
   * Any failure of the network, or a published file without the expected form, makes the check fail.
   */
  @Test
  @DisplayName("F17: the check fails without network or with unexpected files")
  void checkFailuresTest() {
    final List<Executable> cases = new ArrayList<>();
    cases.add(network -> network.unreachable(SUMS));
    cases.add(network -> { });
    cases.add(network -> network.answer(SUMS, FakeHttpClient.Answer.ok("x".getBytes(StandardCharsets.UTF_8))));
    cases.add(network -> network.answer(SUMS, FakeHttpClient.Answer.ok(new byte[GitHubUpdater.MAX_TEXT + 1])));
    cases.add(network -> network.answer(SUMS, new FakeHttpClient.Answer(FakeHttpClient.OK, sums(CONTENT), null,
                                                                        true)));
    cases.add(network -> network.answer(SUMS, FakeHttpClient.Answer.ok(sums(CONTENT))));
    cases.add(network -> network.answer(SUMS, FakeHttpClient.Answer.ok(sums(CONTENT)))
                                .answer(FILES.resolve("release-notes.md"), FakeHttpClient.Answer.ok(CONTENT)));
    for (final Executable failure : cases) {
      final FakeHttpClient network = new FakeHttpClient();
      final GitHubUpdater updater = new GitHubUpdater(network, REPOSITORY, Version.parse("1.1.0"),
                                                      Target.MACOS_AARCH64, folder, MAX);
      failure.run(network);
      final UpdateException error = assertThrows(UpdateException.class, updater::check);
      assertEquals(UpdateError.UNAVAILABLE, error.getError());
    }
  }

  /**
   * Prepares a fake network for a failing check.
   */
  @FunctionalInterface
  private interface Executable {

    /**
     * Prepares the fake network.
     *
     * @param network the fake network.
     */
    void run(FakeHttpClient network);

  }

  /**
   * The installer is downloaded into the folder of the downloads, with its progress, and checked.
   *
   * @throws Exception if the download fails.
   */
  @Test
  @DisplayName("F17: the installer is downloaded with its progress and checked against its digest")
  void downloadTest() throws Exception {
    published();
    final List<long[]> progress = new ArrayList<>();
    final Path installer = updater("1.1.0", Target.MACOS_AARCH64)
        .download(release(), (done, total) -> progress.add(new long[] {done, total}), () -> false);
    final long files;
    try (Stream<Path> listed = Files.list(installer.getParent())) {
      files = listed.count();
    }
    assertAll(
        () -> assertEquals(folder.resolve("updates").resolve(INSTALLER), installer),
        () -> assertArrayEquals(CONTENT, Files.readAllBytes(installer)),
        () -> assertArrayEquals(new long[] {CONTENT.length, CONTENT.length}, progress.getLast()),
        () -> assertEquals(1, files));
  }

  /**
   * Without a size announced, the progress has no total.
   *
   * @throws Exception if the download fails.
   */
  @Test
  @DisplayName("F17: the progress of a download without announced size has no total")
  void unknownSizeTest() throws Exception {
    published().answer(FILES.resolve(INSTALLER), new FakeHttpClient.Answer(FakeHttpClient.OK, CONTENT, null, false));
    final List<Long> totals = new ArrayList<>();
    updater("1.1.0", Target.MACOS_AARCH64).download(release(), (done, total) -> totals.add(total), () -> false);
    assertEquals(List.of(-1L), totals);
  }

  /**
   * A download that fails, is too large, does not match its digest or is cancelled leaves no file.
   *
   * @throws Exception if the release is not found.
   */
  @Test
  @DisplayName("F17: a damaged, too large, broken or cancelled download leaves no file")
  void downloadFailuresTest() throws Exception {
    published();
    final Release release = release();
    final GitHubUpdater updater = updater("1.1.0", Target.MACOS_AARCH64);
    final Updater.Progress none = (done, total) -> { };
    final byte[] large = new byte[(int) MAX + 1];
    final Map<UpdateError, FakeHttpClient.Answer> failures = Map.of(
        UpdateError.UNAVAILABLE, new FakeHttpClient.Answer(FakeHttpClient.OK, CONTENT, null, true));
    for (final Map.Entry<UpdateError, FakeHttpClient.Answer> failure : failures.entrySet()) {
      client.answer(release.installer(), failure.getValue());
      assertEquals(failure.getKey(), assertThrows(UpdateException.class,
                                                  () -> updater.download(release, none, () -> false)).getError());
    }
    final List<FakeHttpClient.Answer> damaged = List.of(
        FakeHttpClient.Answer.ok(large), new FakeHttpClient.Answer(FakeHttpClient.OK, large, null, false),
        FakeHttpClient.Answer.ok("another installer".getBytes(StandardCharsets.US_ASCII)));
    for (final FakeHttpClient.Answer answer : damaged) {
      client.answer(release.installer(), answer);
      assertEquals(UpdateError.DAMAGED, assertThrows(UpdateException.class,
                                                     () -> updater.download(release, none, () -> false)).getError());
    }
    client.answer(release.installer(), FakeHttpClient.Answer.ok(CONTENT));
    assertThrows(CancellationException.class, () -> updater.download(release, none, () -> true));
    client.answer(release.installer(), new FakeHttpClient.Answer(FakeHttpClient.NOT_FOUND, new byte[0], null, false));
    assertEquals(UpdateError.UNAVAILABLE, assertThrows(UpdateException.class,
                                                       () -> updater.download(release, none, () -> false)).getError());
    try (Stream<Path> files = Files.list(folder.resolve("updates"))) {
      assertEquals(0, files.count());
    }
  }

  /**
   * The repository must be reached through HTTPS.
   */
  @Test
  @DisplayName("F17: the releases are read through HTTPS only")
  void httpsTest() {
    assertThrows(IllegalArgumentException.class,
                 () -> new GitHubUpdater(client, URI.create("http://example.org/"), Version.parse("1.0.0"), null,
                                         folder, MAX));
  }

  /**
   * The update of the running application uses its version and the public repository. The check itself is not run:
   * with the version of a release, it would reach GitHub, which a test must never do (see {@link #upToDateTest()} for a
   * development version).
   */
  @Test
  @DisplayName("F17: the update of the application uses its version and the public repository")
  void ofTest() {
    final GitHubUpdater updater = GitHubUpdater.of(folder);
    assertAll(
        () -> assertEquals(Version.current(), updater.current()),
        () -> assertEquals("https", GitHubUpdater.REPOSITORY_URI.getScheme()));
  }

  /**
   * An unknown digest algorithm is a programming error.
   */
  @Test
  @DisplayName("An unknown digest algorithm is a programming error")
  void digestTest() {
    assertThrows(IllegalStateException.class, () -> GitHubUpdater.digest("NO-SUCH-ALGORITHM"));
  }

  /**
   * The requests have a time-out.
   *
   * @throws Exception if the check fails.
   */
  @Test
  @DisplayName("F17: every request has a time-out")
  void timeoutTest() throws Exception {
    published();
    updater("1.1.0", Target.MACOS_AARCH64).download(release(), (done, total) -> { }, () -> false);
    assertTrue(client.requests().stream().map(HttpRequest::timeout).allMatch(Optional::isPresent));
  }

}
