package dev.cairn.api.repo;

import dev.cairn.api.domain.PersonalAccessToken;
import dev.cairn.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalAccessTokenJpaRepository extends JpaRepository<PersonalAccessToken, Long> {
    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    /** The raw token behind an existing row can't be recovered (only its hash is stored) - callers use this to avoid minting a redundant one, not to reprint the original. */
    boolean existsByUser(User user);
}
