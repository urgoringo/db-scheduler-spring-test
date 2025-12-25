package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class TimeShiftSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(TimeShiftSchedulerTest.class);
    private static final ConcurrentHashMap<String, Boolean> executionTracker = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> executionCounter = new ConcurrentHashMap<>();

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private OneTimeTask<Void> timeShiftTask;

    @Autowired
    private RecurringTask<Void> testRecurringTask;

    @Autowired
    TimeMachine timeMachine;

    @TestConfiguration
    static class TestTaskConfiguration {
        @Bean
        public OneTimeTask<Void> timeShiftTask() {
            return Tasks
                    .oneTime("time-shift-task", Void.class)
                    .execute((instance, ctx) -> {
                        executionTracker.put(instance.getId(), true);
                        log.info("Task executed: {}", instance.getId());
                    });
        }

        @Bean
        public RecurringTask<Void> testRecurringTask() {
            return Tasks
                    .recurring("test-recurring-task", FixedDelay.of(Duration.ofHours(1)))
                    .execute((instance, ctx) -> {
                        String taskId = instance.getId();
                        executionCounter.computeIfAbsent(taskId, k -> new AtomicInteger(0)).incrementAndGet();
                        executionTracker.put(taskId, true);
                        log.info("Recurring task executed: {} (count: {})", 
                                taskId, 
                                executionCounter.get(taskId).get());
                    });
        }
    }

    @Test
    void shouldExecuteTaskAfterTimeShift() {
        String taskId = "time-shift-test-" + System.currentTimeMillis();
        
        scheduler.schedule(
                timeShiftTask.instance(taskId),
                Instant.now().plus(2, ChronoUnit.HOURS)
        );

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        timeMachine.shiftTimeBy(Duration.ofHours(2));

        await()
                .untilAsserted(() -> {
                    assertThat(executionTracker.get(taskId))
                            .isTrue();
                });

        Integer remainingTasks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scheduled_tasks WHERE task_instance = ?",
                Integer.class,
                taskId
        );
        assertThat(remainingTasks).isEqualTo(0);
    }

    @Test
    void shouldExecuteRecurringTaskAfterTimeShift() {
        String taskId = "recurring-test-" + System.currentTimeMillis();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        scheduler.schedule(
                testRecurringTask.instance(taskId),
                Instant.now().plus(15, ChronoUnit.SECONDS)
        );

        Instant executionTimeBeforeShift = jdbcTemplate.queryForObject(
                "SELECT execution_time FROM scheduled_tasks WHERE task_instance = ?",
                Instant.class,
                taskId
        );
        assertThat(executionTimeBeforeShift).isAfter(Instant.now().plus(10, ChronoUnit.SECONDS));

        timeMachine.shiftTimeBy(Duration.ofSeconds(15));

        await()
                .untilAsserted(() -> {
                    assertThat(executionTracker.get(taskId))
                            .as("Recurring task should have been executed")
                            .isTrue();
                });

        assertThat(executionCounter.get(taskId).get())
                .as("Task should have been executed exactly once")
                .isEqualTo(1);

        Integer taskCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scheduled_tasks WHERE task_instance = ?",
                Integer.class,
                taskId
        );
        assertThat(taskCount)
                .as("Recurring task should still exist in database")
                .isEqualTo(1);

        Instant executionTimeAfterShift = jdbcTemplate.queryForObject(
                "SELECT execution_time FROM scheduled_tasks WHERE task_instance = ?",
                Instant.class,
                taskId
        );
        assertThat(executionTimeAfterShift)
                .as("Recurring task should be rescheduled for the future")
                .isAfter(executionTimeBeforeShift);
    }
}
