# BidMart Authentication Service — Final Design Report

**Module**: Authentication & User Management  
**Group**: B15 (Advanced Programming 2025/2026)  
**Contributor (this document)**: Azka  
**Milestone**: 100% (final)

---

## 1. Problem Statement

BidMart is a real-time auction platform. It requires a centralised authentication and user management microservice that:

- Authenticates users for all other microservices (product listing, bidding, payments)
- Issues short-lived JWT access tokens and long-lived refresh tokens
- Supports TOTP-based two-factor authentication
- Provides session visibility and revocation for users and administrators
- Publishes user lifecycle events to a message broker for downstream consumers

---

## 2. Architecture Overview

### 2.1 Placement in the BidMart Ecosystem

```
Browser / Mobile Client
        │
        ▼
┌───────────────────────────────────────────────┐
│         API Gateway / Reverse Proxy           │
└──────────────────┬────────────────────────────┘
                   │
     ┌─────────────┼──────────────┐
     ▼             ▼              ▼
┌─────────┐  ┌──────────┐  ┌──────────┐
│  Auth   │  │  Bidding │  │ Product  │
│ Service │  │  Service │  │ Service  │
│ :8081   │  │  :8082   │  │ :8083    │
└────┬────┘  └──────────┘  └──────────┘
     │
     ├──► PostgreSQL (auth DB)
     └──► RabbitMQ (event bus)
```

Other services validate the JWT access token they receive from the gateway by verifying the signature against the shared secret, without contacting this service for every request.

### 2.2 Internal Architecture

```
HTTP Request
    │
    ▼
JwtAuthFilter               (extracts & validates Bearer token)
    │
    ▼
DispatcherServlet
    │
    ├──► AuthController      POST /api/auth/**
    ├──► UserController      GET  /api/users/me
    ├──► SessionController   GET/DELETE /api/auth/sessions/**
    ├──► AdminController     POST /api/admin/**
    └──► HomeController      GET /
          │
          ▼
       Services
          ├── AuthService          (registration, login, password reset, TOTP)
          ├── JwtService           (token generation and validation)
          ├── RefreshTokenService  (rotation, reuse detection, sessions)
          ├── TotpService          (TOTP secret and code verification)
          ├── RateLimitService     (per-IP login rate limiting)
          └── UserEventPublisher   (RabbitMQ or logging)
                    │
                    ▼
               Repositories (Spring Data JPA)
                    │
                    ▼
              PostgreSQL (prod) / H2 (test)
```

---

## 3. Data Model

### Entity Relationship

```
users
  ├─ id (UUID, PK)
  ├─ email (unique)
  ├─ username
  ├─ password_hash (BCrypt)
  ├─ enabled (email verification flag)
  ├─ locked (admin lockout flag)
  ├─ role (BUYER | ADMIN)
  ├─ totp_secret (nullable)
  ├─ totp_enabled
  └─ created_at

refresh_tokens
  ├─ id (UUID, PK)
  ├─ token_hash (SHA-256 of raw token, unique)
  ├─ user_id (FK → users)
  ├─ expires_at
  ├─ created_at
  ├─ family_id (UUID, groups tokens from same login)
  ├─ revoked (rotation flag)
  ├─ revoked_at
  ├─ device_info
  └─ ip_address

verification_tokens
  ├─ id (UUID, PK)
  ├─ token (raw UUID, unique)
  ├─ user_id (FK → users)
  ├─ expires_at
  └─ used

password_reset_tokens
  ├─ id (UUID, PK)
  ├─ token_hash (SHA-256)
  ├─ user_id (FK → users)
  ├─ expires_at
  └─ used
```

Flyway manages schema evolution (V1–V9). Each migration adds columns in a backward-compatible way. Schema changes in production are applied on startup.

---

## 4. Key Design Decisions

### 4.1 Stateless JWT Architecture

**Decision**: Use short-lived JWT access tokens (24 h) validated locally by each service.

**Rationale**: A centralised session store would make the auth service a synchronous dependency for every request across all microservices. JWT validation is a local CPU operation with no network round-trip.

