package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.service;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void loadUserByUsernameReturnsUserDetailsForKnownEmail() {
        User user = buildUser("user@example.com", "hash", "BUYER", true, false);
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("user@example.com");

        assertNotNull(details);
        assertEquals("user@example.com", details.getUsername());
        assertEquals("hash", details.getPassword());
    }

    @Test
    void loadUserByUsernameThrowsWhenEmailNotFound() {
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown@example.com"));
    }

    @Test
    void loadUserByUsernameReturnsDisabledWhenUserNotEnabled() {
        User user = buildUser("disabled@example.com", "hash", "BUYER", false, false);
        given(userRepository.findByEmail("disabled@example.com")).willReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("disabled@example.com");

        assertFalse(details.isEnabled());
    }

    @Test
    void loadUserByUsernameReturnsLockedWhenUserIsLocked() {
        User user = buildUser("locked@example.com", "hash", "BUYER", true, true);
        given(userRepository.findByEmail("locked@example.com")).willReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("locked@example.com");

        assertFalse(details.isAccountNonLocked());
    }

    @Test
    void loadUserByUsernameGrantsRoleFromUser() {
        User user = buildUser("admin@example.com", "hash", "ADMIN", true, false);
        given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("admin@example.com");

        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    private User buildUser(String email, String passwordHash, String role,
                           boolean enabled, boolean locked) {
        User user = new User();
        user.setEmail(email);
        user.setUsername("testuser");
        user.setPasswordHash(passwordHash);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setLocked(locked);
        return user;
    }
}
