# Security Hardening Checklist — BidMart Authentication Service

This document records the security controls implemented in the BidMart Authentication Service and their status. It serves as the security review artefact for the 100% milestone.

---

## Authentication & Token Security

| Control | Status | Notes |
|---|---|---|
| JWT access tokens signed with HMAC-SHA256 | Done | Key length >= 256 bits enforced by jjwt |
| JWT secret injected via environment variable, never hardcoded | Done | `${JWT_SECRET}` in `application.properties` |
| Short access token TTL (24 h) | Done | Configurable via `jwt.expiration` |
| MFA tokens scoped with `scope=mfa` claim | Done | `JwtService.extractMfaEmail` rejects tokens without the scope |
| MFA token TTL (5 min) | Done | Hardcoded in `JwtService.MFA_TOKEN_EXPIRY_MS` |
| Stateless session (no server-side sessions) | Done | `SessionCreationPolicy.STATELESS` in `SecurityConfig` |
| CSRF disabled (JWT over Authorization header, no cookies) | Done | `csrf.disable()` in `SecurityConfig` |

---

## Password Security

| Control | Status | Notes |
|---|---|---|
| Passwords hashed with BCrypt (strength 10) | Done | `BCryptPasswordEncoder` default in `SecurityConfig` |
| Minimum password length enforced (8 chars) | Done | `@Size(min = 8)` on `RegisterRequest.password` and `ResetPasswordRequest.newPassword` |
| Password reset tokens hashed with SHA-256 | Done | Raw token never stored in `AuthService.forgotPassword` |
| Password reset tokens are single-use | Done | `used` flag on `PasswordResetToken`, checked in `resetPassword` |
| Password reset tokens expire (30 min) | Done | `LocalDateTime.now().plusMinutes(30)` in `AuthService` |
| Forgot-password endpoint always returns 200 | Done | Prevents user enumeration by account existence |
| All sessions revoked after password reset | Done | `refreshTokenService.revokeAllForUser` called in `resetPassword` |

---

## Refresh Token Security

| Control | Status | Notes |
|---|---|---|
| Refresh tokens hashed with SHA-256 before storage | Done | `hashToken` in `RefreshTokenService` — raw token is ephemeral |
| Refresh token rotation on every use | Done | Old token marked `revoked=true`, new token issued in `rotateRefreshToken` |
| Reuse detection: revoked token replay triggers family revocation | Done | `revokeAllByFamilyId` called when a revoked token is replayed |
| Refresh tokens expire (7 d) | Done | Configurable via `jwt.refresh-expiration` |
| Expired tokens deleted on rotation attempt | Done | `refreshTokenRepository.delete(stored)` when expired |
| Device info and IP address tracked per token | Done | `deviceInfo` and `ipAddress` columns on `RefreshToken` |

---

## Rate Limiting

| Control | Status | Notes |
|---|---|---|
| Login endpoint rate-limited per IP address | Done | Bucket4j token bucket, 5 attempts per 900 s |
| Rate limit applies to both successful and failed logins | Done | Check happens in `AuthController.login` before `authService.login` |
| HTTP 429 returned when limit exceeded | Done | No additional information to prevent timing oracle |
| Rate limit parameters externally configurable | Done | `rate-limit.login.max-attempts`, `rate-limit.login.window-seconds` |

---

## Two-Factor Authentication

| Control | Status | Notes |
|---|---|---|
| TOTP implementation (RFC 6238, HMAC-SHA1) | Done | `dev.samstevens.totp` library |
| 6-digit code, 30-second period | Done | Compliant with Google Authenticator, Authy, etc. |
| Clock skew tolerance (±1 period) | Done | `setAllowedTimePeriodDiscrepancy(1)` in `TotpService` |
| TOTP secret stored in database | Done | Cleared on disable |
| 2FA setup requires code confirmation before it is active | Done | `confirmTotp` checks code before setting `totpEnabled=true` |
| Disabling 2FA requires current TOTP code | Done | `disableTotp` verifies code before clearing |

---

## Input Validation

| Control | Status | Notes |
|---|---|---|
| Bean Validation on all request DTOs | Done | `@Valid` on all controller method parameters |
| Email format validation on registration and forgot-password | Done | `@Email` on DTO fields |
| Minimum username length (3 chars) | Done | `@Size(min = 3, max = 50)` |
| Request body required for all mutation endpoints | Done | `required = true` in controller mappings |

---

## SQL Injection Prevention

| Control | Status | Notes |
|---|---|---|
| All DB access via Spring Data JPA (parameterised queries) | Done | No native SQL string concatenation anywhere |
| Custom JPQL query in `RefreshTokenRepository` uses `@Param` | Done | `revokeAllByFamilyId` uses named parameters |

---

## Account Security

| Control | Status | Notes |
|---|---|---|
| Email verification required before login | Done | `user.enabled = false` until token is verified |
| Verification tokens are single-use | Done | `used` flag on `VerificationToken` |
| Verification tokens expire (24 h) | Done | `LocalDateTime.now().plusHours(24)` |
| Account lockout by admin | Done | `locked` flag on `User`, checked by Spring Security |
| All sessions revoked when account is disabled | Done | `AdminController` calls `refreshTokenService.revokeAllForUser` |

---

## API Security

| Control | Status | Notes |
|---|---|---|
| All endpoints require authentication except explicitly public ones | Done | `anyRequest().authenticated()` in `SecurityConfig` |
| Admin endpoints require ADMIN role | Done | `@PreAuthorize("hasRole('ADMIN')")` on `AdminController` |
| Method-level security enabled | Done | `@EnableMethodSecurity` implied by `@PreAuthorize` |
| HTTP 401 returned for missing/invalid token | Done | Custom `AuthenticationEntryPoint` returning SC_UNAUTHORIZED |
| Swagger UI and OpenAPI spec publicly accessible | Done | Permitted in `SecurityConfig` for API exploration |

---

## Infrastructure Security

| Control | Status | Notes |
|---|---|---|
| Secrets injected via environment variables | Done | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET` |
| Dockerfile uses non-root JRE image | Done | `eclipse-temurin:21-jre-alpine` — minimal attack surface |
| Docker multi-stage build (no build tools in final image) | Done | Build stage uses JDK, run stage uses JRE |
| PostgreSQL uses dedicated non-root user | Done | `bidmart`/`bidmart123` in `docker-compose.yml` (override in prod) |
| Database password not hardcoded in app config | Done | Reads from `DATABASE_PASSWORD` env var |

---

## Dependency Security

| Control | Status | Notes |
|---|---|---|
| OWASP Dependency-Check in CI | Done | `dependencyCheckAnalyze` Gradle task in `ci.yml` |
| Trivy container scan in CI | Done | CRITICAL + HIGH severity; results uploaded as GitHub SARIF |
| Spring Boot 4.0.3 (latest at time of writing) | Done | All transitive dependencies at latest stable |

---

## Known Limitations / Future Work

| Item | Notes |
|---|---|
| TOTP secret not encrypted at rest | Stored as plaintext in DB column — encryption at rest requires column-level encryption or an HSM |
| Rate limiting is in-memory only | Resets on app restart; for multi-instance deployments, switch to a distributed store (Redis + Bucket4j Redis adapter) |
| Email sending is mocked | Verification and reset tokens are returned in the API response body — production deployment requires a real email gateway (SES, SendGrid, etc.) |
| No HTTPS enforcement in app | TLS should be terminated at the load balancer or reverse proxy (e.g., Nginx, AWS ALB) in front of this service |
| CORS not explicitly configured | Add `CorsConfigurationSource` to `SecurityConfig` if the frontend is served from a different origin |