**Trade-off**: Tokens cannot be revoked before expiry. Mitigated by keeping the TTL short (24 h) and using the refresh token rotation system to detect compromised sessions.

### 4.2 Refresh Token Rotation with Reuse Detection

**Decision**: Every use of a refresh token rotates it (old → revoked, new issued). Replaying a revoked token revokes the entire family.

**Rationale**: Refresh token theft is a high-risk attack vector. Rotation limits the window of use for a stolen token to a single request. Reuse detection provides an automatic response when a rotated token is replayed — a strong signal that the token has been stolen.

**Implementation**: The `familyId` UUID groups all rotated tokens from a single login. `revokeAllByFamilyId` sets `revoked=true` and `revokedAt=now()` on all of them. Revoked tokens are not deleted immediately — they must remain in the database to serve as the reuse-detection sentinel.

### 4.3 SHA-256 Token Hashing

**Decision**: Store only the SHA-256 hash of refresh tokens, verification tokens, and password reset tokens.

**Rationale**: If the database is compromised, raw tokens cannot be extracted and replayed. Only the server needs the hash — the client holds the raw token. This is analogous to how passwords are hashed.

### 4.4 TOTP Two-Factor Authentication

**Decision**: Use time-based OTP (RFC 6238) with a server-side secret per user rather than SMS or email OTP.

**Rationale**: TOTP does not require email or SMS infrastructure, is phishing-resistant, and is supported by widely available authenticator apps (Google Authenticator, Authy, Bitwarden).

**Flow**:
1. Setup generates a secret stored in the database and returns an `otpauth://` URI for QR scanning.
2. Confirmation verifies a code against the secret before enabling 2FA — ensures the user successfully scanned and configured the app.
3. Login with 2FA active returns a 5-minute MFA token (not an access token). The MFA token carries a `scope=mfa` claim that is rejected by `JwtAuthFilter`, preventing its use as an access token.

### 4.5 Event-Driven Architecture (Optional RabbitMQ)

**Decision**: Decouple event publishing behind a `UserEventPublisher` interface with two implementations: logging (default) and RabbitMQ.

**Rationale**: In development and testing, the RabbitMQ dependency is costly to set up. The `LoggingUserEventPublisher` satisfies the interface with zero infrastructure. In production (docker-compose), setting `RABBITMQ_ENABLED=true` activates the real publisher without code changes.

**Events published**: `user.registered`, `user.email.verified`, `user.logged.in`, `user.password.reset`, `user.disabled`

### 4.6 Rate Limiting (Bucket4j In-Memory)

**Decision**: Token bucket per IP address in a `ConcurrentHashMap`, checked before the authentication attempt.

**Rationale**: In-memory rate limiting is low-latency and requires no additional infrastructure for a single-instance deployment. For multi-instance deployments, the Bucket4j Redis/Hazelcast adapters can replace the `ConcurrentHashMap` with zero changes to `RateLimitService`.

**Decision**: Rate-limit before calling `AuthenticationManager.authenticate()`.

**Rationale**: This ensures that even a successful login consumes a token, preventing attackers from inferring valid credentials by checking which requests are blocked.

---

## 5. API Design

RESTful JSON API with JWT Bearer authentication. Full specification: [openapi.yaml](src/main/resources/static/openapi.yaml)

### Naming conventions
- POST for all mutation operations (login, logout, password reset, 2FA setup/confirm/disable)
- GET for read operations (sessions, user info)
- DELETE for revocation operations (sessions)

### Error response format

All errors return a JSON body:

```json
{ "error": "human-readable message" }
```

### Status codes used
- `201 Created` — registration
- `200 OK` — all other successes
- `400 Bad Request` — invalid input (wrong TOTP code, invalid token)
- `401 Unauthorized` — missing/invalid JWT or wrong credentials
- `403 Forbidden` — email not verified, account locked, or insufficient role
- `404 Not Found` — resource not found (session ID, user ID)
- `409 Conflict` — email already registered, 2FA setup conflict
- `410 Gone` — verification token expired
- `429 Too Many Requests` — rate limit exceeded

