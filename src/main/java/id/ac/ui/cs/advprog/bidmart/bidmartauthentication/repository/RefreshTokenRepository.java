package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.RefreshToken;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUser(User user);
}
