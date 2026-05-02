package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import lombok.Getter;

@Getter
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final boolean mfaRequired;
    private final String mfaToken;

    public AuthResponse(String accessToken, String refreshToken, String tokenType) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.mfaRequired = false;
        this.mfaToken = null;
    }

    public static AuthResponse requireMfa(String mfaToken) {
        return new AuthResponse(null, null, "Bearer", true, mfaToken);
    }

    private AuthResponse(String accessToken, String refreshToken, String tokenType,
            boolean mfaRequired, String mfaToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.mfaRequired = mfaRequired;
        this.mfaToken = mfaToken;
    }
}
