package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.AuthResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.ForgotPasswordRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.LoginRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RefreshTokenRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RegisterRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RegisterResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.ResetPasswordRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.TotpSetupResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.PasswordResetToken;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.VerificationToken;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.PasswordResetTokenRepository;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.UserRepository;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);
    private static final int TOKEN_EXPIRY_HOURS = 24;
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 30;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final TotpService totpService;
    private final UserEventPublisher eventPublisher;

    public AuthService(UserRepository userRepository,
                       VerificationTokenRepository tokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService,
                       TotpService totpService,
                       UserEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.totpService = totpService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        String rawToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS);
        tokenRepository.save(new VerificationToken(rawToken, user, expiresAt));

        LOG.info("[MOCK EMAIL] Verification token for {}: {}", user.getEmail(), rawToken);
        eventPublisher.publishUserRegistered(user);

        return new RegisterResponse("Registration successful. Please verify your email.", rawToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        VerificationToken vt = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (vt.isUsed()) {
            throw new IllegalStateException("Verification token already used");
        }
        if (vt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Verification token expired");
        }

        vt.getUser().setEnabled(true);
        vt.setUsed(true);
        eventPublisher.publishUserEmailVerified(vt.getUser());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo, String ipAddress) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        if (user.isTotpEnabled()) {
            String mfaToken = jwtService.generateMfaToken(user.getEmail());
            return AuthResponse.requireMfa(mfaToken);
        }

        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user, deviceInfo, ipAddress);
        eventPublisher.publishUserLoggedIn(user);
        return new AuthResponse(accessToken, refreshToken, "Bearer");
    }

    @Transactional
    public AuthResponse verifyMfaAndLogin(String mfaToken, String code,
            String deviceInfo, String ipAddress) {
        String email = jwtService.extractMfaEmail(mfaToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new IllegalArgumentException("2FA is not enabled for this account");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        String accessToken = jwtService.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user, deviceInfo, ipAddress);
        eventPublisher.publishUserLoggedIn(user);
        return new AuthResponse(accessToken, refreshToken, "Bearer");
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String[] newRawTokenHolder = new String[1];
        User user = refreshTokenService.rotateRefreshToken(request.getRefreshToken(), newRawTokenHolder);
        String accessToken = jwtService.generateToken(user.getEmail());
        return new AuthResponse(accessToken, newRawTokenHolder[0], "Bearer");
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeToken(request.getRefreshToken());
    }

    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String rawToken = UUID.randomUUID().toString();
            String tokenHash = hashToken(rawToken);
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES);
            passwordResetTokenRepository.save(new PasswordResetToken(tokenHash, user, expiresAt));
            LOG.info("[MOCK EMAIL] Password reset token for {}: {}", user.getEmail(), rawToken);
        });
        return "If the email is registered, a reset link has been sent.";
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hashToken(request.getToken());
        PasswordResetToken prt = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid password reset token"));

        if (prt.isUsed()) {
            throw new IllegalArgumentException("Password reset token already used");
        }
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Password reset token expired");
        }

        prt.getUser().setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        prt.setUsed(true);
        refreshTokenService.revokeAllForUser(prt.getUser());
        eventPublisher.publishPasswordReset(prt.getUser());
    }

    @Transactional
    public TotpSetupResponse setupTotp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        String otpAuthUrl = totpService.getOtpAuthUrl(secret, email);
        return new TotpSetupResponse(secret, otpAuthUrl);
    }

    @Transactional
    public void confirmTotp(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (user.getTotpSecret() == null) {
            throw new IllegalStateException("TOTP setup not initiated. Call /2fa/setup first.");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }
        user.setTotpEnabled(true);
    }

    @Transactional
    public void disableTotp(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        if (!user.isTotpEnabled()) {
            throw new IllegalStateException("2FA is not enabled for this account");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
