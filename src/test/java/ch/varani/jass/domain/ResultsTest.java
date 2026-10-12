package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link Results}: sheets sorted and unique per game, exclusions, drawing of lots.
 */
final class ResultsTest {

  /** A participant. */
  private static final int THREE = 3;

  /**
   * The sheets are sorted by round then table; a sheet replaces the previous one of its game.
   */
  @Test
  @DisplayName("The sheets are sorted, one per game")
  void sheetsTest() {
    final ScoreSheet second = ScoreSheet.empty(2, 1, DealPlan.REGULATION);
    final ScoreSheet first = ScoreSheet.empty(1, 2, DealPlan.REGULATION);
    final ScoreSheet firstTable = ScoreSheet.empty(1, 1, DealPlan.REGULATION);
    final Results results = Results.EMPTY.withSheet(second).withSheet(first).withSheet(firstTable);
    final ScoreSheet absent = first.withAbsence(Absence.BOTH);
    assertAll(
        () -> assertEquals(List.of(firstTable, first, second), results.sheets()),
        () -> assertEquals(Optional.of(absent), results.withSheet(absent).sheet(1, 2)),
        () -> assertEquals(THREE, results.withSheet(absent).sheets().size()),
        () -> assertEquals(Optional.empty(), results.withoutSheet(1, 2).sheet(1, 2)),
        () -> assertEquals(ValidationError.DUPLICATE_SHEET,
                           assertThrows(DomainException.class,
                                        () -> new Results(List.of(first, absent), List.of(), null)).getError()));
  }

  /**
   * Rule 17: a participant can be excluded and the exclusion cancelled; the exclusions are sorted and unique.
   */
  @Test
  @DisplayName("Rule 17: a participant can be excluded, and the exclusion cancelled")
  void exclusionTest() {
    final Results results = Results.EMPTY.withExclusion(THREE, true).withExclusion(1, true).withExclusion(1, true);
    assertAll(
        () -> assertEquals(List.of(1, THREE), results.excluded()),
        () -> assertEquals(List.of(THREE), results.withExclusion(1, false).excluded()),
        () -> assertFalse(results.isEmpty()));
  }

  /**
   * The results are empty until a sheet, an exclusion or a draw is entered.
   */
  @Test
  @DisplayName("The results are empty until something is entered")
  void emptyTest() {
    final TieBreak tieBreak = new TieBreak(1L, List.of());
    assertAll(
        () -> assertTrue(Results.EMPTY.isEmpty()),
        () -> assertFalse(Results.EMPTY.withTieBreak(tieBreak).isEmpty()),
        () -> assertNull(Results.EMPTY.withTieBreak(tieBreak).withTieBreak(null).tieBreak()),
        () -> assertFalse(Results.EMPTY.withSheet(ScoreSheet.empty(1, 1, DealPlan.REGULATION)).isEmpty()));
  }

}
