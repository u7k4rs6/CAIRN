package dev.cairn.api.repo;

import dev.cairn.api.collab.Issue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueJpaRepository extends JpaRepository<Issue, Long> {
}
