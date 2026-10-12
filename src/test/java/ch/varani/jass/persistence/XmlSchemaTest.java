package ch.varani.jass.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link XmlSchema}: a schema that cannot be compiled is a programming error.
 */
final class XmlSchemaTest {

  /**
   * A resource that is not a schema cannot be compiled.
   */
  @Test
  @DisplayName("A resource that is not a schema is rejected")
  void invalidSchemaTest() {
    final URL url = XmlSchemaTest.class.getResource("not-a-schema.xsd");
    assertThrows(IllegalStateException.class, () -> XmlSchema.load(url));
  }

}
