package dev.cairn.api.repo;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Team;
import dev.cairn.api.domain.TeamGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamGrantJpaRepository extends JpaRepository<TeamGrant, Long> {
    Optional<TeamGrant> findByTeamAndRepo(Team team, Repo repo);
}
