package com.urgoringo.dbschedulertest.scheduler;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TaskSchedulerService {
    
    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerService.class);
    
    private final Scheduler scheduler;
    private final OneTimeTask<String> emailTask;

    public TaskSchedulerService(Scheduler scheduler, OneTimeTask<String> emailTask) {
        this.scheduler = scheduler;
        this.emailTask = emailTask;
    }

    public void scheduleEmail(String emailAddress, Instant executionTime) {
        scheduler.schedule(
                emailTask.instance(
                        "email-" + System.currentTimeMillis(),
                        emailAddress
                ),
                executionTime
        );
        log.info("Scheduled email to {} at {}", emailAddress, executionTime);
    }
}
