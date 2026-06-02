# BidMart Authentication — Demo Guide per Milestone

## Checklist Sebelum Demo

- [ ] **Terminal 1** — PostgreSQL: `docker-compose up postgres -d`
- [ ] **Terminal 2** — Backend: `./gradlew bootRun --args='--spring.jpa.hibernate.ddl-auto=update'`
  - Tunggu log: `Started BidmartAuthenticationApplication in X.XXX seconds`
- [ ] **Terminal 3** — Frontend: `cd D:\AAAZKA\WINDOWS\ADPRO\bidmart-frontend && npm run dev`
- [ ] **Tab browser 1** — `http://localhost:3000/login`
- [ ] **Tab browser 2** — `http://localhost:8081/swagger-ui.html`
- [ ] **HP** — Google Authenticator terinstall (untuk demo 2FA di Milestone 75%)

---

## MILESTONE 25% — MVP

> **Inti:** Registrasi dengan password hashing, verifikasi email (mocked), login JWT, protected endpoint, migrasi DB.

---

### Demo 1 — Registrasi & Password Hashing

**Lokasi:** `localhost:3000/login` → tab **Register**

**Langkah:**
1. Isi form: Username `demouser`, Email `demo@test.com`, Tipe Akun Pembeli, Password `demo1234`
2. Klik **Buat Akun** → muncul alert "Registrasi berhasil!"

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/AuthService.java` **baris 70–93**
```java
// Baris 79 — password di-hash SEBELUM disimpan
user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

// Baris 85–87 — buat verification token (mocked email)
String rawToken = UUID.randomUUID().toString();
LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);
tokenRepository.save(new VerificationToken(rawToken, user, expiresAt));
```

Buka `src/main/java/.../model/User.java` **baris 35–39**
```java
// passwordHash — tidak pernah plaintext
@Column(nullable = false)
private String passwordHash;

// enabled = false sampai email diverifikasi
private boolean enabled = false;
```

**Yang diucapkan:**
> "Password di-hash menggunakan BCrypt sebelum disimpan. Di database tidak ada kolom `password`, hanya `password_hash`. Setelah register, akun belum aktif (`enabled=false`) sampai email diverifikasi. Karena ini mocked, token dikembalikan langsung di response dan frontend auto-memanggil verify-email."

---

### Demo 2 — Login & JWT Access Token

**Lokasi:** `localhost:3000/login` → tab **Sign In**

**Langkah:**
1. Masukkan `demo@test.com` + `demo1234` → klik **Masuk**
2. Redirect ke halaman home
3. Buka **DevTools** (F12) → **Application** → **Local Storage** → `localhost:3000`
4. Tunjukkan key: `bidmart_token`, `bidmart_role`, `bidmart_email`

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/AuthService.java` **baris 113–130**
```java
// Baris 115–117 — Spring Security validasi kredensial
authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(email, request.getPassword())
);

// Baris 121–124 — cek apakah 2FA aktif (Milestone 75%)
if (user.isTotpEnabled()) {
    String mfaToken = jwtService.generateMfaToken(user.getEmail());
    return AuthResponse.requireMfa(mfaToken);
}

// Baris 126–129 — generate JWT + refresh token
String accessToken = jwtService.generateToken(user.getEmail());
String refreshToken = refreshTokenService.createRefreshToken(user, deviceInfo, ipAddress);
```

Buka `src/main/java/.../service/JwtService.java` **baris 32–39**
```java
// JWT hanya berisi subject=email, iat, exp — stateless, tidak ada session di server
public String generateToken(String email) {
    return Jwts.builder()
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration)) // 24 jam
            .signWith(getSigningKey())
            .compact();
}
```

**Yang diucapkan:**
> "Login berhasil mengembalikan JWT Access Token yang berlaku 24 jam. JWT bersifat stateless — server tidak menyimpan session, cukup verifikasi signature-nya saja."

---

### Demo 3 — Protected Endpoint GET /api/users/me

**Lokasi:** `localhost:3000/profil`

**Langkah:**
1. Buka `localhost:3000/profil` — tunjukkan data user yang tampil
2. Buka **Swagger** `localhost:8081/swagger-ui.html`
3. Copy JWT dari localStorage → klik **Authorize** → paste → Close
4. Expand **GET /api/users/me** → Try it out → Execute → tunjukkan response 200
5. Klik **Authorize** lagi → hapus token → Execute lagi → tunjukkan 401

**Kode yang ditunjukkan:**

Buka `src/main/java/.../config/JwtAuthFilter.java` **baris 30–54**
```java
// Filter ini jalan di SETIAP request sebelum sampai ke controller
protected void doFilterInternal(...) {
    String authHeader = request.getHeader("Authorization");

    // Baris 35–38 — kalau tidak ada Bearer token, langsung lanjut (akan ditolak SecurityConfig)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;
    }

    // Baris 40–50 — ekstrak email dari JWT, load user, set ke SecurityContext
    String token = authHeader.substring(7);
    String email = jwtService.extractEmail(token);
    ...
    SecurityContextHolder.getContext().setAuthentication(authToken);
}
```

