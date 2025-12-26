package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Clock;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Test to verify whether db-scheduler uses the provided Clock or database NOW()
 * 
 * If db-scheduler uses Clock: shifting the Clock will cause tasks to execute
 * If db-scheduler uses database NOW(): shifting the Clock won't work, we'd need to manipulate DB
 */
@SpringBootTest
class ClockVsDatabaseNowTest {

    private static final AtomicBoolean taskExecuted = new AtomicBoolean(false);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private OneTimeTask<Void> testTask;

    @Autowired
    Clock clock;

    @TestConfiguration
    static class TestTaskConfiguration {
        @Bean
        public Clock testClock() {
            return new TestClockAdapter();
        }

        @Bean
        public OneTimeTask<Void> testTask() {
            return Tasks
                    .oneTime("clock-test-task", Void.class)
                    .execute((instance, ctx) -> {
                        taskExecuted.set(true);
                    });
        }
    }

    @Test
    void verifyDbSchedulerUsesClockNotDatabaseNow() {
        // Reset execution flag
        taskExecuted.set(false);
        
        TestClockAdapter clockAdapter = (TestClockAdapter) clock;
        TestClock testClock = clockAdapter.getTestClock();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Get current time from Clock
        Instant clockNow = testClock.instant();
        
        // Schedule task 1 hour in the future (using Clock time)
        String taskId = "clock-test-" + System.currentTimeMillis();
        Instant scheduledTime = clockNow.plus(1, ChronoUnit.HOURS);
        
        scheduler.schedule(
                testTask.instance(taskId),
                scheduledTime
        );
        
        // Verify task is in database with future execution_time
        Instant dbExecutionTime = jdbcTemplate.queryForObject(
                "SELECT execution_time FROM scheduled_tasks WHERE task_instance = ?",
                Instant.class,
                taskId
        );
        assertThat(dbExecutionTime).isAfter(clockNow);
        
        // Get database NOW() - this should be real time, not Clock time
        Instant dbNow = jdbcTemplate.queryForObject(
                "SELECT NOW()",
                Instant.class
        );
        
        // Shift Clock forward by 1 hour
        testClock.shiftTimeBy(Duration.ofHours(1));
        Instant clockAfterShift = testClock.instant();
        
        // Verify Clock has shifted
        assertThat(clockAfterShift).isAfter(clockNow.plus(59, ChronoUnit.MINUTES));
        
        // Trigger scheduler to check for due tasks
        scheduler.triggerCheckForDueExecutions();
        
        // Wait for task execution
        await()
                .untilAsserted(() -> {
                    assertThat(taskExecuted.get())
                            .as("Task should execute when Clock is shifted, proving db-scheduler uses Clock, not database NOW()")
                            .isTrue();
                });
        
        // If we get here, db-scheduler is using the Clock!
        // If db-scheduler used database NOW(), the task wouldn't execute because
        // the database time hasn't changed, only the Clock has.
    }
}
