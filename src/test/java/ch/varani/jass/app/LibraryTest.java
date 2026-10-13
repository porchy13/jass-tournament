package ch.varani.jass.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Library}, without the toolkit; the window of the libraries is tested by {@link JassApplicationTest}.
 */
final class LibraryTest {

  /**
   * The key of the role of a library is made of the name of its constant, in lower case.
   */
  @Test
  @DisplayName("The key of the role of a library comes from the name of its constant")
  void roleKeyTest() {
    assertAll(
        () -> assertEquals("libraries.role.or_tools", Library.OR_TOOLS.roleKey()),
        () -> assertEquals("libraries.role.commons_logging", Library.COMMONS_LOGGING.roleKey()));
  }

  /**
   * Every library has a name, a licence, a site in HTTPS and the text of its role in every language.
   */
  @Test
  @DisplayName("Every library has a name, a licence, a site in HTTPS and its role in every language")
  void librariesTest() {
    final List<ResourceBundle> bundles = Stream.of(Languages.FRENCH, Languages.GERMAN, Languages.ITALIAN,
                                                   Languages.ENGLISH)
        .map(locale -> Languages.bundle(Messages.BUNDLE_NAME, locale)).toList();
    assertAll(Arrays.stream(Library.values()).map(library -> () -> assertAll(
        () -> assertTrue(!library.title().isBlank() && !library.licence().isBlank(), library.name()),
        () -> assertEquals("https", library.site().getScheme(), library.name()),
        () -> assertTrue(bundles.stream().allMatch(bundle -> bundle.containsKey(library.roleKey())),
                         library.name()))));
  }

  /**
   * The licences are SPDX expressions, the same in every language.
   */
  @Test
  @DisplayName("The licences of the libraries are SPDX expressions")
  void licenceTest() {
    assertAll(
        () -> assertEquals("Apache-2.0", Library.PDFBOX.licence()),
        () -> assertEquals("BSD-2-Clause", Library.COMMONMARK.licence()),
        () -> assertEquals("GPL-2.0-only WITH Classpath-exception-2.0", Library.JAVAFX.licence()),
        () -> assertEquals(Library.JAVAFX.licence(), Library.OPENJDK.licence()),
        () -> assertEquals("Google OR-Tools", Library.OR_TOOLS.title()),
        () -> assertEquals("https://openjfx.io", Library.JAVAFX.site().toString()));
  }

}
