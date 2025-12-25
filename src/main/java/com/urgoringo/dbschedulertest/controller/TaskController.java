package com.urgoringo.dbschedulertest.controller;

import com.urgoringo.dbschedulertest.scheduler.TaskSchedulerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskSchedulerService taskSchedulerService;

    public TaskController(TaskSchedulerService taskSchedulerService) {
        this.taskSchedulerService = taskSchedulerService;
    }

    @PostMapping("/schedule-email")
    public ResponseEntity<Map<String, String>> scheduleEmail(
            @RequestParam String email,
            @RequestParam(required = false) Integer delaySeconds
    ) {
        Instant executionTime = Instant.now().plus(
                delaySeconds != null ? delaySeconds : 10,
                ChronoUnit.SECONDS
        );
        
        taskSchedulerService.scheduleEmail(email, executionTime);
        
        return ResponseEntity.ok(Map.of(
                "message", "Email scheduled successfully",
                "email", email,
                "scheduledAt", executionTime.toString()
        ));
    }
}
