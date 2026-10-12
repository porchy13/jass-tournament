package ch.varani.jass.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The automatic update from the releases of the project on GitHub (F17).
 *
 * <p><b>Check</b> ({@link #check()}): the installers of a previous update are deleted from the folder of the
 * downloads; then, unless the running application is a development version or no installer is published for its
 * platform, the checksum file of the latest release ({@value #CHECKSUMS_FILE}, published by the release workflow with
 * every file of the release) is read from the address {@code releases/latest/download/} of the repository, which GitHub
 * redirects to the latest release. It names the installer of each platform, whose name carries the version
 * ({@link Target}). If this version is newer than the running one, the notes of the release ({@value #NOTES_FILE}) are
 * read too, and checked against their digest.</p>
 *
 * <p><b>Download</b> ({@link #download(Release, Progress, BooleanSupplier)}): the installer is written into the folder
 * of the downloads ({@value #UPDATES_FOLDER} in the data folder) under a temporary name, its SHA-256 digest computed on
 * the way, at most {@value #MAX_INSTALLER} bytes; it is renamed once its digest is the published one, deleted
 * otherwise, and when the download fails or is cancelled.</p>
 *
 * <p><b>Network</b>: HTTPS only (the address of the repository must be HTTPS, and the redirections never lead from
 * HTTPS to HTTP, {@link HttpClient.Redirect#NORMAL}); short time-outs; the requests are plain GET of public files and
 * send no data of the user. The texts read are at most {@value #MAX_TEXT} bytes.</p>
 *
 * <p>Immutable, safe for concurrent use; the methods block on the network and must run on a background thread.</p>
 */
public final class GitHubUpdater implements Updater {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The public repository of the project on GitHub, whose releases carry the installers. */
  public static final URI REPOSITORY_URI = URI.create("https://github.com/porchy13/jass-tournament/");
  /** The name of the checksum file of a release, as written by {@code sha256sum}. */
  static final String CHECKSUMS_FILE = "SHA256SUMS";
  /** The name of the notes of a release. */
  static final String NOTES_FILE = "release-notes.md";
  /** The folder of the downloads, in the data folder. */
  public static final String UPDATES_FOLDER = "updates";
  /** The largest installer accepted, in bytes (1 GiB). */
  static final long MAX_INSTALLER = 1L << 30;
  /** The largest text read (checksums, notes), in bytes (64 KiB). */
  static final int MAX_TEXT = 1 << 16;
  /** The HTTP status of a success. */
  private static final int OK = 200;
  /** The longest wait for a connection. */
  private static final Duration CONNECTION = Duration.ofSeconds(5);
  /** The longest wait for the answer to the check. */
  private static final Duration CHECK = Duration.ofSeconds(10);
  /** The longest wait for the beginning of the answer of a download. */
  private static final Duration DOWNLOAD = Duration.ofSeconds(30);
  /** The size of the buffer of a download, in bytes. */
  private static final int BUFFER = 1 << 16;
  /** The suffix of an installer being downloaded. */
  private static final String PARTIAL = ".part";
  /** A line of the checksum file: the digest, a space, a space or a star (binary mode), then the file name. */
  private static final Pattern LINE = Pattern.compile("([0-9a-f]{64}) [ *]([A-Za-z0-9._-]+)");
  /** The algorithm of the digests. */
  static final String ALGORITHM = "SHA-256";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The client of the network. */
  private final HttpClient client;
  /** The repository, ending with a slash. */
  private final URI repository;
  /** The version of the running application. */
  private final Version current;
  /** The platform of the running application, or {@code null} if no installer is published for it. */
  private final Target target;
  /** The folder of the downloads. */
  private final Path folder;
  /** The largest installer accepted, in bytes. */
  private final long maxInstaller;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates the update.
   *
   * @param client the client of the network, following the redirections.
   * @param repository the repository on GitHub, in HTTPS, ending with a slash.
   * @param current the version of the running application.
   * @param target the platform of the running application, or {@code null} if no installer is published for it.
   * @param folder the folder of the downloads.
   * @param maxInstaller the largest installer accepted, in bytes.
   *
   * @throws IllegalArgumentException if the repository is not an HTTPS address.
   */
  GitHubUpdater(final HttpClient client, final URI repository, final Version current, final Target target,
                final Path folder, final long maxInstaller) {
    if (!"https".equals(repository.getScheme())) {
      throw new IllegalArgumentException("The repository must be an HTTPS address.");
    }
    this.client = client;
    this.repository = repository;
    this.current = current;
    this.target = target;
    this.folder = folder;
    this.maxInstaller = maxInstaller;
  }

  /**
   * Creates the update of the running application: its version, its platform, the repository of the project, and the
   * folder of the downloads in a data folder.
   *
   * @param dataFolder the data folder of the application.
   *
   * @return the update.
   */
  public static GitHubUpdater of(final Path dataFolder) {
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                                        .connectTimeout(CONNECTION).build();
    return new GitHubUpdater(client, REPOSITORY_URI, Version.current(),
                             Target.of(System.getProperty("os.name"), System.getProperty("os.arch")).orElse(null),
                             dataFolder.resolve(UPDATES_FOLDER), MAX_INSTALLER);
  }

  // *******************************************************************************************************************
  // Updater
  // *******************************************************************************************************************

  /**
   * Returns the version of the running application.
   *
   * @return the version.
   */
  @Override
  public Version current() {
    return current;
  }

  /**
   * Deletes the installers of a previous update, then finds whether the latest release is newer than the running
   * application and has an installer for its platform.
   *
   * @return the release, with its notes, or nothing.
   *
   * @throws UpdateException if a file of the release cannot be read, or does not have the expected form.
   * @throws IOException if the installers of a previous update cannot be deleted.
   * @throws InterruptedException if the thread is interrupted while waiting for the network.
   */
  @Override
  public Optional<Release> check() throws IOException, InterruptedException {
    clean();
    if (current.development() || target == null) {
      return Optional.empty();
    }
    final Map<String, String> sums = checksums(text(repository.resolve("releases/latest/download/" + CHECKSUMS_FILE)));
    final Optional<Version> latest = sums.keySet().stream().map(target::versionOf).flatMap(Optional::stream)
                                         .findFirst();
    if (latest.isEmpty() || !latest.get().isNewerThan(current)) {
      return Optional.empty();
    }
    final Version version = latest.get();
    final URI files = repository.resolve("releases/download/v" + version + "/");
    final byte[] notes = text(files.resolve(NOTES_FILE));
    if (!sha256(notes).equals(sums.get(NOTES_FILE))) {
      throw new UpdateException(UpdateError.UNAVAILABLE);
    }
    final String installer = target.installer(version);
    return Optional.of(new Release(version, new String(notes, StandardCharsets.UTF_8), files.resolve(installer),
                                   installer, sums.get(installer)));
  }

  /**
   * Downloads the installer of a release into the folder of the downloads and checks its digest.
   *
   * @param release the release.
   * @param progress receives the progress of the download.
   * @param cancelled tells whether the download is cancelled.
   *
   * @return the installer, checked.
   *
   * @throws UpdateException if the installer cannot be downloaded ({@link UpdateError#UNAVAILABLE}), is too large or
   *                         does not match its digest ({@link UpdateError#DAMAGED}).
   * @throws IOException if the installer cannot be written.
   * @throws InterruptedException if the thread is interrupted while waiting for the network.
   * @throws CancellationException if the download is cancelled.
   */
  @Override
  public Path download(final Release release, final Progress progress, final BooleanSupplier cancelled)
      throws IOException, InterruptedException {
    final HttpResponse<InputStream> response = get(release.installer(), DOWNLOAD);
    final long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
    final Path partial = Files.createDirectories(folder).resolve(release.fileName() + PARTIAL);
    final String digest;
    try (InputStream in = response.body()) {
      if (total > maxInstaller) {
        throw new UpdateException(UpdateError.DAMAGED);
      }
      digest = copy(in, partial, total, progress, cancelled);
    } catch (final IOException | RuntimeException e) {
      Files.deleteIfExists(partial);
      throw e;
    }
    if (!digest.equals(release.sha256())) {
      Files.delete(partial);
      throw new UpdateException(UpdateError.DAMAGED);
    }
    return Files.move(partial, folder.resolve(release.fileName()), StandardCopyOption.REPLACE_EXISTING);
  }

  // *******************************************************************************************************************
  // Implementation
  // *******************************************************************************************************************

  /**
   * Deletes the files of the folder of the downloads: the installers of a previous update, installed or abandoned.
   *
   * @throws IOException if a file cannot be deleted.
   */
  private void clean() throws IOException {
    if (!Files.isDirectory(folder)) {
      return;
    }
    final List<Path> files;
    try (Stream<Path> listed = Files.list(folder)) {
      files = listed.toList();
    }
    for (final Path file : files) {
      Files.deleteIfExists(file);
    }
  }

  /**
   * Sends a GET request and checks that it succeeded.
   *
   * @param uri the address.
   * @param timeout the longest wait for the beginning of the answer.
   *
   * @return the answer, whose body must be closed.
   *
   * @throws UpdateException if the request fails or the answer is not a success.
   * @throws IOException if the body of an answer that is not a success cannot be closed.
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  private HttpResponse<InputStream> get(final URI uri, final Duration timeout)
      throws IOException, InterruptedException {
    final HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
    final HttpResponse<InputStream> response;
    try {
      response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (final IOException e) {
      throw new UpdateException(UpdateError.UNAVAILABLE, e);
    }
    if (response.statusCode() != OK) {
      response.body().close();
      throw new UpdateException(UpdateError.UNAVAILABLE);
    }
    return response;
  }

  /**
   * Reads a small text file of a release, at most {@value #MAX_TEXT} bytes.
   *
   * @param uri the address of the file.
   *
   * @return the content of the file.
   *
   * @throws UpdateException if the file cannot be read or is too large.
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  private byte[] text(final URI uri) throws UpdateException, InterruptedException {
    try (InputStream in = get(uri, CHECK).body()) {
      final byte[] content = in.readNBytes(MAX_TEXT + 1);
      if (content.length > MAX_TEXT) {
        throw new UpdateException(UpdateError.UNAVAILABLE);
      }
      return content;
    } catch (final UpdateException e) {
      throw e;
    } catch (final IOException e) {
      throw new UpdateException(UpdateError.UNAVAILABLE, e);
    }
  }

  /**
   * Reads a checksum file, as written by {@code sha256sum}: one line per file, the digest then the name.
   *
   * @param content the content of the file.
   *
   * @return the digests, by file name.
   *
   * @throws UpdateException if a line does not have the expected form.
   */
  static Map<String, String> checksums(final byte[] content) throws UpdateException {
    final Map<String, String> sums = new HashMap<>();
    for (final String line : new String(content, StandardCharsets.UTF_8).lines().toList()) {
      final Matcher matcher = LINE.matcher(line);
      if (!matcher.matches()) {
        throw new UpdateException(UpdateError.UNAVAILABLE);
      }
      sums.put(matcher.group(2), matcher.group(1));
    }
    return sums;
  }

  /**
   * Copies the installer being downloaded into a file, computes its digest and reports the progress.
   *
   * @param in the content of the installer.
   * @param file the file to write.
   * @param total the size announced, or {@code -1}.
   * @param progress receives the progress.
   * @param cancelled tells whether the download is cancelled.
   *
   * @return the SHA-256 digest of the content, 64 hexadecimal digits in lower case.
   *
   * @throws UpdateException if the content cannot be read, or exceeds the largest installer accepted.
   * @throws IOException if the file cannot be written.
   * @throws CancellationException if the download is cancelled.
   */
  private String copy(final InputStream in, final Path file, final long total, final Progress progress,
                      final BooleanSupplier cancelled) throws IOException {
    final MessageDigest digest = digest(ALGORITHM);
    final byte[] buffer = new byte[BUFFER];
    long done = 0;
    try (OutputStream out = Files.newOutputStream(file)) {
      for (int read = read(in, buffer); read >= 0; read = read(in, buffer)) {
        if (cancelled.getAsBoolean()) {
          throw new CancellationException("The download is cancelled.");
        }
        done += read;
        if (done > maxInstaller) {
          throw new UpdateException(UpdateError.DAMAGED);
        }
        digest.update(buffer, 0, read);
        out.write(buffer, 0, read);
        progress.update(done, total);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  /**
   * Reads the next bytes of a download.
   *
   * @param in the content being downloaded.
   * @param buffer receives the bytes.
   *
   * @return the number of bytes read, or {@code -1} at the end.
   *
   * @throws UpdateException if the connection fails.
   */
  private static int read(final InputStream in, final byte[] buffer) throws UpdateException {
    try {
      return in.read(buffer);
    } catch (final IOException e) {
      throw new UpdateException(UpdateError.UNAVAILABLE, e);
    }
  }

  /**
   * Computes the SHA-256 digest of a content.
   *
   * @param content the content.
   *
   * @return the digest, 64 hexadecimal digits in lower case.
   */
  static String sha256(final byte[] content) {
    return HexFormat.of().formatHex(digest(ALGORITHM).digest(content));
  }

  /**
   * Returns a new message digest of the JDK.
   *
   * @param algorithm the name of the algorithm.
   *
   * @return the message digest.
   *
   * @throws IllegalStateException if the algorithm is not available (SHA-256 is required in every JDK).
   */
  static MessageDigest digest(final String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("The digest algorithm " + algorithm + " is not available.", e);
    }
  }

}
