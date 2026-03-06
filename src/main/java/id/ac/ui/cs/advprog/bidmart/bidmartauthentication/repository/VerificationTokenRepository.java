package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByToken(String token);
}