Buka `src/main/java/.../config/SecurityConfig.java` **baris 33–46**
```java
// Baris 35–42 — endpoint public (tidak perlu token)
.requestMatchers("/api/auth/register", "/api/auth/login", ...).permitAll()

// Baris 45 — semua endpoint lain WAJIB autentikasi
.anyRequest().authenticated()
```

**Yang diucapkan:**
> "Setiap request melewati `JwtAuthFilter`. Filter mengekstrak email dari JWT, memuat user dari database, lalu mengisi Spring Security Context. Kalau token tidak ada atau invalid, request ditolak dengan 401 sebelum sampai ke controller."

---

### Demo 4 — DB Migration (Flyway)

**Lokasi:** `src/main/resources/db/migration/`

**Kode yang ditunjukkan:**

Buka `src/main/resources/db/migration/V1__create_users_table.sql`
```sql
-- V1: tabel users dasar
CREATE TABLE users (id, email, username, password_hash, enabled, role, created_at)
```

Buka `src/main/resources/db/migration/V2__create_verification_tokens_table.sql`
```sql
-- V2: token verifikasi email
CREATE TABLE verification_tokens (id, token, user_id FK, expires_at, used)
```

Tunjukkan bahwa ada 9 file migration (V1–V9) yang masing-masing incremental — ini adalah hasil kerja per milestone.

**Yang diucapkan:**
> "Flyway menjalankan migrasi secara berurutan. V1 membuat tabel `users`, V2 `verification_tokens`, dan seterusnya sampai V9 untuk token family reuse detection. Ini memastikan skema database selalu sinkron dengan kode tanpa perlu manual SQL."

---

## MILESTONE 50% — Refresh Token, Roles & OpenAPI

> **Inti:** Refresh token rotation dengan secure storage, logout, password reset, admin endpoint, Swagger docs.

---

### Demo 1 — Refresh Token Rotation & Secure Storage

**Lokasi:** Swagger `localhost:8081/swagger-ui.html`

**Langkah:**
1. **POST /api/auth/login** — Execute dengan `demo@test.com` / `demo1234`
2. Copy `refreshToken` dari response
3. **POST /api/auth/refresh** — paste refreshToken → Execute
4. Tunjukkan: `accessToken` baru + `refreshToken` baru yang **berbeda** dari sebelumnya
5. Coba **POST /api/auth/refresh** lagi dengan token **lama** → 401

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/RefreshTokenService.java` **baris 35–43**
```java
// Token disimpan sebagai SHA-256 hash, bukan raw token
public String createRefreshToken(User user, ...) {
    String rawToken = UUID.randomUUID().toString();
    String tokenHash = hashToken(rawToken); // ← SHA-256 hash
    UUID familyId = UUID.randomUUID();      // ← family untuk reuse detection
    refreshTokenRepository.save(new RefreshToken(tokenHash, user, expiresAt, familyId, ...));
    return rawToken; // ← raw token dikirim ke client, TIDAK disimpan
}
```

Buka `src/main/java/.../service/RefreshTokenService.java` **baris 46–82**
```java
// Rotasi: token lama di-revoke, token baru dibuat dalam family yang sama
public User rotateRefreshToken(String rawToken, String[] newRawTokenHolder) {
    ...
    // Baris 55–59 — REUSE DETECTION: token sudah di-revoke tapi dipakai lagi?
    if (stored.isRevoked()) {
        refreshTokenRepository.revokeAllByFamilyId(stored.getFamilyId(), LocalDateTime.now());
        throw new IllegalArgumentException("Refresh token reuse detected; all sessions revoked");
    }
    ...
    // Baris 70–72 — tandai token lama sebagai revoked
    stored.setRevoked(true);
    stored.setRevokedAt(LocalDateTime.now());
}
```

Buka `src/main/java/.../model/RefreshToken.java` **baris 32–57**
```java
@Column(nullable = false, unique = true, length = 64)
private String tokenHash;   // ← yang disimpan di DB, bukan raw token

@Column(nullable = false)
private UUID familyId;      // ← semua rotasi satu sesi punya familyId sama

@Column(nullable = false)
private boolean revoked = false;  // ← true setelah di-rotate atau logout
```

**Yang diucapkan:**
> "Raw token tidak pernah disimpan di database, hanya SHA-256 hash-nya. Ini berarti kalau database bocor, attacker tidak bisa menggunakan token-token tersebut. Setiap rotasi, token lama di-revoke dan token baru dibuat — tapi tetap dalam `familyId` yang sama untuk tracking."

---

### Demo 2 — Logout & Session Revocation

**Lokasi:** Swagger

**Langkah:**
1. Dari token yang baru didapat, copy `refreshToken`
2. **POST /api/auth/logout** → paste refreshToken → Execute → 200 OK
3. **POST /api/auth/refresh** dengan token yang SAMA → 401

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/RefreshTokenService.java` **baris 84–89**
```java
// Logout = hapus refresh token dari DB → tidak bisa refresh lagi
public void revokeToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
            .ifPresent(refreshTokenRepository::delete);
}
```

