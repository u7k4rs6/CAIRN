package dev.cairn.api.repo;

import dev.cairn.api.collab.PullRequest;
import dev.cairn.api.domain.Repo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PullRequestJpaRepository extends JpaRepository<PullRequest, Long> {
    Optional<PullRequest> findByRepoAndTitle(Repo repo, String title);
}
