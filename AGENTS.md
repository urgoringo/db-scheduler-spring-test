# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

db-scheduler-test is a Spring Boot 3.5 application demonstrating [db-scheduler](https://github.com/kagkarlsson/db-scheduler) integration. It uses:
- **Java 25** (with toolchain configured in build.gradle)
- **Spring Boot 3.5.9** with Spring Data JDBC
- **db-scheduler 16.6.0** - Persistent task scheduling with database-backed storage
- **H2 Database** - In-memory database for quick testing
- **Flyway** - Database migration management
- **JUnit 5** for testing
- **Awaitility** for testing async behavior

## Build Commands

### Building the Application
```bash
./gradlew build          # Full build with tests
./gradlew assemble       # Build without running tests
./gradlew clean build    # Clean build from scratch
```

### Running Tests
```bash
./gradlew test                                    # Run all tests
./gradlew test --tests ClassName                  # Run specific test class
./gradlew test --tests ClassName.methodName       # Run specific test method
```

**Gradle Daemon**: Always use the Gradle daemon (default behavior). Do not use `--no-daemon` flag unless there's a specific reason (e.g., CI/CD environments requiring clean JVM per build). The daemon provides faster builds by reusing JVM processes and keeping compiled classes in memory.

### Running the Application
```bash
./gradlew bootRun        # Run the Spring Boot application
```

The application will start on `http://localhost:8080`

### H2 Console
Access the H2 database console at: `http://localhost:8080/h2-console`
- **JDBC URL**: `jdbc:h2:mem:testdb`
- **Username**: `sa`
- **Password**: (empty)

### Other Useful Commands
```bash
./gradlew bootJar        # Create executable JAR
./gradlew bootBuildImage # Build OCI/Docker image
./gradlew dependencies   # View dependency tree
./gradlew javadoc        # Generate API documentation
```

## Architecture

### db-scheduler Configuration

The project uses **db-scheduler** for persistent task scheduling:

- **Auto-Configuration**: Uses `db-scheduler-spring-boot-starter` for automatic configuration
- **No Manual Configuration Required**: The starter automatically configures the scheduler based on `application.yml` properties
- **Configuration Properties**: Configured in `application.yml` under `db-scheduler:` section
  - `table-name`: `scheduled_tasks` (default)
  - `polling-interval`: 10ms
  - `threads`: 10
  - `immediate-execution-enabled`: true
  - `heartbeat-interval`: 5m
  - `missed-heartbeats-limit`: 6
  - `shutdown-max-wait`: 30s
  - `delete-unresolved-after`: 14d

### Task Types

Tasks are defined as Spring beans in `ScheduledTasksConfiguration`:

1. **Recurring Tasks**: Tasks that execute on a fixed schedule
   - `heartbeatTask()` - Executes every 30 seconds
   - `cleanupTask()` - Executes every 5 minutes

2. **One-Time Tasks**: Tasks that execute once at a specific time
   - `emailTask()` - Can be scheduled programmatically via `TaskSchedulerService`

### Database Layer
- **Flyway** manages database migrations in `src/main/resources/db/migration/`
- Migration naming: `V{version}__{description}.sql` (e.g., `V1__create_scheduled_tasks_table.sql`)
- Hibernate is configured with `ddl-auto: validate` - schema changes MUST be done via Flyway migrations
- Database baseline is automatically created on first migration (`baseline-on-migrate: true`)
- **H2 Database**: In-memory database for development and testing

### Application Structure
- Main package: `com.urgoringo.dbschedulertest`
- Spring Boot application entry point: `DbSchedulerTestApplication.java`
- Configuration: `src/main/resources/application.yml`
- Scheduler configuration: `scheduler/ScheduledTasksConfiguration.java`
- Task scheduling service: `scheduler/TaskSchedulerService.java`

### Code Style

#### Code Comments
- **Generally avoid comments** - Code should be self-explanatory through clear naming and structure
- **Add comments only when code is non-obvious** - Comments should explain *why* something is done, not *what* is done
- Prefer improving code clarity over adding comments
- When comments are necessary, focus on business logic rationale, complex algorithms, or non-obvious workarounds
- JavaDoc comments are acceptable for public APIs, but keep them concise and focused on contract/behavior

## Testing Architecture

### Time-Shift Testing with SettableClock

The project uses **SettableClock** from db-scheduler's test helpers to test time-dependent scheduler behavior:

- **SettableClock**: Allows manipulating time in tests without waiting for real time to pass
- **Test Configuration**: `TestApplicationConfiguration` provides a `SettableClock` bean for tests
- **Time Manipulation**: Use `clock.tick(Duration)` to advance time
- **Trigger Execution**: After time shift, call `scheduler.triggerCheckForDueExecutions()` to process due tasks
- **Awaitility**: Use Awaitility to wait for async task execution to complete

**Example time-shift test pattern:**
```java
@Test
void shouldExecuteTaskAfterTimeShift() {
    String taskId = "test-task-" + System.currentTimeMillis();
    
    // Schedule task 2 hours in the future
    scheduler.schedule(
        timeShiftTask.instance(taskId),
        clock.now().plus(2, ChronoUnit.HOURS)
    );
    
    // Shift time forward by 2 hours
    shiftTime(Duration.ofHours(2));
    
    // Verify task executed
    assertThat(executionCounter.get(taskId).get()).isEqualTo(1);
}

private void shiftTime(Duration toAdd) {
    clock.tick(toAdd);
    scheduler.triggerCheckForDueExecutions();
    await()
        .pollInterval(Duration.ofMillis(10))
        .until(() -> countDueTasks() == 0);
}
```

### Testing Guidelines

#### Integration Testing
- Tests use `@SpringBootTest` to load the full Spring context
- Tests can inject `Scheduler`, tasks, `SettableClock`, and `JdbcTemplate`
- Use `@TestConfiguration` to define test-specific task beans
- Tests should be independent and clean up after themselves

#### Querying Scheduled Tasks
- Use `JdbcTemplate` to query the `scheduled_tasks` table directly
- Common queries:
  - Count tasks: `SELECT COUNT(*) FROM scheduled_tasks WHERE task_instance = ?`
  - Get execution time: `SELECT execution_time FROM scheduled_tasks WHERE task_instance = ?`
  - Count due tasks: `SELECT COUNT(*) FROM scheduled_tasks WHERE execution_time <= ? AND picked = FALSE`
- Always use parameterized queries to prevent SQL injection

#### Async Task Execution
- Use **Awaitility** to wait for async task execution
- Poll at short intervals (e.g., 10ms) for fast test execution
- Wait until conditions are met (e.g., task count reaches expected value, execution counter increments)

**Example with Awaitility:**
```java
await()
    .pollInterval(Duration.ofMillis(10))
    .atMost(Duration.ofSeconds(5))
    .until(() -> executionCounter.get(taskId).get() == 1);
```

## Development Workflow

### Commit Messages

**Keep commit messages concise and focused on the "why", not the "what":**

- **First line**: Short summary (50-70 chars max) describing what changed at a high level
- **Body (optional)**: 1-3 sentences explaining the reasoning or context
- **Avoid**: Repeating implementation details that are visible in the diff
- **Focus**: Business value, architectural decisions, or trade-offs made

**Good examples:**
```
Use db-scheduler auto-configuration and fix test clock usage
```

```
Add time-shift testing for recurring tasks

Enables testing recurring task execution and rescheduling without waiting for real time.
```

**Bad examples:**
```
Add time-shift testing for recurring tasks

- Added TestApplicationConfiguration with SettableClock bean
- Updated TimeShiftSchedulerTest to test recurring tasks
- Added shiftTime helper method
- Updated queryTaskCount to use clock.now()
```
*Too verbose - repeats what's in the diff*

### Adding Database Changes
1. Create new Flyway migration in `src/main/resources/db/migration/`
2. Use sequential versioning: V1, V2, V3, etc.
3. Run tests to validate migration against H2 database
4. Never modify existing migrations once committed

### Adding New Tasks

1. **Define the task** in `ScheduledTasksConfiguration`:
   ```java
   @Bean
   public RecurringTask<Void> myTask() {
       return Tasks
           .recurring("my-task", FixedDelay.of(Duration.ofMinutes(5)))
           .execute((instance, ctx) -> {
               // Task logic here
           });
   }
   ```

2. **Schedule the task** (if one-time) via `TaskSchedulerService` or let recurring tasks start automatically

3. **Test the task** using time-shift testing if time-dependent behavior needs verification

### Testing New Tasks

1. **Create test task bean** in `@TestConfiguration`:
   ```java
   @TestConfiguration
   static class TestTaskConfiguration {
       @Bean
       public OneTimeTask<Void> myTestTask() {
           return Tasks
               .oneTime("my-test-task", Void.class)
               .execute((instance, ctx) -> {
                   // Test task logic
               });
       }
   }
   ```

2. **Inject and use** in test:
   ```java
   @Autowired
   private OneTimeTask<Void> myTestTask;
   
   @Test
   void shouldExecuteMyTask() {
       scheduler.schedule(myTestTask.instance("test-id"), clock.now().plusSeconds(10));
       shiftTime(Duration.ofSeconds(10));
       // Assertions
   }
   ```

## Learn More

- [db-scheduler Documentation](https://github.com/kagkarlsson/db-scheduler)
- [db-scheduler Spring Boot Starter](https://github.com/kagkarlsson/db-scheduler/tree/master/db-scheduler-spring-boot-starter)
- [Spring Boot 3.5 Documentation](https://docs.spring.io/spring-boot/docs/3.5.9/reference/)
- [Awaitility Documentation](https://github.com/awaitility/awaitility)
