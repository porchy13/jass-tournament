package ch.varani.jass.persistence;

import ch.varani.jass.domain.Edition;
import ch.varani.jass.domain.TournamentMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the values of the persistence: {@link StoredEdition}, {@link EditionHeader}, {@link EditionList} and
 * {@link FileFormatException}.
 */
final class ValueTypesTest {

  /**
   * A stored edition needs an identifier and an edition.
   */
  @Test
  @DisplayName("A stored edition rejects missing values")
  void storedEditionTest() {
    final Edition edition = Fixtures.teamEdition();
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> new StoredEdition(null, edition)),
        () -> assertThrows(NullPointerException.class, () -> new StoredEdition(Fixtures.ID, null)));
  }

  /**
   * A header needs every value.
   */
  @Test
  @DisplayName("A header rejects missing values")
  void headerTest() {
    final UUID id = Fixtures.ID;
    final TournamentMode mode = TournamentMode.TEAM;
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> new EditionHeader(null, "n", Fixtures.DATE, mode, 1)),
        () -> assertThrows(NullPointerException.class, () -> new EditionHeader(id, null, Fixtures.DATE, mode, 1)),
        () -> assertThrows(NullPointerException.class, () -> new EditionHeader(id, "n", null, mode, 1)),
        () -> assertThrows(NullPointerException.class, () -> new EditionHeader(id, "n", Fixtures.DATE, null, 1)));
  }

  /**
   * A list copies its lists.
   */
  @Test
  @DisplayName("A list of editions copies its lists")
  void listTest() {
    final List<UUID> unreadable = new ArrayList<>(List.of(Fixtures.ID));
    final EditionList list = new EditionList(List.of(), unreadable);
    unreadable.clear();
    assertAll(
        () -> assertEquals(List.of(Fixtures.ID), list.unreadable()),
        () -> assertThrows(UnsupportedOperationException.class, () -> list.unreadable().clear()));
  }

  /**
   * The message of a format exception is its code only, without cause, and the error is mandatory.
   */
  @Test
  @DisplayName("A format exception carries its code only")
  void exceptionTest() {
    final FileFormatException exception = new FileFormatException(FormatError.INVALID_XML);
    assertAll(
        () -> assertEquals(FormatError.INVALID_XML, exception.getError()),
        () -> assertEquals("INVALID_XML", exception.getMessage()),
        () -> assertEquals(null, exception.getCause()),
        () -> assertThrows(NullPointerException.class, () -> new FileFormatException(null)));
  }

}
