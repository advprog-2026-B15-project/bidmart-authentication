package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import lombok.Getter;

@Getter
public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;

    public AuthResponse(String accessToken, String refreshToken, String tokenType) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
    }
}
