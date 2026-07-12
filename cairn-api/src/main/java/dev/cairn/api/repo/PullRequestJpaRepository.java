package dev.cairn.api.repo;

import dev.cairn.api.collab.PullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestJpaRepository extends JpaRepository<PullRequest, Long> {
}
