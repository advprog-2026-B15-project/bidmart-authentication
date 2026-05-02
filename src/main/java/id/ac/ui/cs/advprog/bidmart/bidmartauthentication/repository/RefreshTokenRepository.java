package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.repository;

import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.RefreshToken;
import id.ac.ui.cs.advprog.bidmart.bidmartauthentication.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    List<RefreshToken> findByUserAndRevokedFalseAndExpiresAtAfter(User user, LocalDateTime now);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.familyId = :familyId")
    void revokeAllByFamilyId(@Param("familyId") UUID familyId, @Param("now") LocalDateTime now);

    void deleteAllByUser(User user);
}
