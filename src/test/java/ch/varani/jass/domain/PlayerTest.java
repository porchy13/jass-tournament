package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Player}: cleaning of the names, mandatory first name, full name, link to the directory and
 * comparison of persons.
 */
final class PlayerTest {

  /**
   * The names are stripped and their inner spaces reduced to one.
   */
  @Test
  @DisplayName("The names are stripped and their inner spaces reduced")
  void cleanTest() {
    final Player player = new Player("  Anne   Marie ", "\tDe   la Fontaine  ");
    assertAll(
        () -> assertEquals("Anne Marie", player.firstName()),
        () -> assertEquals("De la Fontaine", player.lastName()));
  }

  /**
   * A blank first name is rejected.
   */
  @Test
  @DisplayName("A blank first name is rejected")
  void blankFirstNameTest() {
    final DomainException exception = assertThrows(DomainException.class, () -> new Player("  ", "Fontaine"));
    assertEquals(ValidationError.BLANK_FIRST_NAME, exception.getError());
  }

  /**
   * A {@code null} name is a programming error.
   */
  @Test
  @DisplayName("A null name is rejected")
  void nullNamesTest() {
    assertAll(
        () -> assertThrows(NullPointerException.class, () -> new Player(null, "Fontaine")),
        () -> assertThrows(NullPointerException.class, () -> new Player("Anne", null)));
  }

  /**
   * The full name is the first name followed by the last name, or the first name alone.
   */
  @Test
  @DisplayName("The full name joins the first and the last names, which may be empty")
  void fullNameTest() {
    assertAll(
        () -> assertEquals("Anne Fontaine", new Player("Anne", "Fontaine").fullName()),
        () -> assertEquals("Anne", new Player("Anne", " ").fullName()));
  }

  /**
   * Two players with the same names, accents, case and spaces ignored, are the same person.
   */
  @Test
  @DisplayName("Accents, case and spaces are ignored to recognise the same person")
  void samePersonTest() {
    final Player player = new Player("Zoé", "Müller");
    assertAll(
        () -> assertTrue(player.isSamePersonAs(new Player(" zoe ", "MULLER"))),
        () -> assertFalse(player.isSamePersonAs(new Player("Zoé", "Meyer"))),
        () -> assertFalse(player.isSamePersonAs(new Player("Zoé", ""))));
  }

  /**
   * Two players linked to the same person of the directory are the same person, whatever their names; two players
   * linked to different persons are different persons unless their names are equal.
   */
  @Test
  @DisplayName("Two players linked to the same person of the directory are the same person")
  void samePersonIdTest() {
    final UUID id = UUID.fromString("5d0c8a52-2f5e-4b47-9d3c-1a2b3c4d5e6f");
    final UUID other = UUID.fromString("6e1d9b63-3a6f-4c58-8e4d-2b3c4d5e6f70");
    final Player linked = new Player("Zoé", "Müller").withPersonId(id);
    assertAll(
        () -> assertEquals(id, linked.personId()),
        () -> assertNull(linked.withPersonId(null).personId()),
        () -> assertTrue(linked.isSamePersonAs(new Player("Zoe", "Muller-Favre", id))),
        () -> assertFalse(linked.isSamePersonAs(new Player("Zoe", "Muller-Favre", other))),
        () -> assertFalse(linked.isSamePersonAs(new Player("Zoe", "Muller-Favre"))),
        () -> assertFalse(new Player("Zoe", "Muller-Favre").isSamePersonAs(linked)),
        () -> assertTrue(linked.isSamePersonAs(new Player("zoe", "muller", other))));
  }

  /**
   * The comparison form of a text ignores accents, case and spaces.
   */
  @Test
  @DisplayName("The comparison form of a text ignores accents, case and spaces")
  void comparisonKeyTest() {
    assertAll(
        () -> assertEquals("zoe muller", Player.comparisonKey("  Zoé   MÜLLER ")),
        () -> assertTrue(new Player("Zoé", "Müller").hasSameNameAs(new Player("zoe", "muller"))));
  }

  /**
   * A player has not paid its registration fee until marked so; the link to the directory is kept.
   */
  @Test
  @DisplayName("A player has not paid its fee until marked so")
  void paidTest() {
    final UUID id = UUID.fromString("5d0c8a52-2f5e-4b47-9d3c-1a2b3c4d5e6f");
    final Player player = new Player("Anne", "Fontaine", id);
    final Player paid = player.withPaid(true);
    assertAll(
        () -> assertFalse(player.paid()),
        () -> assertTrue(paid.paid()),
        () -> assertTrue(paid.withPersonId(null).paid()),
        () -> assertEquals(id, paid.personId()),
        () -> assertFalse(paid.withPaid(false).paid()),
        () -> assertTrue(paid.isSamePersonAs(player)));
  }

}
