package dev.cairn.api.repo;

import dev.cairn.api.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamJpaRepository extends JpaRepository<Team, Long> {
}
