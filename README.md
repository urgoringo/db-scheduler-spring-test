# DB-Scheduler Test Application

A Spring Boot 3.5 application demonstrating [db-scheduler](https://github.com/kagkarlsson/db-scheduler) integration.

## Features

- **Spring Boot 3.5.9** with Java 25
- **db-scheduler 16.6.0** - Persistent task scheduling with database-backed storage
- **H2 Database** - In-memory database for quick testing
- **Flyway** - Database migration management
- **Gradle 9.0** - Latest Gradle version with Java 25 support
- **Example Tasks**:
  - Recurring heartbeat task (every 30 seconds)
  - Recurring cleanup task (every 5 minutes)
  - One-time email task (can be scheduled programmatically)

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

## Testing

The project includes comprehensive tests demonstrating db-scheduler functionality:

### Time-Shift Scheduler Test
```bash
./gradlew test --tests TimeShiftSchedulerTest
```

Advanced test that:
1. Schedules a task 2 hours in the future
2. Manipulates the database to shift time forward by 2 hours
3. Verifies the task executes automatically
4. Confirms proper cleanup after execution

This test demonstrates how to test time-dependent scheduler behavior without waiting for real time to pass. The test uses `SettableClock` to manipulate time and verify task execution.

The test covers:
- One-time task execution after time shift
- Recurring task execution and rescheduling after time shift

### Run All Tests
```bash
./gradlew test
```

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

- `spring-boot-starter-data-jdbc` - Database access
- `db-scheduler-spring-boot-starter` - db-scheduler integration
- `flyway-core` - Database migration management
- `h2` - In-memory database
- `awaitility` - Testing async behavior

## Learn More

- [db-scheduler Documentation](https://github.com/kagkarlsson/db-scheduler)
- [Spring Boot 3.5 Documentation](https://docs.spring.io/spring-boot/docs/3.5.9/reference/)
