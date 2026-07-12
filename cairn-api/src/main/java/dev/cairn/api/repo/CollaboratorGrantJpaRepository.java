package dev.cairn.api.repo;

import dev.cairn.api.domain.CollaboratorGrant;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollaboratorGrantJpaRepository extends JpaRepository<CollaboratorGrant, Long> {
    Optional<CollaboratorGrant> findByUserAndRepo(User user, Repo repo);

    List<CollaboratorGrant> findByRepo(Repo repo);
}
