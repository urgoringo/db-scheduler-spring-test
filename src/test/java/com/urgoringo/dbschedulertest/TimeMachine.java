package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.event.AbstractSchedulerListener;
import com.github.kagkarlsson.scheduler.task.ExecutionComplete;
import com.github.kagkarlsson.scheduler.testhelper.SettableClock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;

@Component
public class TimeMachine {

    private final SettableClock clock;
    private final Scheduler scheduler;
    private final JdbcTemplate jdbcTemplate;
    private final AtomicInteger pendingCompletions = new AtomicInteger(0);
    private final ExecutionTrackingListener executionListener = new ExecutionTrackingListener();

    public TimeMachine(SettableClock clock, Scheduler scheduler, JdbcTemplate jdbcTemplate) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.scheduler.registerSchedulerListener(executionListener);
        this.jdbcTemplate = jdbcTemplate;
    }

    public void shiftTime(Duration toAdd) {
        clock.tick(toAdd);

        scheduler.triggerCheckForDueExecutions();

        await()
                .pollInterval(Duration.ofMillis(10))
                .until(() -> countDueTasks(clock.now()) == 0);
    }

    public Instant now() {
        return clock.now();
    }

    private int countDueTasks(Instant now) {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM scheduled_tasks
                        WHERE execution_time <= ?""",
                Integer.class,
                now
        );
    }

    private class ExecutionTrackingListener extends AbstractSchedulerListener {
        @Override
        public void onExecutionComplete(ExecutionComplete executionComplete) {
            pendingCompletions.updateAndGet(count -> Math.max(0, count - 1));
        }
    }
}
