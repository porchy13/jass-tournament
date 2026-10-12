package ch.varani.jass.app;

import ch.varani.jass.update.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link AboutController}, without the toolkit; the window itself is tested by {@link JassApplicationTest}.
 */
final class AboutControllerTest {

  /**
   * A development version is marked as such, a released version is not.
   */
  @Test
  @DisplayName("Issue #3: a development version is marked as such")
  void versionKeyTest() {
    assertAll(
        () -> assertEquals("about.version", AboutController.versionKey(Version.parse("1.0.1"))),
        () -> assertEquals("about.version.development", AboutController.versionKey(Version.parse("1.0.2-SNAPSHOT"))));
  }

}
