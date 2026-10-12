package ch.varani.jass.directory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of {@link Participation}: mandatory values and change of the team mate.
 */
final class ParticipationTest {

  /**
   * The edition, its name and its date are mandatory; the team mate is optional and can be changed.
   */
  @Test
  @DisplayName("The edition is mandatory, the team mate optional")
  void valuesTest() {
    final UUID edition = People.EDITION;
    final LocalDate date = People.DATE;
    final Participation participation = new Participation(edition, "Match", date, null);
    assertAll(
        () -> assertNull(participation.teamMate()),
        () -> assertEquals(People.OTHER, participation.withTeamMate(People.OTHER).teamMate()),
        () -> assertEquals(edition, participation.withTeamMate(People.OTHER).edition()),
        () -> assertThrows(NullPointerException.class, () -> new Participation(null, "Match", date, null)),
        () -> assertThrows(NullPointerException.class, () -> new Participation(edition, null, date, null)),
        () -> assertThrows(NullPointerException.class, () -> new Participation(edition, "Match", null, null)));
  }

}
