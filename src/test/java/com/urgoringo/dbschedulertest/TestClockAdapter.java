package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Clock;

import java.time.Instant;

public class TestClockAdapter implements Clock {
    private final TestClock testClock;

    public TestClockAdapter() {
        this.testClock = new TestClock();
    }

    @Override
    public Instant now() {
        return testClock.instant();
    }

    public TestClock getTestClock() {
        return testClock;
    }
}
