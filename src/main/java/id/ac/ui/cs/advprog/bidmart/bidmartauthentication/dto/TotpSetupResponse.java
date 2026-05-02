package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TotpSetupResponse {

    private final String secret;
    private final String otpAuthUrl;
}
