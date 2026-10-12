package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of {@link DealType}: each type of deal belongs to its trump mode.
 */
final class DealTypeTest {

  /**
   * Rules 8 to 11: the four imposed trumps share their trump mode, the other types have their own.
   */
  @Test
  @DisplayName("Rules 8 to 11: each type of deal has its trump mode")
  void modeTest() {
    assertAll(
        () -> assertEquals(TrumpMode.IMPOSED_TRUMP, DealType.IMPOSED_HEARTS.mode()),
        () -> assertEquals(TrumpMode.IMPOSED_TRUMP, DealType.IMPOSED_CLUBS.mode()),
        () -> assertEquals(TrumpMode.IMPOSED_TRUMP, DealType.IMPOSED_DIAMONDS.mode()),
        () -> assertEquals(TrumpMode.IMPOSED_TRUMP, DealType.IMPOSED_SPADES.mode()),
        () -> assertEquals(TrumpMode.SCHIEBER, DealType.SCHIEBER.mode()),
        () -> assertEquals(TrumpMode.KREUZ, DealType.KREUZ.mode()),
        () -> assertEquals(TrumpMode.FORCED_TRUMP, DealType.FORCED_TRUMP.mode()));
  }

}
