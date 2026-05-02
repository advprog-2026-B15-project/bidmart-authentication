package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class SessionResponse {

    private final UUID id;
    private final String deviceInfo;
    private final String ipAddress;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
}
