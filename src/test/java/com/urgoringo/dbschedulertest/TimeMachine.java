package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TimeMachine {
    private final JdbcTemplate jdbcTemplate;
    private final Scheduler scheduler;

    public TimeMachine(JdbcTemplate jdbcTemplate, Scheduler scheduler) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduler = scheduler;
    }

    public void shiftTimeBy(Duration duration) {
        int dueTaskCount = countDueTasks(duration);

        if (dueTaskCount == 0) {
            return;
        }

        jdbcTemplate.update("""
                UPDATE scheduled_tasks
                SET execution_time = NOW()
                WHERE execution_time <= DATEADD('SECOND', ?, NOW())""", duration.getSeconds());

        waitForAllDueTasksToComplete();
    }

    private int countDueTasks(Duration duration) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM scheduled_tasks
                WHERE execution_time <= DATEADD('SECOND', ?, NOW())""",
                Integer.class,
                duration.getSeconds());
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

            int dueTasks = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM scheduled_tasks
                    WHERE execution_time <= NOW()
                    AND picked = FALSE""", Integer.class);

            if (dueTasks == 0) {
                return;
            }
        }

        throw new RuntimeException("Timeout waiting for tasks to complete");
    }
}
