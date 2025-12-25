package com.urgoringo.dbschedulertest;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class TimeShiftSchedulerTest {

    private static final Logger log = LoggerFactory.getLogger(TimeShiftSchedulerTest.class);
    private static final ConcurrentHashMap<String, Boolean> executionTracker = new ConcurrentHashMap<>();

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private OneTimeTask<Void> timeShiftTask;

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
                        log.info("✅ Task executed: {}", instance.getId());
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
}
