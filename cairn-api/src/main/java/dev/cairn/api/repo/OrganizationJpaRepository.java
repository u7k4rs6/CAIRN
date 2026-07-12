package dev.cairn.api.repo;

import dev.cairn.api.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationJpaRepository extends JpaRepository<Organization, Long> {
}