---

## 6. Test Strategy

### Unit Tests (Mockito)

Service-layer unit tests mock all dependencies (repositories, external libraries) and test business logic in isolation:

- `JwtServiceTest` — token generation, email extraction, MFA scope enforcement
- `RateLimitServiceTest` — bucket capacity, per-IP isolation
- `TotpServiceTest` — secret generation, OTP URL format, invalid code rejection
- `RefreshTokenServiceTest` — rotation, reuse detection, session mapping
- `UserDetailsServiceImplTest` — user loading, role mapping, disabled/locked flags
- `HomeControllerTest` — model population

### Integration Tests (Spring Boot + H2 + MockMvc)

Full Spring context tests exercise the HTTP layer through the security filter chain, using H2 for the database:

- `AuthControllerIntegrationTest` — register/verify/login/logout/forgot/reset
- `RefreshTokenIntegrationTest` — rotation, expiry, reuse detection
- `TotpIntegrationTest` — setup, confirm, login with MFA, disable
- `SessionIntegrationTest` — list, single revoke, revoke all
- `RateLimitIntegrationTest` — 429 after limit, different IPs isolated
- `AdminControllerIntegrationTest` — disable user, permission enforcement

### Test Isolation

Each integration test class is annotated with `@DirtiesContext(AFTER_CLASS)` to ensure a clean application context (and therefore clean rate-limit buckets and database state) between classes.

---

## 7. CI/CD Pipeline

### Continuous Integration

Every push or pull request to `main` and `100percent_milestone` runs:

1. Checkstyle — code style gate
2. Unit + integration tests — correctness gate
3. JaCoCo coverage — visibility into untested code
4. SonarCloud — static analysis (code smells, bugs, security hotspots)
5. OWASP Dependency-Check — CVE scan
6. Trivy container scan — base image vulnerability scan

### Continuous Delivery

On CI success on `main`:

1. Build JAR (tests skipped — already ran in CI)
2. Docker build and push to Docker Hub (tagged `latest` and by git SHA)
3. SSH to EC2, pull new image, restart container with env-var injection

The CD pipeline is a "dummy" deploy in the sense that it requires secrets (`DOCKERHUB_USERNAME`, `EC2_HOST`, etc.) to be configured in GitHub repository secrets before it becomes fully operational. The pipeline definition is complete and verified to trigger; actual deployment activation is gated by the infrastructure team providing the secrets.

---

## 8. Technology Choices

| Technology | Version | Reason |
|---|---|---|
| Spring Boot | 4.0.3 | Latest stable; Jakarta EE namespace |
| Spring Security | 7.x (via Boot) | Industry-standard, integrates with JWT filter cleanly |
| jjwt | 0.12.6 | JJWT modern API (0.12+), no deprecated methods |
| Flyway | 10.x | Schema version control, compatible with Spring Boot 4 |
| dev.samstevens.totp | 1.7.1 | RFC 6238 compliant, minimal dependency footprint |
| Bucket4j | 8.10.1 | Pure Java token bucket, no Redis required for single instance |
| SpringDoc OpenAPI | 2.8.6 | Swagger UI served from the app, spec auto-served at `/v3/api-docs` |
| PostgreSQL | 16 | Production database |
| H2 | — | In-memory database for tests (no external dependency) |
| RabbitMQ | 3.13 | AMQP message broker for user lifecycle events |

---

## 9. Milestones Summary

| Milestone | Feature Set |
|---|---|
| 25% | Project setup, Spring Boot skeleton, User entity, HomeController |
| 50% | Registration, email verification, login (JWT), logout, password reset, AdminController, refresh token rotation |
| 75% | TOTP 2FA, refresh token reuse detection, login rate limiting, session management, RabbitMQ event publishing, CI with SonarCloud + OWASP + Trivy |
| 100% | Complete unit tests (service layer), full README + OpenAPI, security hardening checklist, demo instructions, final design report, CD pipeline verified |
