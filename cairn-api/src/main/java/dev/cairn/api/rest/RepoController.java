package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.repo.RepoJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** FR-REPO-1: a user can create a repository with a visibility level. */
@RestController
@RequestMapping("/api/repos")
public class RepoController {

    private final RepoJpaRepository repos;
    private final PrincipalResolver principalResolver;

    public RepoController(RepoJpaRepository repos, PrincipalResolver principalResolver) {
        this.repos = repos;
        this.principalResolver = principalResolver;
    }

    public record CreateRepoRequest(String name, Visibility visibility) {
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRepoRequest body, HttpServletRequest request) {
        Principal principal = principalResolver.resolve(request);
        if (!(principal instanceof Principal.UserPrincipal userPrincipal)) {
            return ResponseEntity.status(401).build();
        }
        Repo repo = repos.save(new Repo(body.name(), userPrincipal.user(), null,
                body.visibility() == null ? Visibility.PRIVATE : body.visibility()));
        return ResponseEntity.ok(Map.of(
                "id", repo.id(),
                "name", repo.name(),
                "visibility", repo.visibility(),
                "owner", userPrincipal.user().username()));
    }
}
