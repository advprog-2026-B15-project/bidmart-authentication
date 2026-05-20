# BidMart Authentication Service

Authentication and User Management microservice for **BidMart**, a real-time auction platform. Part of a group project for the Advanced Programming course (2025/2026 — Group B15).

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Features](#features)
- [Environment Variables](#environment-variables)
- [Running Locally](#running-locally)
- [Running Tests](#running-tests)
- [API Reference](#api-reference)
- [Security Design](#security-design)
- [CI/CD Pipeline](#cicd-pipeline)
- [Project Structure](#project-structure)
- [Demo Instructions](#demo-instructions)

---

## Overview

| Property | Value |
|---|---|
| Framework | Spring Boot 4.0.3 |
| Language | Java 21 |
| Port | 8081 |
| Database (prod) | PostgreSQL 16 |
| Database (test) | H2 in-memory |
| Message broker | RabbitMQ 3.13 (optional) |

---

## Architecture

```
┌─────────────────────────────────────────────┐
│             BidMart Auth Service             │
│                  :8081                       │
│                                              │
│  Controllers ──► Services ──► Repositories  │
│       │              │              │        │
│  SecurityConfig  JwtService    PostgreSQL    │
│  JwtAuthFilter   TotpService                 │
│  RabbitMqConfig  RateLimitService            │
│                  RefreshTokenService         │
│                  UserEventPublisher ──► MQ   │
└─────────────────────────────────────────────┘
```

The service is **stateless** — no server-side session. All authentication state lives in JWT access tokens (short-lived, 24 h) and hashed refresh tokens stored in the database.

---

## Features

### Core Authentication
- User registration with email verification (token mocked in response body for development)
- Login with BCrypt password verification
- JWT access token (24 h) + refresh token (7 d) pair
- Logout (revoke refresh token)
- Forgot password / reset password (token mocked in response body)

### Refresh Token Security
- Tokens are **hashed with SHA-256** before storage — raw token never persists
- **Token rotation**: each `/refresh` call issues a new token and marks the old one as revoked
- **Reuse detection**: replaying a rotated token revokes the entire token family, logging out all sessions
- Family-based session grouping tracks device and IP metadata

### TOTP Two-Factor Authentication
- Setup: `POST /api/auth/2fa/setup` → returns TOTP secret + `otpauth://` URI for QR code
- Confirm: `POST /api/auth/2fa/confirm` with a valid code → enables 2FA on the account
- Login flow with 2FA: initial login returns `{ mfaRequired: true, mfaToken: "..." }`; complete with `POST /api/auth/2fa/verify`
- Disable: `POST /api/auth/2fa/disable` (requires current TOTP code)

### Session Management
- `GET /api/auth/sessions` — list all active sessions (non-revoked, non-expired refresh tokens)
- `DELETE /api/auth/sessions/{id}` — revoke a specific session
- `DELETE /api/auth/sessions` — revoke all sessions (sign out everywhere)

### Login Rate Limiting
- 5 attempts per IP per 15 minutes (configurable)
- Returns HTTP 429 when the limit is exceeded
- Both successful and failed login attempts consume the bucket

### Admin
- `POST /api/admin/users/{id}/disable` (ADMIN role only) — locks the account and revokes all sessions

### Event Publishing
- User lifecycle events published to RabbitMQ exchange `bidmart.auth.events`
- Events: `user.registered`, `user.email.verified`, `user.logged.in`, `user.password.reset`, `user.disabled`
- Falls back to logging when RabbitMQ is disabled (`spring.rabbitmq.enabled=false`, the default)

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | — | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/bidmart_auth` |
| `DATABASE_USERNAME` | — | Database user |
| `DATABASE_PASSWORD` | — | Database password |
| `JWT_SECRET` | — | HMAC-SHA key for signing JWTs (min 32 chars) |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP port |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ password |
| `RABBITMQ_ENABLED` | `false` | Set to `true` to enable event publishing |

JWT expiry and rate limit values can be tuned in `application.properties`:

```properties
jwt.expiration=86400000          # access token: 24 h (ms)
jwt.refresh-expiration=604800000 # refresh token: 7 d (ms)
rate-limit.login.max-attempts=5
rate-limit.login.window-seconds=900
```

---

## Running Locally

### Prerequisites

- Java 21
- Docker + Docker Compose (recommended)

### Option 1 — Docker Compose (easiest)

Spins up the app, PostgreSQL, and RabbitMQ with a single command:

```bash
docker compose up --build
```

The app starts at http://localhost:8081. Swagger UI is at http://localhost:8081/swagger-ui.html.

Stop and remove containers:

```bash
docker compose down        # keep database volume
docker compose down -v     # also wipe database
```

### Option 2 — Gradle (requires local PostgreSQL)

```sql
CREATE DATABASE bidmart_auth;
CREATE USER bidmart WITH PASSWORD 'bidmart123';
GRANT ALL PRIVILEGES ON DATABASE bidmart_auth TO bidmart;
```

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/bidmart_auth \
DATABASE_USERNAME=bidmart \
DATABASE_PASSWORD=bidmart123 \
JWT_SECRET=your-secret-key-at-least-32-characters-long \
./gradlew bootRun
```

### Option 3 — Heroku / Procfile

```bash
heroku config:set DATABASE_URL=... JWT_SECRET=...
git push heroku main
```

---

## Running Tests

Tests use H2 in-memory database — no PostgreSQL or RabbitMQ needed.

```bash
./gradlew test
```

Generate a coverage report (HTML output at `build/reports/jacoco/test/html/index.html`):

```bash
./gradlew jacocoTestReport
```

Run code style checks:

```bash
./gradlew checkstyleMain checkstyleTest
```

### Test suite breakdown

| Class | Type | Tests |
|---|---|---|
| `AuthControllerIntegrationTest` | Integration | 9 |
| `RefreshTokenIntegrationTest` | Integration | 9 |
| `TotpIntegrationTest` | Integration | 9 |
| `SessionIntegrationTest` | Integration | 9 |
| `RateLimitIntegrationTest` | Integration | 5 |
| `AdminControllerIntegrationTest` | Integration | 6 |
| `HomeControllerTest` | Unit | 2 |
| `JwtServiceTest` | Unit | 8 |
| `RateLimitServiceTest` | Unit | 4 |
| `TotpServiceTest` | Unit | 5 |
| `RefreshTokenServiceTest` | Unit | 8 |
| `UserDetailsServiceImplTest` | Unit | 5 |
| `BidmartAuthenticationApplicationTests` | Smoke | 1 |

---

## API Reference

Interactive Swagger UI: http://localhost:8081/swagger-ui.html

Raw OpenAPI spec: http://localhost:8081/openapi.yaml

### Quick reference

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register a new user |
| POST | `/api/auth/verify-email?token=` | Public | Verify email address |
| POST | `/api/auth/login` | Public | Login (returns tokens or MFA challenge) |
| POST | `/api/auth/refresh` | Public | Rotate refresh token |
| POST | `/api/auth/logout` | Public | Revoke refresh token |
| POST | `/api/auth/forgot-password` | Public | Request password reset |
| POST | `/api/auth/reset-password` | Public | Reset password with token |
| POST | `/api/auth/2fa/setup` | Bearer | Generate TOTP secret |
| POST | `/api/auth/2fa/confirm` | Bearer | Enable 2FA with code |
| POST | `/api/auth/2fa/verify` | Public | Complete MFA login |
| POST | `/api/auth/2fa/disable` | Bearer | Disable 2FA |
| GET | `/api/auth/sessions` | Bearer | List active sessions |
| DELETE | `/api/auth/sessions/{id}` | Bearer | Revoke one session |
| DELETE | `/api/auth/sessions` | Bearer | Revoke all sessions |
| GET | `/api/users/me` | Bearer | Get current user info |
| POST | `/api/admin/users/{id}/disable` | Bearer (ADMIN) | Disable a user account |

---

## Security Design

### JWT
- Algorithm: HMAC-SHA256
- Access token TTL: 24 hours
- MFA token TTL: 5 minutes (separate `scope=mfa` claim, rejected by regular endpoints)
- Tokens validated on every request via `JwtAuthFilter`

### Refresh Tokens
- Raw token is never stored — only a SHA-256 hex digest
- Rotation-on-use: old token is marked `revoked=true` (not deleted), enabling reuse detection
- Reuse of a revoked token triggers family-wide revocation (all sessions for that device family)

### Password Security
- BCrypt with Spring Security default strength (10 rounds)
- Password reset tokens are also stored as SHA-256 hashes
- Reset tokens expire after 30 minutes and are single-use

### Rate Limiting
- Bucket4j token bucket per IP address (in-memory, `ConcurrentHashMap`)
- Limit: 5 login attempts per 900 seconds
- Returns HTTP 429 with no additional information to avoid timing oracles

### TOTP 2FA
- HMAC-SHA1, 6 digits, 30-second period (RFC 6238 compliant)
- Allows ±1 time period tolerance for clock skew
- Secret stored in the database; cleared on disable

### Input Validation
- Bean Validation (`@Valid`) on all request DTOs
- Email format enforced at registration
- Minimum password length: 8 characters

### SQL Injection Prevention
- All database access through Spring Data JPA with parameterised queries
- No native SQL string concatenation

### Stateless Architecture
- No server-side sessions (`SessionCreationPolicy.STATELESS`)
- CSRF disabled (JWT-based, no cookies)

---

## CI/CD Pipeline

### CI (`ci.yml`) — triggers on push/PR to `main` and `100percent_milestone`

1. **Checkstyle** — enforces code style (max line 120, no star imports, braces required)
2. **Tests** — full unit + integration suite against H2
3. **JaCoCo** — generates XML + HTML coverage reports (uploaded as artifact)
4. **SonarCloud** — static analysis (requires `SONAR_TOKEN`, `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION` secrets)
5. **OWASP Dependency-Check** — CVE scan of transitive dependencies
6. **Trivy** — container image vulnerability scan (CRITICAL + HIGH), results uploaded as SARIF

### CD (`cd.yml`) — triggers when CI succeeds on `main`

1. Build the JAR (`./gradlew build -x test`)
2. Build and push Docker image to Docker Hub (tagged with git SHA and `latest`)
3. SSH deploy to EC2: pull new image, stop old container, start new container

> **Secrets required for CD**: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `EC2_HOST`, `EC2_SSH_KEY`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`

---

## Project Structure

```
src/
├── main/
│   ├── java/.../
│   │   ├── config/
│   │   │   ├── SecurityConfig.java         # Spring Security, JWT filter, CORS
│   │   │   ├── JwtAuthFilter.java          # Extracts and validates Bearer token per request
│   │   │   ├── FlywayConfig.java           # Flyway disabled in test profile
│   │   │   ├── OpenApiConfig.java          # SpringDoc customisation
│   │   │   └── RabbitMqConfig.java         # Exchange / queue declarations
│   │   ├── controller/
│   │   │   ├── AuthController.java         # /api/auth/** endpoints
│   │   │   ├── UserController.java         # /api/users/me
│   │   │   ├── SessionController.java      # /api/auth/sessions/**
│   │   │   ├── AdminController.java        # /api/admin/** (ADMIN role)
│   │   │   └── HomeController.java         # / status page
│   │   ├── service/
│   │   │   ├── AuthService.java            # Registration, login, password reset, TOTP
│   │   │   ├── JwtService.java             # Token generation and validation
│   │   │   ├── RefreshTokenService.java    # Rotation, reuse detection, sessions
│   │   │   ├── TotpService.java            # TOTP secret and code verification
│   │   │   ├── RateLimitService.java       # Bucket4j per-IP login rate limiting
│   │   │   ├── UserDetailsServiceImpl.java # Spring Security user loading
│   │   │   ├── UserEventPublisher.java     # Interface for lifecycle events
│   │   │   ├── LoggingUserEventPublisher.java  # Active when RabbitMQ disabled
│   │   │   └── RabbitMqUserEventPublisher.java # Active when RabbitMQ enabled
│   │   ├── model/
│   │   │   ├── User.java
│   │   │   ├── RefreshToken.java
│   │   │   ├── VerificationToken.java
│   │   │   └── PasswordResetToken.java
│   │   ├── repository/         # Spring Data JPA repositories
│   │   └── dto/                # Request / response DTOs
│   └── resources/
│       ├── application.properties
│       ├── db/migration/       # Flyway V1–V9
│       └── static/openapi.yaml
└── test/
    ├── java/.../
    │   ├── controller/         # Integration tests (BaseIntegrationTest + MockMvc)
    │   └── service/            # Unit tests (Mockito)
    └── resources/
        └── application-test.properties   # H2, Flyway off, RabbitMQ off
```

---

## Demo Instructions

See [DEMO.md](DEMO.md) for a full walkthrough with `curl` commands covering registration, login, 2FA setup, session management, and rate limiting.