**Yang diucapkan:**
> "Logout benar-benar menghapus refresh token dari database. Access token yang masih berlaku tidak bisa dibatalkan (stateless), tapi dalam 24 jam akan expired sendiri. Ini trade-off standar JWT."

---

### Demo 3 — Password Reset (Mocked)

**Lokasi:** Swagger

**Langkah:**
1. **POST /api/auth/forgot-password** → `{"email": "demo@test.com"}` → Execute
2. Lihat **log terminal** backend → ada baris: `[MOCK EMAIL] Password reset token for demo@test.com: <token>`
3. Copy token dari log
4. **POST /api/auth/reset-password** → `{"token": "<paste>", "newPassword": "newdemo1234"}` → Execute → 200
5. Coba login dengan password **lama** → 401, password **baru** → 200

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/AuthService.java` **baris 165–195**
```java
// Baris 170 — token di-hash sebelum disimpan
String tokenHash = hashToken(rawToken);

// Baris 171–172 — berlaku 30 menit saja
LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES);

// Baris 173 — log sebagai pengganti email (mocked)
LOG.info("[MOCK EMAIL] Password reset token for {}: {}", user.getEmail(), rawToken);

// Baris 191–193 — setelah reset, SEMUA sesi aktif di-revoke
prt.getUser().setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
prt.setUsed(true);
refreshTokenService.revokeAllForUser(prt.getUser()); // ← paksa login ulang semua device
```

**Yang diucapkan:**
> "Reset token disimpan sebagai SHA-256 hash dan berlaku 30 menit. Setelah password berhasil diubah, token langsung ditandai `used=true` dan semua sesi aktif di-revoke — user harus login ulang di semua device."

---

### Demo 4 — Roles & Admin Endpoint

**Lokasi:** Swagger

**Langkah:**
1. Register user admin via Swagger:
   - **POST /api/auth/register** → `{"email":"admin@test.com","username":"admin","password":"admin1234","role":"ADMIN"}`
   - Copy `verificationToken` → **POST /api/auth/verify-email?token=...** → Execute
2. **POST /api/auth/login** dengan admin credentials → copy `accessToken`
3. Klik **Authorize** → paste token admin
4. Login `demo@test.com` di browser → **GET /api/users/me** → copy UUID user
5. **POST /api/admin/users/{id}/disable** → paste UUID → Execute → 200
6. Coba **POST /api/auth/refresh** dengan refresh token `demo@test.com` → 401 (sesi direvoke)

**Kode yang ditunjukkan:**

Buka `src/main/java/.../controller/AdminController.java` **baris 20–48**
```java
// Baris 20 — seluruh controller hanya bisa diakses ADMIN
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    ...
    // Baris 40–46 — disable user + revoke semua sesi
    user.setLocked(true);
    userRepository.save(user);
    refreshTokenService.revokeAllForUser(user); // ← logout paksa semua device
    eventPublisher.publishUserDisabled(user, adminId);
}
```

**Yang diucapkan:**
> "`@PreAuthorize('hasRole(ADMIN)')` di class level berarti semua method di controller ini otomatis memerlukan role ADMIN. Kalau dicoba dengan token non-ADMIN, Spring Security langsung return 403 tanpa masuk ke method-nya sama sekali."

---

### Demo 5 — OpenAPI Documentation

**Lokasi:** `localhost:8081/swagger-ui.html`

**Yang ditunjukkan:**
- Semua endpoint terorganisir per tag: Auth, 2FA, Sessions, Users, Admin
- Setiap endpoint ada schema request body + semua response codes (200, 400, 401, 403, 409, 410, 429)
- Klik tombol **Authorize** — ada Bearer JWT security scheme

**Kode yang ditunjukkan:**

Buka `src/main/java/.../config/OpenApiConfig.java`
```java
// Konfigurasi SpringDoc OpenAPI — otomatis generate docs dari annotations
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info().title("BidMart Authentication API"))
        .components(new Components().addSecuritySchemes("bearerAuth",
            new SecurityScheme().type(HTTP).scheme("bearer").bearerFormat("JWT")));
}
```

**Yang diucapkan:**
> "OpenAPI spec di-generate otomatis oleh SpringDoc dari kode dan anotasi. File `openapi.yaml` di `src/main/resources/static/` adalah versi statis yang bisa digunakan oleh tim lain untuk integrasi tanpa harus menjalankan server."

---

## MILESTONE 75% — 2FA, Rate Limiting, Sessions & Events

> **Inti:** TOTP 2FA, reuse detection, rate limiting, session management, event publishing, CI extended.

> **Persiapan:** Install **Google Authenticator** di HP sebelum demo ini.

---

### Demo 1 — Setup TOTP 2FA

**Lokasi:** Login dulu di `localhost:3000`, lalu buka `localhost:3000/keamanan`

**Langkah:**
1. Buka `localhost:3000/keamanan`
2. Klik setup/enable 2FA
3. Tunjukkan QR code atau secret yang muncul
4. Scan QR code dengan Google Authenticator di HP
5. Masukkan 6-digit kode yang muncul di app → Konfirmasi → 2FA aktif

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/TotpService.java` **baris 15–48**
```java
private static final int DIGITS = 6;    // 6-digit code
private static final int PERIOD = 30;   // refresh tiap 30 detik

public String generateSecret() {
    return secretGenerator.generate(); // base32-encoded secret
}

// URL ini yang diconvert jadi QR code
public String getOtpAuthUrl(String secret, String email) {
    return "otpauth://totp/BidMart:" + email
            + "?secret=" + secret + "&issuer=BidMart&algorithm=SHA1"
            + "&digits=6&period=30";
}

// Toleransi ±1 periode (30 detik) untuk clock skew
verifier.setAllowedTimePeriodDiscrepancy(1);
```

