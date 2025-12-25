package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class DbSchedulerTimeMachine {
    private final JdbcTemplate jdbcTemplate;
    private final Scheduler scheduler;

    public DbSchedulerTimeMachine(JdbcTemplate jdbcTemplate, Scheduler scheduler) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduler = scheduler;
    }

    public void shiftTimeBy(Duration duration) {
        if (countDueTasks(duration) == 0) {
            return;
        }

        updateExecutionTimeForTasksThatWereScheduledWithin(duration);
        waitForAllDueTasksToComplete();
    }

    private void waitForAllDueTasksToComplete() {
        long startTime = System.currentTimeMillis();
        long timeoutMs = 30000;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            scheduler.triggerCheckForDueExecutions();

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for tasks to complete", e);
            }

            if (countDueTasks() == 0) {
                return;
            }
        }

        throw new RuntimeException("Timeout waiting for tasks to complete");
    }

    private int countDueTasks() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM scheduled_tasks
                WHERE execution_time <= NOW()
                AND picked = FALSE""", Integer.class);
    }

    private void updateExecutionTimeForTasksThatWereScheduledWithin(Duration duration) {
        jdbcTemplate.update("""
                UPDATE scheduled_tasks
                SET execution_time = NOW()
                WHERE execution_time <= DATEADD('SECOND', ?, NOW())""", duration.getSeconds());
    }

    private int countDueTasks(Duration duration) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM scheduled_tasks
                WHERE execution_time <= DATEADD('SECOND', ?, NOW())""",
                Integer.class,
                duration.getSeconds());
    }

}
