package dev.cairn.api.rest;

import dev.cairn.api.activity.ActivityEvent;
import dev.cairn.api.activity.InMemoryActivityFeed;
import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.RepoJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reads back what {@link dev.cairn.api.activity.ActivityPublisher} fanned out (M9's
 * Observer pattern demonstration): the same {@code read}-gated visibility rule as
 * every other repo endpoint, since an activity feed is exactly the kind of thing a
 * private repo must not leak through.
 */
@RestController
@RequestMapping("/api/repos/{owner}/{name}/activity")
public class ActivityController {

    private final RepoJpaRepository repos;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;
    private final InMemoryActivityFeed feed;

    public ActivityController(RepoJpaRepository repos, PrincipalResolver principalResolver,
                               PermissionResolver permissionResolver, InMemoryActivityFeed feed) {
        this.repos = repos;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
        this.feed = feed;
    }

    @GetMapping
    public ResponseEntity<?> recent(@PathVariable String owner, @PathVariable String name, HttpServletRequest request) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null || !permissionResolver.authorize(principalResolver.resolve(request), repo, Role.READ)) {
            return ResponseEntity.notFound().build();
        }
        List<ActivityEvent> events = feed.recent(repo.id());
        return ResponseEntity.ok(events);
    }
}