Buka `src/main/java/.../service/AuthService.java` **baris 197–218**
```java
// Setup: generate secret, simpan ke user (belum aktif)
public TotpSetupResponse setupTotp(String email) {
    String secret = totpService.generateSecret();
    user.setTotpSecret(secret);  // disimpan, tapi totpEnabled masih false
    return new TotpSetupResponse(secret, otpAuthUrl);
}

// Confirm: verifikasi kode pertama, baru aktifkan
public void confirmTotp(String email, String code) {
    if (!totpService.verifyCode(user.getTotpSecret(), code)) {
        throw new IllegalArgumentException("Invalid TOTP code");
    }
    user.setTotpEnabled(true); // ← baru aktif setelah dikonfirmasi
}
```

Buka `src/main/resources/db/migration/V7__add_totp_to_users.sql`
```sql
-- Migration V7 yang menambahkan kolom 2FA ke tabel users
ALTER TABLE users ADD COLUMN totp_secret VARCHAR(255);
ALTER TABLE users ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
```

**Yang diucapkan:**
> "Setup 2FA dua tahap: pertama `/2fa/setup` yang generate secret dan simpan ke user tapi belum aktif. Baru setelah user konfirmasi dengan kode valid via `/2fa/confirm`, field `totpEnabled=true` di-set. Ini mencegah user terkunci karena setup gagal di tengah jalan."

---

### Demo 2 — Login dengan 2FA Aktif

**Lokasi:** `localhost:3000/login`

**Langkah:**
1. Logout dari `localhost:3000`
2. Login dengan `demo@test.com` + `demo1234`
3. Setelah input password → tampil **halaman OTP 6 digit** (bukan langsung masuk)
4. Buka Google Authenticator di HP → masukkan kode
5. Klik Verifikasi → berhasil masuk

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/AuthService.java` **baris 121–124**
```java
// Login: kalau 2FA aktif, return mfaToken bukan full token
if (user.isTotpEnabled()) {
    String mfaToken = jwtService.generateMfaToken(user.getEmail()); // JWT 5 menit
    return AuthResponse.requireMfa(mfaToken); // accessToken = null, mfaRequired = true
}
```

Buka `src/main/java/.../service/JwtService.java` **baris 41–49**
```java
// MFA token: JWT sementara 5 menit dengan claim "scope=mfa"
public String generateMfaToken(String email) {
    return Jwts.builder()
            .subject(email)
            .claim(CLAIM_SCOPE, SCOPE_MFA)            // ← "scope": "mfa"
            .expiration(new Date(System.currentTimeMillis() + MFA_TOKEN_EXPIRY_MS)) // 5 menit
            .signWith(getSigningKey())
            .compact();
}
```

Buka `src/main/java/.../service/AuthService.java` **baris 132–150**
```java
// Verifikasi MFA: validasi mfaToken + kode TOTP → baru return full token
public AuthResponse verifyMfaAndLogin(String mfaToken, String code, ...) {
    String email = jwtService.extractMfaEmail(mfaToken); // validasi scope=mfa
    if (!totpService.verifyCode(user.getTotpSecret(), code)) {
        throw new IllegalArgumentException("Invalid TOTP code");
    }
    String accessToken = jwtService.generateToken(user.getEmail()); // ← full token baru
    String refreshToken = refreshTokenService.createRefreshToken(user, ...);
}
```

**Yang diucapkan:**
> "Saat login dengan 2FA aktif, backend return `mfaRequired=true` dan `mfaToken` — JWT sementara 5 menit yang hanya bisa dipakai untuk endpoint `/2fa/verify`. Frontend menampilkan halaman OTP. Setelah kode TOTP valid, baru JWT penuh diberikan. Ini two-step authentication yang proper."

---

### Demo 3 — Refresh Token Reuse Detection

**Lokasi:** Swagger

**Langkah:**
1. Login → copy `refreshToken` (simpan sebagai Token-A)
2. **POST /api/auth/refresh** dengan Token-A → dapat Token-B (Token-A sekarang revoked)
3. **POST /api/auth/refresh** dengan Token-B → dapat Token-C (normal)
4. **POST /api/auth/refresh** dengan Token-A lagi (yang sudah revoked) → 401
5. Coba Token-C yang masih valid → juga 401 (seluruh family direvoke!)

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/RefreshTokenService.java` **baris 54–59**
```java
// KUNCI: token revoked tapi dicoba lagi = indikasi theft
if (stored.isRevoked()) {
    // Revoke SEMUA token dalam family yang sama
    refreshTokenRepository.revokeAllByFamilyId(stored.getFamilyId(), LocalDateTime.now());
    throw new IllegalArgumentException("Refresh token reuse detected; all sessions revoked");
}
```

