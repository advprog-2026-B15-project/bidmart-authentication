# BidMart Authentication Service

This is the Authentication & User Management module for BidMart, a real-time auction platform. This repo is part of a group project for Advanced Programming course.

## What you need

- Java 21
- Docker + Docker Compose (if you want to run via Docker)

> If you just want to run the tests, you don't need PostgreSQL at all — tests use H2 in-memory database.

## How to run

### The easy way — Docker Compose

This spins up both the app and a PostgreSQL database, no extra setup needed.

```bash
docker compose up --build
```

Then open http://localhost:8080

To stop:
```bash
docker compose down
```

To stop and also delete the database data:
```bash
docker compose down -v
```

### Without Docker (you need PostgreSQL installed locally)

First, set up the database:
```sql
CREATE DATABASE bidmart_auth;
CREATE USER bidmart WITH PASSWORD 'bidmart123';
GRANT ALL PRIVILEGES ON DATABASE bidmart_auth TO bidmart;
```

Then run:
```bash
./gradlew bootRun
```

If your PostgreSQL uses different credentials, you can override them:
```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/bidmart_auth \
DATABASE_USERNAME=bidmart \
DATABASE_PASSWORD=bidmart123 \
./gradlew bootRun
```

## Running tests

```bash
./gradlew test
```

No database needed, it uses H2 automatically.

## Code quality checks

```bash
# Check code style
./gradlew checkstyleMain checkstyleTest

# Generate coverage report (output: build/reports/jacoco/test/html/index.html)
./gradlew jacocoTestReport
```

## CI/CD

There are two GitHub Actions pipelines:

- **CI** — runs on every push/PR to `main`. Runs Checkstyle, then tests, then generates a coverage report.
- **CD** — only runs if CI passes. Builds the JAR and Docker image. The actual deploy step is a placeholder for now.

## Project structure

```
src/
├── main/
│   ├── java/.../
│   │   ├── config/SecurityConfig.java       # Spring Security setup
│   │   ├── controller/HomeController.java   # Homepage endpoint (shows DB status)
│   │   ├── model/User.java                  # User entity
│   │   └── repository/UserRepository.java  # JPA repository for User
│   └── resources/
│       ├── application.properties           # DB config (reads from env vars)
│       └── templates/index.html             # Simple status page
└── test/
    ├── java/.../
    │   ├── BidmartAuthenticationApplicationTests.java  # Spring context load test
    │   └── controller/HomeControllerTest.java          # Unit tests for HomeController
    └── resources/
        └── application-test.properties      # Overrides DB config to use H2 for tests
```
