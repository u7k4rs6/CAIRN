package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Organization;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.repo.OrganizationJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * FR-REPO-1: a user or organization can create a repository with a visibility
 * level. An {@code org} name in the request creates an org-owned repo, gated on the
 * caller administering that org, so a repo can actually end up owned by an
 * organization through a real user action rather than only in test fixtures (the
 * gap the gap-closure audit found: {@link Organization}/{@code Team} grants existed
 * fully tested but a repo could only ever be user-owned via this endpoint).
 */
@RestController
@RequestMapping("/api/repos")
public class RepoController {

    private final RepoJpaRepository repos;
    private final OrganizationJpaRepository organizations;
    private final PrincipalResolver principalResolver;

    public RepoController(RepoJpaRepository repos, OrganizationJpaRepository organizations,
                           PrincipalResolver principalResolver) {
        this.repos = repos;
        this.organizations = organizations;
        this.principalResolver = principalResolver;
    }

    public record CreateRepoRequest(String name, Visibility visibility, String org) {
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRepoRequest body, HttpServletRequest request) {
        Principal principal = principalResolver.resolve(request);
        if (!(principal instanceof Principal.UserPrincipal userPrincipal)) {
            return ResponseEntity.status(401).build();
        }
        Visibility visibility = body.visibility() == null ? Visibility.PRIVATE : body.visibility();
        Repo repo;
        String ownerName;
        if (body.org() != null && !body.org().isBlank()) {
            Organization org = organizations.findByName(body.org()).orElse(null);
            if (org == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "no such organization '" + body.org() + "'"));
            }
            if (!org.isAdmin(userPrincipal.user())) {
                return ResponseEntity.status(403).body(Map.of("error", "admin required on organization '" + body.org() + "'"));
            }
            repo = repos.save(new Repo(body.name(), null, org, visibility));
            ownerName = org.name();
        } else {
            repo = repos.save(new Repo(body.name(), userPrincipal.user(), null, visibility));
            ownerName = userPrincipal.user().username();
        }
        return ResponseEntity.ok(Map.of(
                "id", repo.id(),
                "name", repo.name(),
                "visibility", repo.visibility(),
                "owner", ownerName));
    }
}
