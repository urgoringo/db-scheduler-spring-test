package com.urgoringo.dbschedulertest;

import java.time.*;

public class TestClock extends Clock {
    private Instant instant = Instant.now();
    private final ZoneId zoneId = ZoneId.of("UTC");

    @Override
    public ZoneId getZone() {
        return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("TestClock does not support withZone");
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void frozenOn(LocalDate date) {
        this.instant = date.atStartOfDay(zoneId).toInstant();
    }

    public void frozenAt(Instant instant) {
        this.instant = instant;
    }

    public void shiftTimeBy(Duration duration) {
        this.instant = instant.plus(duration);
    }
}
