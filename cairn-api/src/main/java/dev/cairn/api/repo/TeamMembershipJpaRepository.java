package dev.cairn.api.repo;

import dev.cairn.api.domain.TeamMembership;
import dev.cairn.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMembershipJpaRepository extends JpaRepository<TeamMembership, Long> {
    List<TeamMembership> findByUser(User user);
}
