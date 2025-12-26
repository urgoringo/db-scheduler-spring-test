package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import com.github.kagkarlsson.scheduler.testhelper.SettableClock;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class TimeShiftSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(TimeShiftSchedulerTest.class);
    private static final ConcurrentHashMap<String, AtomicInteger> executionCounter = new ConcurrentHashMap<>();

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private OneTimeTask<Void> timeShiftTask;

    @Autowired
    private RecurringTask<Void> testRecurringTask;

    @Autowired
    SettableClock clock;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TestConfiguration
    static class TestTaskConfiguration {
        @Bean
        public OneTimeTask<Void> timeShiftTask() {
            return Tasks
                    .oneTime("time-shift-task", Void.class)
                    .execute((instance, ctx) -> {
                        executionCounter.computeIfAbsent(instance.getId(), k -> new AtomicInteger(0)).incrementAndGet();
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
                clock.now().plus(2, ChronoUnit.HOURS)
        );

        shiftTime(Duration.ofHours(2));

        assertThat(executionCounter.get(taskId).get()).isEqualTo(1);

        Integer remainingTasks = queryTaskCount(taskId);
        assertThat(remainingTasks).isEqualTo(0);
    }

    @Test
    void shouldExecuteRecurringTaskAfterTimeShift() {
        String taskId = "recurring-test-" + System.currentTimeMillis();

        scheduler.schedule(
                testRecurringTask.instance(taskId),
                clock.now().plus(15, ChronoUnit.SECONDS)
        );

        Instant executionTimeBeforeShift = queryTaskExecutionTime(jdbcTemplate, taskId);
        assertThat(executionTimeBeforeShift).isAfter(clock.now().plus(10, ChronoUnit.SECONDS));

        shiftTime(Duration.ofSeconds(15));

        assertThat(executionCounter.get(taskId).get())
                .as("Recurring task should have been executed")
                .isEqualTo(1);

        Integer taskCount = queryTaskCount(taskId);
        assertThat(taskCount)
                .as("Recurring task should still exist in database")
                .isEqualTo(1);

        Instant executionTimeAfterShift = queryTaskExecutionTime(jdbcTemplate, taskId);
        assertThat(executionTimeAfterShift)
                .as("Recurring task should be rescheduled for the future")
                .isAfter(executionTimeBeforeShift);
    }

    private void shiftTime(Duration toAdd) {
        clock.tick(toAdd);
        scheduler.triggerCheckForDueExecutions();
        await()
                .pollInterval(Duration.ofMillis(10))
                .until(() -> countDueTasks() == 0);
    }

    @Nullable
    private Integer queryTaskCount(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scheduled_tasks WHERE task_instance = ?",
                Integer.class,
                taskId
        );
    }

    private int countDueTasks() {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM scheduled_tasks
                        WHERE execution_time <= NOW()""",
                Integer.class);
    }

    @Nullable
    private static Instant queryTaskExecutionTime(JdbcTemplate jdbcTemplate, String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT execution_time FROM scheduled_tasks WHERE task_instance = ?",
                Instant.class,
                taskId
        );
    }
}
