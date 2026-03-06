package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String tokenType;
}
