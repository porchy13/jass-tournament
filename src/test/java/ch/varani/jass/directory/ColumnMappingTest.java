package ch.varani.jass.directory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link ColumnMapping}: the column of the first names is mandatory, the others optional.
 */
final class ColumnMappingTest {

  /**
   * The optional columns may be missing; the first names may not; no column is below {@link ColumnMapping#NONE}.
   */
  @Test
  @DisplayName("The first names are mandatory, the other columns optional")
  void columnsTest() {
    final int none = ColumnMapping.NONE;
    final int below = none - 1;
    assertAll(
        () -> assertEquals(0, new ColumnMapping(0, none, none, none, false).firstName()),
        () -> assertThrows(IllegalArgumentException.class, () -> new ColumnMapping(none, 1, 2, 2, true)),
        () -> assertThrows(IllegalArgumentException.class, () -> new ColumnMapping(0, below, none, none, true)),
        () -> assertThrows(IllegalArgumentException.class, () -> new ColumnMapping(0, none, below, none, true)),
        () -> assertThrows(IllegalArgumentException.class, () -> new ColumnMapping(0, none, none, below, true)));
  }

}
