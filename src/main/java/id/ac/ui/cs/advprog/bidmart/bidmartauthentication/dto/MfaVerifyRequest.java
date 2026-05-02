package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MfaVerifyRequest {

    @NotBlank
    private String mfaToken;

    @NotBlank
    private String code;
}
