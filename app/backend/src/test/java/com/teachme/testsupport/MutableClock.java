package com.teachme.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** テストで、時刻を進められる時計。処理中の印の期限や、期限の判定を確かめるために使う。 */
public final class MutableClock extends Clock {

  private final ZoneId zone;
  private Instant now;

  public MutableClock(Instant start, ZoneId zone) {
    this.now = start;
    this.zone = zone;
  }

  public void advance(Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("テストの時計は、地域を変えられない");
  }

  @Override
  public Instant instant() {
    return now;
  }
}
