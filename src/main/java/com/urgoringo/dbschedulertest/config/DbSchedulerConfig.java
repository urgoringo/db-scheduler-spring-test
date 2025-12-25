package com.urgoringo.dbschedulertest.config;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

@Configuration
public class DbSchedulerConfig {

    @Bean
    public Scheduler scheduler(DataSource dataSource, List<Task<?>> allTasks) {
        return Scheduler
                .create(dataSource, allTasks)
                .pollingInterval(Duration.ofSeconds(10))
                .threads(10)
                .enableImmediateExecution()
                .build();
    }
}
