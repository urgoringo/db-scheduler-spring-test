package com.urgoringo.dbschedulertest.scheduler;

import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class ScheduledTasksConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(ScheduledTasksConfiguration.class);

    @Bean
    public RecurringTask<Void> heartbeatTask() {
        return Tasks
                .recurring("heartbeat-task", FixedDelay.of(Duration.ofSeconds(30)))
                .execute((instance, ctx) -> {
                    log.info("Heartbeat task executed at: {}", Instant.now());
                });
    }

    @Bean
    public RecurringTask<Void> cleanupTask() {
        return Tasks
                .recurring("cleanup-task", FixedDelay.of(Duration.ofMinutes(5)))
                .execute((instance, ctx) -> {
                    log.info("Cleanup task executed at: {}", Instant.now());
                    log.info("Performing cleanup operations...");
                });
    }

    @Bean
    public OneTimeTask<String> emailTask() {
        return Tasks
                .oneTime("send-email-task", String.class)
                .execute((instance, ctx) -> {
                    log.info("Sending email to: {}", instance.getData());
                    log.info("Email sent successfully!");
                });
    }
}