**Yang diucapkan:**
> "Ini skenario token theft: attacker mencuri refresh token yang sudah di-rotate. Karena token lama seharusnya tidak dipakai lagi, penggunaan token revoked adalah sinyal bahwa ada yang mencuri. Sistem langsung merevoke seluruh family — semua device dalam sesi tersebut harus login ulang. Better safe than sorry."

---

### Demo 4 — Login Rate Limiting

**Lokasi:** Swagger

**Langkah:**
1. **POST /api/auth/login** dengan password salah 5x berturut-turut
2. Percobaan ke-6 → response **429 Too Many Requests**
3. Tunjukkan bahwa password benar pun ditolak sampai 15 menit

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/RateLimitService.java` **baris 22–33**
```java
// Per-IP bucket: 5 token, refill tiap 15 menit
public boolean isLoginAllowed(String ipAddress) {
    Bucket bucket = buckets.computeIfAbsent(ipAddress, k -> createBucket());
    return bucket.tryConsume(1); // false kalau bucket kosong
}

private Bucket createBucket() {
    Bandwidth limit = Bandwidth.builder()
            .capacity(maxAttempts)                            // 5 percobaan
            .refillIntervally(maxAttempts, Duration.ofSeconds(windowSeconds)) // per 900 detik
            .build();
}
```

Buka `src/main/java/.../controller/AuthController.java` **baris 69–72**
```java
// Cek rate limit SEBELUM proses apapun
String ip = httpRequest.getRemoteAddr();
if (!rateLimitService.isLoginAllowed(ip)) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build(); // 429
}
```

Buka `src/main/resources/application.properties` **baris 36–37**
```properties
rate-limit.login.max-attempts=5
rate-limit.login.window-seconds=900   # 15 menit
```

**Yang diucapkan:**
> "Rate limiting menggunakan Bucket4j dengan token bucket algorithm. Setiap IP punya bucket dengan 5 token. Setiap percobaan login mengonsumsi 1 token. Kalau bucket kosong, langsung 429. Bucket terisi ulang penuh setiap 15 menit. Konfigurasi bisa diubah lewat `application.properties` tanpa ubah kode."

---

### Demo 5 — Session Listing & Per-Session Revoke

**Lokasi:** `localhost:3000/keamanan` + browser kedua (incognito/Edge)

**Langkah:**
1. Login di **browser pertama** (Chrome normal)
2. Login di **browser kedua** (Chrome incognito / Microsoft Edge)
3. Buka `localhost:3000/keamanan` di browser pertama
4. Tunjukkan **2 sesi aktif** dengan device info (User-Agent) berbeda
5. Klik revoke pada sesi kedua
6. Pindah ke browser kedua → refresh halaman → redirect ke login

**Kode yang ditunjukkan:**

Buka `src/main/java/.../controller/SessionController.java` **baris 38–65**
```java
// List sesi aktif
@GetMapping
public ResponseEntity<List<SessionResponse>> listSessions(...) {
    return ResponseEntity.ok(refreshTokenService.getActiveSessions(user));
}

// Revoke sesi spesifik
@DeleteMapping("/{sessionId}")
public ResponseEntity<?> revokeSession(..., @PathVariable UUID sessionId) {
    refreshTokenService.revokeSessionById(sessionId, user);
    eventPublisher.publishSessionRevoked(user.getId(), sessionId);
}
```

Buka `src/main/java/.../model/RefreshToken.java` **baris 53–57**
```java
// Metadata yang disimpan per sesi
@Column(length = 255)
private String deviceInfo;  // User-Agent browser

