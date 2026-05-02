package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TotpCodeRequest {

    @NotBlank
    private String code;
}
