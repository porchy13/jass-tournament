package ch.varani.jass.app;

import ch.varani.jass.directory.Person;
import ch.varani.jass.domain.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link PlayerSlot}: names typed, link to a person of the directory, display of a registered player.
 */
final class PlayerSlotTest {

  /**
   * A linked player keeps its link while its names are those of the person, and loses it when they change.
   */
  @Test
  @DisplayName("Typing other names removes the link to the directory")
  void linkTest() {
    final Person anne = Person.create("Anne", "Fontaine");
    final PlayerSlot slot = new PlayerSlot();
    final boolean blank = slot.isBlank();
    slot.link(anne);
    slot.setNames("Anne", "Fontaine");
    final Person kept = slot.person();
    slot.setNames("Anne", "Fontaine-Favre");
    final Person firstNameKept = slot.person();
    slot.link(anne);
    slot.setNames("Anna", "Fontaine");
    assertAll(
        () -> assertTrue(blank),
        () -> assertSame(anne, kept),
        () -> assertNull(firstNameKept),
        () -> assertNull(slot.person()),
        () -> assertEquals("Anna Fontaine", slot.query()),
        () -> assertFalse(slot.isBlank()));
  }

  /**
   * A registered player is shown linked to its person when it is still in the directory; the slot can be emptied.
   */
  @Test
  @DisplayName("A registered player is shown with its link, and the slot emptied")
  void showTest() {
    final Person anne = Person.create("Anne", "Fontaine");
    final PlayerSlot slot = new PlayerSlot();
    slot.show(new Player("Zoé", ""), null);
    final String unlinked = slot.query();
    slot.show(anne.asPlayer(), anne);
    final Person linked = slot.person();
    slot.clear();
    assertAll(
        () -> assertEquals("Zoé ", unlinked),
        () -> assertSame(anne, linked),
        () -> assertTrue(slot.isBlank()),
        () -> assertEquals("", slot.firstName()),
        () -> assertEquals("", slot.lastName()),
        () -> assertNull(slot.person()));
  }

  /**
   * A typed first name alone is not blank.
   */
  @Test
  @DisplayName("A first name alone is not blank")
  void blankTest() {
    final PlayerSlot first = new PlayerSlot();
    first.setNames("Anne", " ");
    final PlayerSlot last = new PlayerSlot();
    last.setNames(" ", "Fontaine");
    assertAll(
        () -> assertFalse(first.isBlank()),
        () -> assertFalse(last.isBlank()));
  }

}