@Column(length = 45)
private String ipAddress;   // IP address client
```

Buka `src/main/resources/db/migration/V8__add_session_metadata.sql`
```sql
-- V8: tambah kolom device info ke refresh_tokens
ALTER TABLE refresh_tokens ADD COLUMN device_info VARCHAR(255);
ALTER TABLE refresh_tokens ADD COLUMN ip_address VARCHAR(45);
```

**Yang diucapkan:**
> "Setiap sesi menyimpan User-Agent dan IP address. User bisa lihat semua device yang sedang login dan revoke sesi yang mencurigakan — mirip fitur 'Active Sessions' di Google atau GitHub. Saat sesi di-revoke, event `session.revoked` juga dipublish."

---

### Demo 6 — Event Publishing

**Lokasi:** Terminal yang menjalankan `bootRun`

**Langkah:**
1. Lakukan aksi: register, login, logout — perhatikan log terminal backend
2. Tunjukkan baris log seperti:
   ```
   [LoggingUserEventPublisher] Publishing user.registered for demo@test.com
   [LoggingUserEventPublisher] Publishing user.logged.in for demo@test.com
   ```

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/RabbitMqUserEventPublisher.java` **baris 17–30**
```java
// Kondisional: hanya aktif kalau spring.rabbitmq.enabled=true
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true")
public class RabbitMqUserEventPublisher implements UserEventPublisher {

    public void publishUserRegistered(User user) {
        publish("user.registered", buildPayload(user.getId(), user.getEmail()));
    }
    // routing keys: user.registered, user.email.verified, user.logged.in,
    //               password.reset, user.disabled, session.revoked
}
```

Buka `src/main/java/.../config/RabbitMqConfig.java`
```java
// Exchange topic "bidmart.auth.events" — service lain bisa subscribe
public static final String EXCHANGE = "bidmart.auth.events";
```

**Yang diucapkan:**
> "Di local, event hanya di-log karena RabbitMQ tidak jalan (`spring.rabbitmq.enabled=false`). Di production, event dikirim ke exchange `bidmart.auth.events` menggunakan Spring AMQP. Service lain seperti notification service bisa subscribe ke routing key `user.registered` untuk kirim welcome email, atau ke `user.disabled` untuk membatalkan transaksi aktif."

---

### Demo 7 — CI Pipeline

**Lokasi:** GitHub repository → tab **Actions**

**Yang ditunjukkan:**
- Workflow `ci.yml` dengan jobs: Build & Test → Checkstyle → SonarCloud → OWASP Dependency-Check → Trivy Container Scan
- Tunjukkan salah satu run yang sukses dengan semua jobs hijau

**Kode yang ditunjukkan:**

Buka `.github/workflows/ci.yml`
```yaml
# CI berjalan di setiap push/PR ke main
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-test:     # compile + test + JaCoCo + SonarCloud
  security-scan:  # OWASP dependency vulnerability check
  container-scan: # Trivy scan Docker image
```

**Yang diucapkan:**
> "CI pipeline memastikan setiap push ke main melewati: unit test, integration test, code style check, analisis kualitas kode di SonarCloud, scan dependensi untuk CVE (kerentanan), dan scan Docker image. CD hanya jalan kalau semua jobs CI sukses."

---

## MILESTONE 100% — Final, Tests, Security, Monitoring & Profiling

> **Inti:** Test coverage, security checklist, CD pipeline, monitoring (Actuator/health), profiling (Micrometer @Timed), dokumentasi lengkap.

---

### Demo 1 — Test Suite Lengkap

**Lokasi:** Terminal

**Langkah:**
```powershell
cd D:\AAAZKA\WINDOWS\ADPRO\bidmart-authentication
./gradlew test
```
Tunggu sampai selesai → tunjukkan semua test PASSED.

**Kode yang ditunjukkan:**

Buka folder `src/test/java/.../` — tunjukkan daftar file test:
- `AuthControllerIntegrationTest.java` — register, verify, login, refresh, logout
- `SessionIntegrationTest.java` — list session, revoke session, revoke all
- `RefreshTokenIntegrationTest.java` — rotasi token, reuse detection
- `AdminControllerIntegrationTest.java` — disable user, auth check
- `RateLimitIntegrationTest.java` — 5 attempts → 429
- `TotpIntegrationTest.java` — setup, confirm, verify, disable 2FA

Buka salah satu test, misal `AuthControllerIntegrationTest.java`:
```java
// Test menggunakan H2 in-memory — tidak butuh PostgreSQL jalan
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@ActiveProfiles("test")
class AuthControllerIntegrationTest {
    // Test happy path dan error case:
    // - registrasi sukses
    // - email duplicate → 409
    // - login sebelum verify → 403
    // - token invalid → 401
}
```

Buka `src/test/resources/application-test.properties`
```properties
# H2 in-memory untuk test — isolasi total dari DB production
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
```

