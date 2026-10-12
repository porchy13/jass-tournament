package ch.varani.jass.update;

import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version of the application, in the form {@code major.minor.patch} of semantic versioning: the version of
 * {@code pom.xml}, of the installers and of the tags {@code vX.Y.Z} of the releases.
 *
 * <p>A version with a qualifier (e.g. {@code 1.2.0-SNAPSHOT}) is a <i>development</i> version: it comes before the
 * version without qualifier, and an application of a development version never looks for an update (it is not
 * installed by an installer). The versions are ordered by major, minor, then patch number.</p>
 *
 * <p>Immutable, safe for concurrent use.</p>
 *
 * @param major the major number, not negative.
 * @param minor the minor number, not negative.
 * @param patch the patch number, not negative.
 * @param development {@code true} for a development version (with a qualifier).
 */
public record Version(int major, int minor, int patch, boolean development) implements Comparable<Version> {

  // *******************************************************************************************************************
  // Constants
  // *******************************************************************************************************************

  /** The form of a version: an optional {@code v}, three numbers, an optional qualifier. */
  private static final Pattern FORM = Pattern.compile(
      "v?(?<major>\\d{1,9})\\.(?<minor>\\d{1,9})\\.(?<patch>\\d{1,9})(?<qualifier>-[0-9A-Za-z.-]+)?");
  /** The resource holding the version of the application, written by Maven from {@code pom.xml}. */
  private static final String RESOURCE = "ch.varani.jass.update.version";
  /** The order of the versions: numbers, then a development version before the version itself. */
  private static final Comparator<Version> ORDER = Comparator.comparingInt(Version::major)
                                                             .thenComparingInt(Version::minor)
                                                             .thenComparingInt(Version::patch)
                                                             .thenComparing(version -> !version.development());

  // *******************************************************************************************************************
  // Construction & Initialization
  // *******************************************************************************************************************

  /**
   * Checks the numbers of a version.
   *
   * @param major the major number.
   * @param minor the minor number.
   * @param patch the patch number.
   * @param development {@code true} for a development version.
   *
   * @throws IllegalArgumentException if a number is negative.
   */
  public Version {
    if (major < 0 || minor < 0 || patch < 0) {
      throw new IllegalArgumentException("A version number cannot be negative.");
    }
  }

  /**
   * Reads a version, e.g. {@code 1.2.0}, {@code v1.2.0} (a tag) or {@code 1.2.0-SNAPSHOT}.
   *
   * @param text the version.
   *
   * @return the version.
   *
   * @throws IllegalArgumentException if the text is not a version.
   */
  public static Version parse(final String text) {
    final Matcher matcher = FORM.matcher(text);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Not a version: " + text);
    }
    return new Version(Integer.parseInt(matcher.group("major")), Integer.parseInt(matcher.group("minor")),
                       Integer.parseInt(matcher.group("patch")), matcher.group("qualifier") != null);
  }

  /**
   * Returns the version of the running application, written into its resources by Maven from {@code pom.xml}.
   *
   * @return the version of the application.
   */
  public static Version current() {
    return parse(ResourceBundle.getBundle(RESOURCE).getString("version"));
  }

  // *******************************************************************************************************************
  // Methods
  // *******************************************************************************************************************

  /**
   * Compares two versions: by major, minor, then patch number; a development version comes before the version
   * without qualifier.
   *
   * @param other the other version.
   *
   * @return a negative number, zero or a positive number if this version is older, the same or newer.
   */
  @Override
  public int compareTo(final Version other) {
    return ORDER.compare(this, other);
  }

  /**
   * Tells whether this version is newer than another one.
   *
   * @param other the other version.
   *
   * @return {@code true} if this version comes after the other one.
   */
  public boolean isNewerThan(final Version other) {
    return compareTo(other) > 0;
  }

  /**
   * Returns the numbers of the version, as shown to the user and in the names of the installers.
   *
   * @return the version, e.g. {@code 1.2.0}, without qualifier.
   */
  @Override
  public String toString() {
    return major + "." + minor + "." + patch;
  }

}
