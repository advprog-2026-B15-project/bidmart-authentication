package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.dto;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UserResponse {

    private final UUID id;
    private final String email;
    private final String username;
    private final String role;
    private final boolean enabled;

    public UserResponse(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
    }
}