**Yang diucapkan:**
> "Semua test adalah integration test yang menguji endpoint dari HTTP request sampai database response. Menggunakan H2 in-memory sehingga bisa jalan di CI tanpa setup PostgreSQL eksternal. `@DirtiesContext` memastikan setiap test class dapat Spring context yang bersih."

---

### Demo 2 — JaCoCo Coverage Report

**Langkah:**
```powershell
./gradlew jacocoTestReport
```
Buka `build/reports/jacoco/test/html/index.html` di browser — tunjukkan persentase coverage per package.

**Yang diucapkan:**
> "JaCoCo mengukur berapa persen kode yang dieksekusi oleh test. CI pipeline dikonfigurasi untuk gagal jika coverage di bawah threshold tertentu, memastikan kode baru selalu punya test."

---

### Demo 3 — Security Hardening Checklist

Tunjukkan tabel ini sambil demo setiap poin:

| # | Fitur Keamanan | Bukti di Kode |
|---|----------------|---------------|
| 1 | Password BCrypt | `AuthService.java:79` — `passwordEncoder.encode()` |
| 2 | JWT stateless | `JwtService.java:32` — tidak ada session di server |
| 3 | Refresh token hashed | `RefreshTokenService.java:37` — `hashToken()` SHA-256 |
| 4 | Token reuse detection | `RefreshTokenService.java:55-58` — revoke seluruh family |
| 5 | Rate limiting | `RateLimitService.java:22` — Bucket4j per-IP |
| 6 | TOTP 2FA | `TotpService.java:46` — `verifyCode()` |
| 7 | Account locking | `AdminController.java:40` — `user.setLocked(true)` |
| 8 | Reset token 30 menit | `AuthService.java:36` — `RESET_TOKEN_EXPIRY_MINUTES = 30` |
| 9 | MFA token 5 menit | `JwtService.java:20` — `MFA_TOKEN_EXPIRY_MS = 5 * 60 * 1000` |
| 10 | Docker multi-stage | `Dockerfile` — build dengan JDK, run dengan JRE (lebih kecil) |

---

### Demo 4 — CD Pipeline

**Lokasi:** GitHub repository → tab **Actions** → workflow `cd.yml`

**Yang ditunjukkan:**
- CD hanya trigger setelah CI sukses
- Steps: Build JAR → Docker build & push ke Docker Hub → SSH ke EC2 → pull & restart

**Kode yang ditunjukkan:**

Buka `.github/workflows/cd.yml`
```yaml
# CD hanya jalan setelah CI sukses
on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]

jobs:
  deploy:
    steps:
      - Build JAR (skip tests)
      - Push Docker image ke Docker Hub
      - SSH ke EC2 → docker pull → docker restart
```

**Yang diucapkan:**
> "CD pipeline adalah 'dummy verified' — pipeline-nya nyata dan sudah pernah jalan, tapi environment production-nya tidak selalu aktif karena biaya. Yang penting adalah pipeline-nya sudah tersetup dan verified bisa jalan."

---

### Demo 5 — Monitoring (Spring Boot Actuator)

**Lokasi:** Browser → `localhost:8081/actuator/health`

**Langkah:**
1. Buka `localhost:8081/actuator/health` — tunjukkan response JSON
2. Buka `localhost:8081/actuator/metrics` — tunjukkan list semua metric names
3. Buka `localhost:8081/actuator/metrics/jvm.memory.used` — tunjukkan JVM memory usage

**Response yang diharapkan di `/actuator/health`:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

**Kode yang ditunjukkan:**

Buka `src/main/resources/application.properties` — bagian bawah:
```properties
management.endpoints.web.exposure.include=health,metrics,prometheus,info
management.endpoint.health.show-details=always
management.info.env.enabled=true
info.app.name=BidMart Authentication Service
```

Buka `src/main/java/.../config/SecurityConfig.java`:
```java
// Actuator endpoints dibuka secara publik — bisa dipantau tanpa login
.requestMatchers("/", "/actuator/health", "/actuator/metrics/**",
        "/actuator/prometheus", "/actuator/info").permitAll()
```

**Yang diucapkan:**
> "Monitoring diimplementasikan menggunakan Spring Boot Actuator. Endpoint `/actuator/health` menunjukkan status real-time dari database, disk, dan aplikasi. Kalau `db: DOWN`, berarti koneksi ke PostgreSQL putus dan perlu segera ditangani. Di production, endpoint ini biasanya disambungkan ke tool seperti UptimeRobot atau AWS CloudWatch untuk alerting otomatis."

---

### Demo 6 — Profiling (Micrometer @Timed)

**Lokasi:** Browser + Terminal

**Langkah:**
1. Lakukan beberapa operasi: register 1 user, login 2-3 kali, refresh token sekali
2. Buka `localhost:8081/actuator/metrics/auth.login` — tunjukkan timing data login
3. Buka `localhost:8081/actuator/metrics/auth.register` — tunjukkan timing data register
4. Buka `localhost:8081/actuator/metrics/http.server.requests?tag=uri:/api/auth/login` — HTTP-level timing

