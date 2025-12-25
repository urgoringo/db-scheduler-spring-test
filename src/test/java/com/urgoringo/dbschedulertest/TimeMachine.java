package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TimeMachine {
    private final JdbcTemplate jdbcTemplate;
    private final Scheduler scheduler;

    public TimeMachine(JdbcTemplate jdbcTemplate, Scheduler scheduler) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduler = scheduler;
    }

    public void shiftTimeBy(Duration duration) {
        Map<String, Instant> dueTasksBeforeShift = getDueTasksWithExecutionTimes(duration);

        if (dueTasksBeforeShift.isEmpty()) {
            return;
        }

        jdbcTemplate.update("""
                UPDATE scheduled_tasks
                SET execution_time = NOW()
                WHERE execution_time <= DATEADD('SECOND', ?, NOW())""", duration.getSeconds());

        waitForAllDueTasksToComplete();
    }

    private Map<String, Instant> getDueTasksWithExecutionTimes(Duration duration) {
        return jdbcTemplate.query("""
                SELECT task_name, task_instance, execution_time
                FROM scheduled_tasks
                WHERE execution_time <= DATEADD('SECOND', ?, NOW())""",
                (rs, rowNum) -> {
                    String taskKey = rs.getString("task_name") + ":" + rs.getString("task_instance");
                    Instant executionTime = rs.getTimestamp("execution_time").toInstant();
                    return Map.entry(taskKey, executionTime);
                },
                duration.getSeconds())
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
