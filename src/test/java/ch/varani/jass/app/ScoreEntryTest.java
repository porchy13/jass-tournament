package ch.varani.jass.app;

import ch.varani.jass.domain.Absence;
import ch.varani.jass.domain.DealPlan;
import ch.varani.jass.domain.DealScore;
import ch.varani.jass.domain.DomainException;
import ch.varani.jass.domain.Results;
import ch.varani.jass.domain.ScoreSheet;
import ch.varani.jass.domain.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of the entry of the score sheets without the toolkit: texts of the columns, derived and manual values, typed
 * texts, absences and sheets kept in the results.
 */
final class ScoreEntryTest {

  /** The points typed in column A. */
  private static final int GIVER = 100;
  /** The points derived in column B: 157 − 100. */
  private static final int DERIVED = 57;
  /** The points typed in column B, different from the derived value. */
  private static final int MANUAL = 60;

  /**
   * A deal not played shows nothing.
   */
  @Test
  @DisplayName("Rule 2: a deal not played shows nothing in both columns")
  void notPlayedTest() {
    assertAll(() -> assertEquals("", ScoreEntry.text(DealScore.NOT_PLAYED, ScoreEntry.Column.GIVER)),
              () -> assertEquals("", ScoreEntry.text(DealScore.NOT_PLAYED, ScoreEntry.Column.RECEIVER)),
              () -> assertFalse(ScoreEntry.derived(DealScore.NOT_PLAYED, ScoreEntry.Column.GIVER)),
              () -> assertFalse(ScoreEntry.derived(DealScore.NOT_PLAYED, ScoreEntry.Column.RECEIVER)));
  }

  /**
   * Typing column A derives column B.
   */
  @Test
  @DisplayName("Rule 4: typing column A shows column B derived as 157 minus A")
  void derivedReceiverTest() {
    final DealScore score = ScoreEntry.enter(DealScore.NOT_PLAYED, ScoreEntry.Column.GIVER, " 100 ");
    assertAll(() -> assertEquals(DealScore.of(GIVER), score),
              () -> assertEquals("100", ScoreEntry.text(score, ScoreEntry.Column.GIVER)),
              () -> assertEquals(String.valueOf(DERIVED), ScoreEntry.text(score, ScoreEntry.Column.RECEIVER)),
              () -> assertFalse(ScoreEntry.derived(score, ScoreEntry.Column.GIVER)),
              () -> assertTrue(ScoreEntry.derived(score, ScoreEntry.Column.RECEIVER)));
  }

  /**
   * Typing column B alone derives column A.
   */
  @Test
  @DisplayName("Rule 4: typing column B alone derives column A")
  void derivedGiverTest() {
    final DealScore score = ScoreEntry.enter(DealScore.NOT_PLAYED, ScoreEntry.Column.RECEIVER, "57");
    assertAll(() -> assertEquals(new DealScore(null, DERIVED), score),
              () -> assertEquals("100", ScoreEntry.text(score, ScoreEntry.Column.GIVER)),
              () -> assertTrue(ScoreEntry.derived(score, ScoreEntry.Column.GIVER)));
  }

  /**
   * The derived value left unchanged stays derived.
   */
  @Test
  @DisplayName("F9: leaving the derived value unchanged keeps it derived")
  void unchangedTest() {
    final DealScore score = DealScore.of(GIVER);
    assertSame(score, ScoreEntry.enter(score, ScoreEntry.Column.RECEIVER, "57"));
  }

  /**
   * Typing column B makes it manual.
   */
  @Test
  @DisplayName("F9: typing column B makes it manual, kept even when it differs from the derived value")
  void manualTest() {
    final DealScore score = ScoreEntry.enter(DealScore.of(GIVER), ScoreEntry.Column.RECEIVER, "60");
    assertAll(() -> assertEquals(new DealScore(GIVER, MANUAL), score),
              () -> assertFalse(ScoreEntry.derived(score, ScoreEntry.Column.RECEIVER)),
              () -> assertFalse(score.isConsistent()));
  }

  /**
   * Emptying a column makes it derived again, or the deal not played.
   */
  @Test
  @DisplayName("F9: emptying a column makes it derived again, or the deal not played")
  void emptiedTest() {
    final DealScore manual = new DealScore(GIVER, MANUAL);
    assertAll(() -> assertEquals(DealScore.of(GIVER), ScoreEntry.enter(manual, ScoreEntry.Column.RECEIVER, " ")),
              () -> assertEquals(DealScore.NOT_PLAYED,
                                 ScoreEntry.enter(DealScore.of(GIVER), ScoreEntry.Column.GIVER, "")));
  }

  /**
   * A text that is not a number between 0 and 157 is rejected.
   *
   * @param typed the text typed.
   */
  @ParameterizedTest
  @ValueSource(strings = {"abc", "-1", "12.5", "1000", "158"})
  @DisplayName("Rule 4: a text that is not a number between 0 and 157 is rejected")
  void rejectedTest(final String typed) {
    final DomainException error = assertThrows(DomainException.class,
        () -> ScoreEntry.enter(DealScore.NOT_PLAYED, ScoreEntry.Column.GIVER, typed));
    assertEquals(ValidationError.INVALID_POINTS, error.getError());
  }

  /**
   * The boxes of the absent sides.
   */
  @Test
  @DisplayName("Rule 19: the boxes of the sheet give the absent sides, and back")
  void absenceTest() {
    assertAll(() -> assertEquals(Absence.NONE, ScoreEntry.absence(false, false)),
              () -> assertEquals(Absence.GIVER, ScoreEntry.absence(true, false)),
              () -> assertEquals(Absence.RECEIVER, ScoreEntry.absence(false, true)),
              () -> assertEquals(Absence.BOTH, ScoreEntry.absence(true, true)),
              () -> assertTrue(ScoreEntry.absent(Absence.GIVER, ScoreEntry.Column.GIVER)),
              () -> assertFalse(ScoreEntry.absent(Absence.GIVER, ScoreEntry.Column.RECEIVER)),
              () -> assertTrue(ScoreEntry.absent(Absence.RECEIVER, ScoreEntry.Column.RECEIVER)),
              () -> assertTrue(ScoreEntry.absent(Absence.BOTH, ScoreEntry.Column.GIVER)),
              () -> assertFalse(ScoreEntry.absent(Absence.NONE, ScoreEntry.Column.RECEIVER)));
  }

  /**
   * The sheets where something is entered are kept, the others removed.
   */
  @Test
  @DisplayName("F9: a sheet with an entry is kept; a sheet where nothing is entered leaves the results")
  void keepTest() {
    final ScoreSheet empty = ScoreSheet.empty(1, 2, DealPlan.REGULATION);
    final ScoreSheet entered = empty.withDeal(1, DealScore.of(GIVER));
    final Results results = ScoreEntry.keep(Results.EMPTY, entered, DealPlan.REGULATION);
    assertAll(() -> assertEquals(entered, results.sheet(1, 2).orElseThrow()),
              () -> assertTrue(ScoreEntry.keep(results, empty, DealPlan.REGULATION).isEmpty()),
              () -> assertEquals(Absence.GIVER, ScoreEntry.keep(results, empty.withAbsence(Absence.GIVER),
                                                                DealPlan.REGULATION)
                                                          .sheet(1, 2).orElseThrow().absence()));
  }

}
