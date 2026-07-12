package dev.cairn.api.rest;

import dev.cairn.api.activity.ActivityEvent;
import dev.cairn.api.activity.ActivityPublisher;
import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.collab.MergeStrategy;
import dev.cairn.api.collab.PullRequest;
import dev.cairn.api.collab.PullRequestService;
import dev.cairn.api.collab.Review;
import dev.cairn.api.collab.ReviewVerdict;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.PullRequestJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.ReviewJpaRepository;
import dev.cairn.vcs.object.PersonIdent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** FR-COLLAB-2/3/4: open, review, and merge pull requests. */
@RestController
@RequestMapping("/api/repos/{owner}/{name}/pulls")
public class PullRequestController {

    private final RepoJpaRepository repos;
    private final PullRequestJpaRepository pullRequests;
    private final ReviewJpaRepository reviews;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;
    private final PullRequestService pullRequestService;
    private final ActivityPublisher activityPublisher;

    public PullRequestController(RepoJpaRepository repos, PullRequestJpaRepository pullRequests,
                                  ReviewJpaRepository reviews, PrincipalResolver principalResolver,
                                  PermissionResolver permissionResolver, PullRequestService pullRequestService,
                                  ActivityPublisher activityPublisher) {
        this.repos = repos;
        this.pullRequests = pullRequests;
        this.reviews = reviews;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
        this.pullRequestService = pullRequestService;
        this.activityPublisher = activityPublisher;
    }

    public record CreatePullRequestRequest(String title, String sourceRef, String targetRef) {
    }

    public record ReviewRequest(ReviewVerdict verdict, String body, String path, Integer line) {
    }

    public record MergeRequest(MergeStrategy strategy, String message) {
    }

    private Repo requireReadableRepo(String owner, String name, HttpServletRequest request) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null || !permissionResolver.authorize(principalResolver.resolve(request), repo, Role.READ)) {
            return null;
        }
        return repo;
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable String owner, @PathVariable String name,
                                     @RequestBody CreatePullRequestRequest body, HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        Principal principal = principalResolver.resolve(request);
        if (!(principal instanceof Principal.UserPrincipal up)) {
            return ResponseEntity.status(401).build();
        }
        PullRequest pr = pullRequests.save(new PullRequest(repo, up.user(), body.title(), body.sourceRef(), body.targetRef()));
        activityPublisher.publish(new ActivityEvent(repo.id(), repo.ownerName() + "/" + repo.name(),
                up.user().username(), "pr_opened",
                "opened pull request \"" + pr.title() + "\" (" + body.sourceRef() + " -> " + body.targetRef() + ")",
                System.currentTimeMillis() / 1000));
        return ResponseEntity.ok(pr);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String owner, @PathVariable String name, HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        List<PullRequest> all = pullRequests.findAll().stream().filter(pr -> pr.repo().id().equals(repo.id())).toList();
        return ResponseEntity.ok(all);
    }

    @PostMapping("/{number}/reviews")
    public ResponseEntity<?> review(@PathVariable String owner, @PathVariable String name, @PathVariable Long number,
                                     @RequestBody ReviewRequest body, HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        PullRequest pr = pullRequests.findById(number).orElse(null);
        if (repo == null || pr == null) {
            return ResponseEntity.notFound().build();
        }
        Principal principal = principalResolver.resolve(request);
        if (!(principal instanceof Principal.UserPrincipal up)) {
            return ResponseEntity.status(401).build();
        }
        Review saved = reviews.save(new Review(pr, up.user(), body.verdict(), body.body(), body.path(), body.line()));
        pr.transitionTo(switch (body.verdict()) {
            case APPROVE -> pr.state().approve();
            case REQUEST_CHANGES -> pr.state().requestChanges();
            case COMMENT -> pr.state();
        });
        pullRequests.save(pr);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{number}/merge")
    public ResponseEntity<?> merge(@PathVariable String owner, @PathVariable String name, @PathVariable Long number,
                                    @RequestBody MergeRequest body, HttpServletRequest request) {
        Repo repo = requireReadableRepo(owner, name, request);
        PullRequest pr = pullRequests.findById(number).orElse(null);
        if (repo == null || pr == null) {
            return ResponseEntity.notFound().build();
        }
        Principal principal = principalResolver.resolve(request);
        if (principal instanceof Principal.AnonymousPrincipal) {
            return ResponseEntity.status(401).build();
        }
        PersonIdent ident = new PersonIdent(
                principal instanceof Principal.UserPrincipal up ? up.user().username() : "unknown",
                principal instanceof Principal.UserPrincipal up ? up.user().email() : "unknown@cairn.dev",
                System.currentTimeMillis() / 1000, "+0000");
        try {
            var result = pullRequestService.merge(pr, principal, body.strategy(), ident, ident,
                    body.message() != null ? body.message() : pr.title());
            pullRequests.save(pr);
            if (!result.isClean()) {
                return ResponseEntity.status(409).body(result.conflicts());
            }
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
}
