package dev.cairn.api.repo;

import dev.cairn.api.collab.Label;
import dev.cairn.api.domain.Repo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LabelJpaRepository extends JpaRepository<Label, Long> {
    List<Label> findByRepo(Repo repo);

    Optional<Label> findByRepoAndName(Repo repo, String name);
}
