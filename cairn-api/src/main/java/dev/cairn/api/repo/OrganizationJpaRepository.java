package dev.cairn.api.repo;

import dev.cairn.api.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationJpaRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByName(String name);
}
