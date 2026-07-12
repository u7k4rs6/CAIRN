package dev.cairn.api.repo;

import dev.cairn.api.domain.PersonalAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalAccessTokenJpaRepository extends JpaRepository<PersonalAccessToken, Long> {
    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);
}
