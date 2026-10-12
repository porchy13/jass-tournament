package ch.varani.jass.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock of the tests that stands still until the test moves it forward, e.g. to follow a countdown.
 *
 * <p>Thread-safe: the instant is read by the JavaFX Application Thread and moved by the test thread.</p>
 */
final class MutableClock extends Clock {

  /** The current instant. */
  private volatile Instant instant;

  /**
   * Creates a clock standing at an instant, in UTC.
   *
   * @param start the instant.
   */
  MutableClock(final Instant start) {
    super();
    this.instant = start;
  }

  /**
   * Moves the clock forward.
   *
   * @param time the time elapsed.
   */
  void advance(final Duration time) {
    instant = instant.plus(time);
  }

  /**
   * Returns the time zone of the clock: UTC.
   *
   * @return UTC.
   */
  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  /**
   * Returns this clock, whatever the zone: the tests use UTC only.
   *
   * @param zone the time zone.
   *
   * @return this clock.
   */
  @Override
  public Clock withZone(final ZoneId zone) {
    return this;
  }

  /**
   * Returns the current instant.
   *
   * @return the instant.
   */
  @Override
  public Instant instant() {
    return instant;
  }

}
