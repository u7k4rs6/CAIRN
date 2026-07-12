package dev.cairn.api.git;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves or auto-creates the {@link Repo} metadata row for an {@code owner/name}
 * pair. Auto-created repos (the path any Git-over-HTTP request takes before a real
 * {@code POST /api/repos} call has ever happened for that name) default to
 * {@link Visibility#PUBLIC}: a safe default, since a repo nobody explicitly locked
 * down should behave like M5 did, not silently start requiring a grant.
 */
@Component
public class RepoService {

    private final RepoJpaRepository repos;
    private final UserJpaRepository users;

    public RepoService(RepoJpaRepository repos, UserJpaRepository users) {
        this.repos = repos;
        this.users = users;
    }

    public Repo resolveOrCreate(String owner, String name) {
        return repos.findByOwnerAndName(owner, name).orElseGet(() -> {
            User ownerUser = users.findByUsername(owner)
                    .orElseGet(() -> users.save(new User(owner, owner + "@cairn.local", "")));
            return repos.save(new Repo(name, ownerUser, null, Visibility.PUBLIC));
        });
    }
}
