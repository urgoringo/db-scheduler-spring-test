package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class TimeShiftSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(TimeShiftSchedulerTest.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Scheduler scheduler;

    private final ConcurrentHashMap<String, Boolean> executionTracker = new ConcurrentHashMap<>();

    @Test
    void shouldExecuteTaskAfterTimeShift() throws Exception {
        // Given: Create a one-time task
        String taskId = "time-shift-test-" + System.currentTimeMillis();
        OneTimeTask<Void> testTask = Tasks
                .oneTime("time-shift-task", Void.class)
                .execute((instance, ctx) -> {
                    executionTracker.put(instance.getId(), true);
                    log.info("✅ Task executed: {}", instance.getId());
                });

        // Create a new scheduler with our test task
        Scheduler testScheduler = Scheduler
                .create(dataSource, testTask)
                .threads(1)
                .pollingInterval(java.time.Duration.ofSeconds(1))
                .build();

        testScheduler.start();

        try {
            // When: Schedule task 2 hours in the future
            Instant twoHoursFromNow = Instant.now().plus(2, ChronoUnit.HOURS);
            testScheduler.schedule(
                    testTask.instance(taskId),
                    twoHoursFromNow
            );

            log.info("📅 Scheduled task '{}' for execution at: {}", taskId, twoHoursFromNow);

            // Wait a bit to ensure task is persisted
            Thread.sleep(500);

            // Verify task is not executed yet
            assertThat(executionTracker.get(taskId)).isNull();
            log.info("✓ Verified task is not executed yet (scheduled for future)");

            // Then: Shift time forward by 2 hours in the database
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            int updatedRows = jdbcTemplate.update(
                    "UPDATE scheduled_tasks SET execution_time = DATEADD('HOUR', -2, execution_time) WHERE task_instance = ?",
                    taskId
            );

            log.info("⏰ Time-shifted {} task(s) by 2 hours into the past", updatedRows);
            assertThat(updatedRows).isEqualTo(1);

            // Verify the task's execution time was updated
            Instant updatedExecutionTime = jdbcTemplate.queryForObject(
                    "SELECT execution_time FROM scheduled_tasks WHERE task_instance = ?",
                    Instant.class,
                    taskId
            );
            log.info("⏱️  Updated execution time: {} (now in the past)", updatedExecutionTime);
            assertThat(updatedExecutionTime).isBefore(Instant.now());

            // Wait for task to be executed (should happen within next polling interval)
            log.info("⏳ Waiting for task to be executed...");
            await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        assertThat(executionTracker.get(taskId))
                                .as("Task should have been executed after time shift")
                                .isTrue();
                    });

            log.info("✓ Task was executed successfully after time shift");

            // Verify task is removed from database after execution
            Integer remainingTasks = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM scheduled_tasks WHERE task_instance = ?",
                    Integer.class,
                    taskId
            );
            assertThat(remainingTasks).isEqualTo(0);
            log.info("✓ Task removed from database after execution");

            log.info("🎉 Test completed successfully!");

        } finally {
            testScheduler.stop();
        }
    }
}
