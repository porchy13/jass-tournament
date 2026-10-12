package ch.varani.jass.update;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The platforms for which an installer is published with each release, and the names of their installers:
 * {@code jass-tournament-<version>-<platform>.<extension>}, as {@code .github/workflows/release.yml} names them.
 *
 * <p>Immutable, safe for concurrent use.</p>
 */
public enum Target {

  /** macOS on Apple Silicon: disk image. */
  MACOS_AARCH64("macos-aarch64", "dmg"),

  /** macOS on Intel: disk image. */
  MACOS_X64("macos-x64", "dmg"),

  /** Windows on x64: Windows Installer package. */
  WINDOWS_X64("windows-x64", "msi"),

  /** Linux on x64: Debian package. */
  LINUX_X64("linux-x64", "deb");

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The prefix of the names of the installers. */
  private static final String PREFIX = "jass-tournament-";

  // *******************************************************************************************************************
  // Attributes
  // *******************************************************************************************************************

  /** The platform in the name of the installer, e.g. {@code macos-aarch64}. */
  private final String platform;
  /** The extension of the installer, without dot. */
  private final String extension;
  /** The names of the installers of this platform; the group 1 is the version. */
  private final Pattern names;

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Creates a platform.
   *
   * @param platform the platform in the name of the installer.
   * @param extension the extension of the installer, without dot.
   */
  Target(final String platform, final String extension) {
    this.platform = platform;
    this.extension = extension;
    this.names = Pattern.compile(Pattern.quote(PREFIX) + "(\\d{1,9}\\.\\d{1,9}\\.\\d{1,9})-" + Pattern.quote(platform)
                                 + "\\." + Pattern.quote(extension));
  }

  /**
   * Returns the platform of a system, as given by the system properties {@code os.name} and {@code os.arch}.
   *
   * @param osName the name of the operating system, e.g. {@code Mac OS X}, {@code Windows 11}, {@code Linux}.
   * @param osArch the architecture, e.g. {@code aarch64}, {@code x86_64} or {@code amd64}.
   *
   * @return the platform, or nothing if no installer is published for this system.
   */
  public static Optional<Target> of(final String osName, final String osArch) {
    final String name = system(osName.toLowerCase(Locale.ROOT)) + "-" + cpu(osArch.toLowerCase(Locale.ROOT));
    return Arrays.stream(values()).filter(target -> target.platform.equals(name)).findFirst();
  }

  /**
   * Returns the system in the name of an installer.
   *
   * @param osName the name of the operating system, in lower case.
   *
   * @return {@code macos}, {@code windows}, {@code linux}, or the name itself for another system.
   */
  private static String system(final String osName) {
    if (osName.startsWith("mac")) {
      return "macos";
    }
    if (osName.startsWith("windows")) {
      return "windows";
    }
    return osName;
  }

  /**
   * Returns the processor in the name of an installer.
   *
   * @param osArch the architecture, in lower case.
   *
   * @return {@code x64} for the 64-bit Intel and AMD processors, the architecture itself otherwise.
   */
  private static String cpu(final String osArch) {
    return "amd64".equals(osArch) || "x86_64".equals(osArch) ? "x64" : osArch;
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Returns the name of the installer of a version for this platform.
   *
   * @param version the version.
   *
   * @return the name, e.g. {@code jass-tournament-1.2.0-macos-aarch64.dmg}.
   */
  public String installer(final Version version) {
    return PREFIX + version + "-" + platform + "." + extension;
  }

  /**
   * Reads the version of a file name, if it is the name of an installer of this platform.
   *
   * @param fileName the name of a file published with a release.
   *
   * @return the version of the installer, or nothing if the file is not an installer of this platform.
   */
  public Optional<Version> versionOf(final String fileName) {
    final Matcher matcher = names.matcher(fileName);
    return matcher.matches() ? Optional.of(Version.parse(matcher.group(1))) : Optional.empty();
  }

}
