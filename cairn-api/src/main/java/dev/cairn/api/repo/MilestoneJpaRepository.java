package dev.cairn.api.repo;

import dev.cairn.api.collab.Milestone;
import dev.cairn.api.domain.Repo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestoneJpaRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByRepo(Repo repo);
}
