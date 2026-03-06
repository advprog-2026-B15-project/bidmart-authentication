package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegisterResponse {

    private String message;

    /**
     * Returned in dev/test so callers can verify email without a real mail server.
     * In production this would be omitted and sent via email only.
     */
    private String verificationToken;
}
