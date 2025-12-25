# DB-Scheduler Test Application

A Spring Boot 4.0 application demonstrating [db-scheduler](https://github.com/kagkarlsson/db-scheduler) integration.

## Features

- **Spring Boot 4.0.0** with Java 25
- **db-scheduler 16.6.0** - Persistent task scheduling with database-backed storage
- **H2 Database** - In-memory database for quick testing
- **Gradle 9.0** - Latest Gradle version with Java 25 support
- **Example Tasks**:
  - Recurring heartbeat task (every 30 seconds)
  - Recurring cleanup task (every 5 minutes)
  - One-time email task (schedulable via REST API)

## Running the Application

### Prerequisites
- Java 25 or higher
- Gradle (or use the wrapper)

### Start the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

### H2 Console

Access the H2 database console at: `http://localhost:8080/h2-console`

- **JDBC URL**: `jdbc:h2:mem:testdb`
- **Username**: `sa`
- **Password**: (empty)

## API Endpoints

### Schedule an Email Task

```bash
# Schedule email with default 10 second delay
curl -X POST "http://localhost:8080/api/tasks/schedule-email?email=test@example.com"

# Schedule email with custom delay (in seconds)
curl -X POST "http://localhost:8080/api/tasks/schedule-email?email=test@example.com&delaySeconds=30"
```

## Testing

The project includes comprehensive tests demonstrating db-scheduler functionality:

### Basic Context Test
```bash
./gradlew test --tests DbSchedulerTestApplicationTests
```

Verifies the Spring application context loads correctly with all db-scheduler beans.

### Time-Shift Scheduler Test
```bash
./gradlew test --tests TimeShiftSchedulerTest
```

Advanced test that:
1. Schedules a task 2 hours in the future
2. Manipulates the database to shift time forward by 2 hours
3. Verifies the task executes automatically
4. Confirms proper cleanup after execution

This test demonstrates how to test time-dependent scheduler behavior without waiting for real time to pass. See [TIME_SHIFT_TEST.md](TIME_SHIFT_TEST.md) for detailed documentation.

**Test execution time**: ~1.3 seconds

### Run All Tests
```bash
./gradlew test
```

## db-scheduler Configuration

Configuration in `application.yml`:

- **Table Name**: `scheduled_tasks`
- **Polling Interval**: 10 seconds
- **Thread Pool Size**: 10 threads
- **Immediate Execution**: Enabled

## Task Types

### Recurring Tasks

Defined in `ScheduledTasksConfiguration`:

1. **Heartbeat Task** - Executes every 30 seconds
2. **Cleanup Task** - Executes every 5 minutes

### One-Time Tasks

Defined in `TaskSchedulerService`:

1. **Email Task** - Sends an email at a specific time (demo)

## Database Schema

db-scheduler automatically creates the `scheduled_tasks` table to store:

- Task instances
- Execution times
- Task data (serialized)
- Execution status

## Building the Project

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Create executable JAR
./gradlew bootJar
```

## Dependencies

Key dependencies:

- `spring-boot-starter-web` - REST API
- `spring-boot-starter-data-jpa` - Database access
- `db-scheduler-spring-boot-starter` - db-scheduler integration
- `h2` - In-memory database

## Learn More

- [db-scheduler Documentation](https://github.com/kagkarlsson/db-scheduler)
- [Spring Boot 4.0 Documentation](https://docs.spring.io/spring-boot/docs/4.0.0/reference/)
