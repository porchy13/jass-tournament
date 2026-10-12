package ch.varani.jass.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Version}: reading, order, version of the application.
 */
final class VersionTest {

  /**
   * A version is read with or without the {@code v} of a tag; a qualifier makes it a development version.
   */
  @Test
  @DisplayName("A version is read from pom.xml, a tag or a qualified version")
  void parseTest() {
    assertAll(
        () -> assertEquals(new Version(1, 2, 0, false), Version.parse("1.2.0")),
        () -> assertEquals(new Version(1, 2, 0, false), Version.parse("v1.2.0")),
        () -> assertEquals(new Version(1, 0, 0, true), Version.parse("1.0.0-SNAPSHOT")),
        () -> assertEquals("10.20.30", Version.parse("10.20.30-rc.1").toString()));
  }

  /**
   * A text that is not a version is rejected, and so is a negative number.
   *
   * @param text the text.
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "1.2", "1.2.3.4", "x1.2.3", "1.2.3-", "1.2.3 ", "1.2.a"})
  @DisplayName("A text that is not a version is rejected")
  void invalidTest(final String text) {
    assertThrows(IllegalArgumentException.class, () -> Version.parse(text));
  }

  /**
   * No number of a version is negative.
   */
  @Test
  @DisplayName("A negative version number is rejected")
  void negativeTest() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> new Version(-1, 0, 0, false)),
        () -> assertThrows(IllegalArgumentException.class, () -> new Version(0, -1, 0, false)),
        () -> assertThrows(IllegalArgumentException.class, () -> new Version(0, 0, -1, false)));
  }

  /**
   * The versions are ordered by major, minor and patch number; a development version comes before its release.
   */
  @Test
  @DisplayName("The versions are ordered by their numbers, a development version before its release")
  void orderTest() {
    final List<Version> ordered = Stream.of("1.0.0-SNAPSHOT", "1.0.0", "1.0.1", "1.1.0-SNAPSHOT", "1.1.0", "2.0.0")
                                        .map(Version::parse).toList();
    assertAll(
        () -> assertEquals(ordered, ordered.reversed().stream().sorted().toList()),
        () -> assertTrue(Version.parse("1.0.10").isNewerThan(Version.parse("1.0.9"))),
        () -> assertFalse(Version.parse("1.0.0").isNewerThan(Version.parse("1.0.0"))),
        () -> assertFalse(Version.parse("1.0.0-SNAPSHOT").isNewerThan(Version.parse("1.0.0"))));
  }

  /**
   * The version of the application is the one of {@code pom.xml}, written into the resources by Maven.
   */
  @Test
  @DisplayName("The version of the application comes from pom.xml")
  void currentTest() {
    final Version current = Version.current();
    assertTrue(current.toString().matches("\\d+\\.\\d+\\.\\d+"));
  }

}
