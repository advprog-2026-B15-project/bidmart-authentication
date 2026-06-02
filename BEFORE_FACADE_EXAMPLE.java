@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository tokenRepository;
    private final UserEventRepository userEventRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.status(409).body("Email already registered");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(encoder.encode(request.getPassword()));
        user.setEnabled(false);
        userRepository.save(user);

        String rawToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        tokenRepository.save(new VerificationToken(rawToken, user, expiresAt));
        userEventRepository.save(new UserEvent("user.registered", user.getId()));

        return ResponseEntity.ok(Map.of("verificationToken", rawToken));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isTotpEnabled()) {
            String mfaToken = Jwts.builder()
                .subject(user.getEmail())
                .claim("scope", "mfa")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
            return ResponseEntity.ok(Map.of("requiresMfa", true, "mfaToken", mfaToken));
        }

        String accessToken = Jwts.builder()
            .subject(user.getEmail())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 86_400_000))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
            .compact();

        String rawRefreshToken = UUID.randomUUID().toString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                rawRefreshToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) sb.append(String.format("%02x", b));
            String tokenHash = sb.toString();

            UUID familyId = UUID.randomUUID();
            refreshTokenRepository.save(new RefreshToken(
                tokenHash, user,
                LocalDateTime.now().plusDays(7), familyId,
                httpRequest.getHeader("User-Agent"),
                httpRequest.getRemoteAddr()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        userEventRepository.save(new UserEvent("user.logged.in", user.getId()));

        return ResponseEntity.ok(
            Map.of("accessToken", accessToken,
                   "refreshToken", rawRefreshToken,
                   "tokenType", "Bearer"));
    }
}