**Response yang diharapkan di `/actuator/metrics/auth.login`:**
```json
{
  "name": "auth.login",
  "description": "Time taken to authenticate a user",
  "measurements": [
    { "statistic": "COUNT", "value": 3.0 },
    { "statistic": "TOTAL_TIME", "value": 0.847 },
    { "statistic": "MAX", "value": 0.412 }
  ]
}
```

**Kode yang ditunjukkan:**

Buka `src/main/java/.../service/AuthService.java`:
```java
// @Timed mengukur waktu eksekusi method ini secara otomatis
@Timed(value = "auth.login", description = "Time taken to authenticate a user")
@Transactional
public AuthResponse login(LoginRequest request, String deviceInfo, String ipAddress) { ... }

@Timed(value = "auth.register", description = "Time taken to register a new user")
@Transactional
public RegisterResponse register(RegisterRequest request) { ... }

@Timed(value = "auth.mfa.verify", description = "Time taken to verify MFA and complete login")
@Transactional
public AuthResponse verifyMfaAndLogin(...) { ... }
```

Buka `src/main/java/.../service/RefreshTokenService.java`:
```java
@Timed(value = "auth.token.rotate", description = "Time taken to rotate a refresh token")
@Transactional
public User rotateRefreshToken(...) { ... }
```

Buka `src/main/java/.../config/MetricsConfig.java`:
```java
// Bean ini yang mengaktifkan @Timed pada service methods
@Bean
public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
}
```

**Analisis improvement dari data profiling:**
- Kalau `auth.login MAX` > 500ms → bottleneck di BCrypt hashing atau DB query
- Kalau `auth.token.rotate MAX` > 200ms → bottleneck di SHA-256 hash atau DB write
- Kalau `http.server.requests` untuk `/api/auth/refresh` tinggi → pertimbangkan caching user lookup

**Yang diucapkan:**
> "Profiling diimplementasikan menggunakan Micrometer `@Timed` annotation pada key service methods. Dari data ini kita bisa lihat bahwa operasi login rata-rata selesai dalam X ms, dengan max di Y ms. Bottleneck utama ada di BCrypt password verification yang memang by-design lambat untuk keamanan — cost factor 10 artinya setiap hash butuh ~100ms. Improvement yang bisa dilakukan adalah menambahkan Redis cache untuk user lookup agar tidak selalu query DB di setiap request."

---

### Demo 7 — README & Dokumentasi Final

**Lokasi:** Buka `README.md` di GitHub atau editor

**Yang ditunjukkan:**
- Quick start dengan Docker Compose
- Cara setup lokal tanpa Docker
- Cara menjalankan test dan quality checks
- Penjelasan CI/CD pipeline

**Yang diucapkan:**
> "README mencakup semua yang dibutuhkan developer baru untuk langsung contribute: cara setup, cara test, cara deploy, dan penjelasan arsitektur. Kombinasi README + OpenAPI spec di Swagger menjadi dokumentasi lengkap service ini."

---

## Tips Presentasi

### Urutan Demo yang Disarankan
```
M25: Register → Login → GET /me (5 menit)
M50: Refresh → Logout → Reset Password → Admin Disable → Swagger (10 menit)
M75: 2FA Setup → Login OTP → Rate Limit → Sessions → Event Log → CI (15 menit)
M100: Tests → Coverage → Security Checklist → CD → Monitoring → Profiling → README (10 menit)
```

### Kalau Ada Error Saat Demo
| Error | Solusi Cepat |
|-------|-------------|
| 401 di Swagger | Klik Authorize → paste token baru dari login |
| Frontend tidak bisa connect | Cek terminal backend masih running |
| 2FA kode salah | Tunggu 30 detik, kode berganti setiap 30 detik |
| Rate limit kena | Restart backend (`Ctrl+C` → `./gradlew bootRun ...`) — bucket reset |
| `/actuator/health` 404 | Backend belum restart setelah tambah dependency actuator |
| `auth.login` metric tidak muncul | Lakukan minimal 1 login dulu — metric baru muncul setelah method dipanggil |

### File Kode Paling Penting (urutan kepentingan)
1. `src/main/java/.../service/AuthService.java` — logika utama semua flow
2. `src/main/java/.../service/RefreshTokenService.java` — token rotation & reuse detection
3. `src/main/java/.../config/SecurityConfig.java` — aturan autentikasi
4. `src/main/java/.../config/JwtAuthFilter.java` — interceptor JWT
5. `src/main/java/.../service/TotpService.java` — 2FA
6. `src/main/java/.../service/RateLimitService.java` — rate limiting
7. `src/main/resources/db/migration/` — evolusi skema DB per milestone
