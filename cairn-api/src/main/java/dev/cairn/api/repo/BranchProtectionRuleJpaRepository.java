package dev.cairn.api.repo;

import dev.cairn.api.domain.BranchProtectionRule;
import dev.cairn.api.domain.Repo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchProtectionRuleJpaRepository extends JpaRepository<BranchProtectionRule, Long> {
    List<BranchProtectionRule> findByRepo(Repo repo);

    Optional<BranchProtectionRule> findByRepoAndRef(Repo repo, String ref);
}
