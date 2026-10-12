package ch.varani.jass.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link Release} and {@link UpdateException}.
 */
final class ReleaseTest {

  /** The version of the release. */
  private static final Version VERSION = Version.parse("1.2.0");
  /** The address of the installer. */
  private static final URI INSTALLER = URI.create("https://example.org/a.dmg");

  /**
   * A release has every value.
   */
  @Test
  @DisplayName("A release has a version, notes, an installer, its name and its digest")
  void releaseTest() {
    assertAll(
        () -> assertEquals(VERSION, new Release(VERSION, "", INSTALLER, "a.dmg", "0").version()),
        () -> assertThrows(NullPointerException.class, () -> new Release(null, "", INSTALLER, "a.dmg", "0")),
        () -> assertThrows(NullPointerException.class, () -> new Release(VERSION, null, INSTALLER, "a.dmg", "0")),
        () -> assertThrows(NullPointerException.class, () -> new Release(VERSION, "", null, "a.dmg", "0")),
        () -> assertThrows(NullPointerException.class, () -> new Release(VERSION, "", INSTALLER, null, "0")),
        () -> assertThrows(NullPointerException.class, () -> new Release(VERSION, "", INSTALLER, "a.dmg", null)));
  }

  /**
   * The message of the exception is its code; the cause is kept.
   */
  @Test
  @DisplayName("An update error carries its code and its cause")
  void exceptionTest() {
    final IllegalStateException cause = new IllegalStateException();
    assertAll(
        () -> assertEquals("DAMAGED", new UpdateException(UpdateError.DAMAGED).getMessage()),
        () -> assertEquals(UpdateError.UNAVAILABLE, new UpdateException(UpdateError.UNAVAILABLE, cause).getError()),
        () -> assertEquals(cause, new UpdateException(UpdateError.UNAVAILABLE, cause).getCause()),
        () -> assertThrows(NullPointerException.class, () -> new UpdateException(null)));
  }

}
