package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TimeMachine {
    private JdbcTemplate jdbcTemplate;
    private Scheduler scheduler;

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
        scheduler.triggerCheckForDueExecutions();

        waitForTasksToComplete(dueTasksBeforeShift);
    }

    private Map<String, Instant> getDueTasksWithExecutionTimes(Duration duration) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT task_name, task_instance, execution_time
                FROM scheduled_tasks
                WHERE execution_time <= DATEADD('SECOND', ?, NOW())""", duration.getSeconds());

        Map<String, Instant> tasks = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String taskKey = row.get("task_name") + ":" + row.get("task_instance");
            Instant executionTime = ((java.sql.Timestamp) row.get("execution_time")).toInstant();
            tasks.put(taskKey, executionTime);
        }
        return tasks;
    }

    private void waitForTasksToComplete(Map<String, Instant> tasksBeforeShift) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = 30000;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            boolean allCompleted = tasksBeforeShift.keySet().stream()
                    .allMatch(taskKey -> isTaskCompletedOrRescheduled(taskKey, tasksBeforeShift.get(taskKey)));
            if (allCompleted) {
                return;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for tasks to complete", e);
            }
        }

        throw new RuntimeException("Timeout waiting for tasks to complete");
    }

    private boolean isTaskCompletedOrRescheduled(String taskKey, Instant originalExecutionTime) {
        String[] parts = taskKey.split(":", 2);
        String taskName = parts[0];
        String taskInstance = parts[1];

        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT execution_time FROM scheduled_tasks WHERE task_name = ? AND task_instance = ?",
                taskName,
                taskInstance
        );

        if (results.isEmpty()) {
            return true;
        }

        Instant currentExecutionTime = ((java.sql.Timestamp) results.get(0).get("execution_time")).toInstant();
        return currentExecutionTime.isAfter(originalExecutionTime);
    }
}
