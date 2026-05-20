# BidMart Authentication Service — Demo Instructions

This guide walks through the main features of the authentication service using `curl`. All examples assume the service is running at `http://localhost:8081`.

---

## Prerequisites

Start the service with Docker Compose:

```bash
docker compose up --build
```

Wait until you see `Started BidmartAuthenticationApplication` in the logs.

Alternatively, open the interactive Swagger UI at: http://localhost:8081/swagger-ui.html

---

## 1. Register a New User

```bash
curl -s -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","username":"demouser","password":"Password1!"}' | jq .
```

Expected response (HTTP 201):

```json
{
  "message": "Registration successful. Please verify your email.",
  "verificationToken": "some-uuid-token"
}
```

Save the `verificationToken` — in production this would be sent by email. Here it is returned directly for development.

---

## 2. Verify Email

```bash
curl -s -X POST "http://localhost:8081/api/auth/verify-email?token=<verificationToken>" | jq .
```

Expected response (HTTP 200): `Email verified successfully.`

---

## 3. Login

```bash
curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com","password":"Password1!"}' | jq .
```

Expected response (HTTP 200):

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "some-uuid-raw-token",
  "tokenType": "Bearer",
  "mfaRequired": false,
  "mfaToken": null
}
```

Save `accessToken` and `refreshToken` as environment variables:

```bash
ACCESS_TOKEN="eyJ..."
REFRESH_TOKEN="some-uuid-raw-token"
```

---

## 4. Access Protected Endpoint

```bash
curl -s http://localhost:8081/api/users/me \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Expected response (HTTP 200):

```json
{
  "id": "uuid",
  "email": "demo@example.com",
  "username": "demouser",
  "role": "BUYER",
  "enabled": true
}
```

---

## 5. Refresh Token Rotation

Exchange a refresh token for a new token pair:

```bash
curl -s -X POST http://localhost:8081/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jq .
```

Expected response (HTTP 200): a new `accessToken` + `refreshToken`. The old refresh token is now invalid.

---

## 6. Refresh Token Reuse Detection

Reuse the **old** refresh token (from step 3, before rotation):

```bash
curl -s -X POST http://localhost:8081/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jq .
```

Expected response (HTTP 401): `Refresh token reuse detected; all sessions revoked`

This revokes the **entire session family** — all refresh tokens from the same login are invalidated.

---

## 7. Two-Factor Authentication (TOTP)

### 7a. Log in with a fresh account and enable 2FA

```bash
# Register and verify a new user
curl -s -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"mfa@example.com","username":"mfauser","password":"Password1!"}' | jq .
```

Verify the email token, then login to get an access token.

### 7b. Set up TOTP

```bash
curl -s -X POST http://localhost:8081/api/auth/2fa/setup \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Expected response:

```json
{
  "secret": "BASE32SECRETHERE",
  "otpAuthUrl": "otpauth://totp/BidMart:mfa@example.com?secret=BASE32SECRETHERE&issuer=BidMart&..."
}
```

Scan the `otpAuthUrl` with Google Authenticator, Authy, or any TOTP app.

### 7c. Confirm 2FA (with current code from the app)

```bash
curl -s -X POST http://localhost:8081/api/auth/2fa/confirm \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"123456"}' | jq .
```

Expected response (HTTP 200): `2FA enabled successfully.`

### 7d. Login with 2FA enabled

```bash
curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"mfa@example.com","password":"Password1!"}' | jq .
```

Expected response (no tokens yet):

```json
{
  "accessToken": null,
  "refreshToken": null,
  "tokenType": "Bearer",
  "mfaRequired": true,
  "mfaToken": "eyJ..."
}
```

### 7e. Complete MFA login

```bash
MFA_TOKEN="eyJ..."

curl -s -X POST http://localhost:8081/api/auth/2fa/verify \
  -H "Content-Type: application/json" \
  -d "{\"mfaToken\":\"$MFA_TOKEN\",\"code\":\"123456\"}" | jq .
```

Expected response (HTTP 200): full `accessToken` + `refreshToken` pair.

---

## 8. Session Management

List all active sessions for the current user:

```bash
curl -s http://localhost:8081/api/auth/sessions \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Expected response (HTTP 200): array of session objects with device info, IP, and timestamps.

Revoke all sessions (sign out everywhere):

```bash
curl -s -X DELETE http://localhost:8081/api/auth/sessions \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

Revoke a specific session by ID:

```bash
SESSION_ID="uuid-from-sessions-list"

curl -s -X DELETE "http://localhost:8081/api/auth/sessions/$SESSION_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq .
```

---

## 9. Password Reset

```bash
# Request reset (token returned in response body for demo; would be emailed in production)
curl -s -X POST http://localhost:8081/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@example.com"}' | jq .
```

Check the server logs for the reset token:
```
[MOCK EMAIL] Password reset token for demo@example.com: <token>
```

Then reset the password:

```bash
curl -s -X POST http://localhost:8081/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<reset-token>","newPassword":"NewPassword1!"}' | jq .
```

Expected response (HTTP 200): `Password reset successfully.`

> Note: all existing refresh tokens are revoked immediately after a password reset.

---

## 10. Rate Limiting

Hit the login endpoint more than 5 times in quick succession from the same IP:

```bash
for i in {1..6}; do
  curl -s -o /dev/null -w "Attempt $i: %{http_code}\n" \
    -X POST http://localhost:8081/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"demo@example.com","password":"WrongPassword"}';
done
```

Expected output:

```
Attempt 1: 401
Attempt 2: 401
Attempt 3: 401
Attempt 4: 401
Attempt 5: 401
Attempt 6: 429
```

HTTP 429 means "Too Many Requests" — the IP is blocked for the remainder of the 15-minute window.

---

## 11. Admin — Disable a User (ADMIN role required)

First, promote a user to ADMIN directly in the database (or register with an ADMIN role if applicable), then:

```bash
USER_ID="uuid-of-user-to-disable"

curl -s -X POST "http://localhost:8081/api/admin/users/$USER_ID/disable" \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" | jq .
```

Expected response (HTTP 200):

```json
{
  "message": "User disabled and sessions revoked"
}
```

The target user's account is now locked and all their refresh tokens are revoked.

---

## 12. Logout

```bash
curl -s -X POST http://localhost:8081/api/auth/logout \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}" | jq .
```

Expected response (HTTP 200): `Logged out successfully.`

The refresh token is deleted. Attempting to use it again returns HTTP 401.

---

## Health Check

```bash
curl -s http://localhost:8081/actuator/health | jq .
```

Expected:

```json
{
  "status": "UP"
}
```

---

## RabbitMQ Management (Docker Compose only)

Open http://localhost:15672 — login with `guest`/`guest`.

Navigate to **Exchanges** → `bidmart.auth.events` to see the topic exchange. Events are published there on registration, login, email verification, password reset, and account disable.
