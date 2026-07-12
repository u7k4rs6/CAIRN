package dev.cairn.api.repo;

import dev.cairn.api.domain.Organization;
import dev.cairn.api.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamJpaRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByOrganizationAndName(Organization organization, String name);

    List<Team> findByOrganization(Organization organization);
}
