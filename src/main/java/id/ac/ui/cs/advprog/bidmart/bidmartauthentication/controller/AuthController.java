package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.controller;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.AuthResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.ForgotPasswordRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.LoginRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.MfaVerifyRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RefreshTokenRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RegisterRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.RegisterResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.ResetPasswordRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.TotpCodeRequest;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto.TotpSetupResponse;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service.AuthService;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    public AuthController(AuthService authService, RateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (!rateLimitService.isLoginAllowed(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        try {
            String deviceInfo = httpRequest.getHeader("User-Agent");
            AuthResponse response = authService.login(request, deviceInfo, ip);
            return ResponseEntity.ok(response);
        } catch (DisabledException | LockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refresh(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        String message = authService.forgotPassword(request);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<TotpSetupResponse> setupTotp(
            @AuthenticationPrincipal UserDetails userDetails) {
        TotpSetupResponse response = authService.setupTotp(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/confirm")
    public ResponseEntity<Map<String, String>> confirmTotp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TotpCodeRequest request) {
        try {
            authService.confirmTotp(userDetails.getUsername(), request.getCode());
            return ResponseEntity.ok(Map.of("message", "2FA enabled successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest httpRequest) {
        try {
            String deviceInfo = httpRequest.getHeader("User-Agent");
            String ip = httpRequest.getRemoteAddr();
            AuthResponse response = authService.verifyMfaAndLogin(
                    request.getMfaToken(), request.getCode(), deviceInfo, ip);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<Map<String, String>> disableTotp(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TotpCodeRequest request) {
        try {
            authService.disableTotp(userDetails.getUsername(), request.getCode());
            return ResponseEntity.ok(Map.of("message", "2FA disabled successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
