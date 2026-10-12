package ch.varani.jass.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests of {@link DomainException}: the error is kept, and the message is the name of the error only.
 */
final class DomainExceptionTest {

  /**
   * The error is returned, and the message is its name (no personal data).
   *
   * @param error the error.
   */
  @ParameterizedTest
  @EnumSource(ValidationError.class)
  @DisplayName("The exception keeps its error, and its message is the name of the error")
  void errorTest(final ValidationError error) {
    final DomainException exception = new DomainException(error);
    assertAll(
        () -> assertSame(error, exception.getError()),
        () -> assertEquals(error.name(), exception.getMessage()));
  }

}
