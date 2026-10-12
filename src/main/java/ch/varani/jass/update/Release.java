package ch.varani.jass.update;

import java.net.URI;
import java.util.Objects;

/**
 * A release of the application newer than the running one, with the installer of the platform: what the user is
 * proposed to install.
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param version the version of the release.
 * @param notes the notes of the release (its section of the changelog, in Markdown), as published.
 * @param installer the address of the installer of the platform, in HTTPS.
 * @param fileName the name of the installer, e.g. {@code jass-tournament-1.2.0-macos-aarch64.dmg}.
 * @param sha256 the SHA-256 digest of the installer published with the release, 64 hexadecimal digits in lower case.
 */
public record Release(Version version, String notes, URI installer, String fileName, String sha256) {

  /**
   * Checks the release.
   *
   * @param version the version of the release.
   * @param notes the notes of the release.
   * @param installer the address of the installer.
   * @param fileName the name of the installer.
   * @param sha256 the digest of the installer.
   *
   * @throws NullPointerException if a value is {@code null}.
   */
  public Release {
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(notes, "notes");
    Objects.requireNonNull(installer, "installer");
    Objects.requireNonNull(fileName, "fileName");
    Objects.requireNonNull(sha256, "sha256");
  }

}
