package dev.cairn.api.rest;

import dev.cairn.api.activity.ActivityEvent;
import dev.cairn.api.activity.ActivityPublisher;
import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.collab.Comment;
import dev.cairn.api.collab.Issue;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.CommentJpaRepository;
import dev.cairn.api.repo.IssueJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** FR-COLLAB-1: open and read issues on a repo the caller can read. */
@RestController
@RequestMapping("/api/repos/{owner}/{name}/issues")
public class IssueController {

    private final RepoJpaRepository repos;
    private final IssueJpaRepository issues;
    private final CommentJpaRepository comments;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;
    private final ActivityPublisher activityPublisher;

    public IssueController(RepoJpaRepository repos, IssueJpaRepository issues, CommentJpaRepository comments,
                            PrincipalResolver principalResolver, PermissionResolver permissionResolver,
                            ActivityPublisher activityPublisher) {
        this.repos = repos;
        this.issues = issues;
        this.comments = comments;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
        this.activityPublisher = activityPublisher;
    }

    public record CreateIssueRequest(String title, String body) {
    }

    public record CommentRequest(String body) {
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable String owner, @PathVariable String name,
                                     @RequestBody CreateIssueRequest request, HttpServletRequest httpRequest) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        Principal principal = principalResolver.resolve(httpRequest);
        if (!permissionResolver.authorize(principal, repo, Role.READ)) {
            return ResponseEntity.notFound().build();
        }
        if (!(principal instanceof Principal.UserPrincipal up)) {
            return ResponseEntity.status(401).build();
        }
        Issue issue = issues.save(new Issue(repo, up.user(), request.title(), request.body()));
        activityPublisher.publish(new ActivityEvent(repo.id(), repo.ownerName() + "/" + repo.name(),
                up.user().username(), "issue_opened", "opened issue \"" + issue.title() + "\"",
                System.currentTimeMillis() / 1000));
        return ResponseEntity.ok(issue);
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String owner, @PathVariable String name, HttpServletRequest httpRequest) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null || !permissionResolver.authorize(principalResolver.resolve(httpRequest), repo, Role.READ)) {
            return ResponseEntity.notFound().build();
        }
        List<Issue> all = issues.findAll().stream().filter(i -> i.repo().id().equals(repo.id())).toList();
        return ResponseEntity.ok(all);
    }

    @PostMapping("/{issueId}/comments")
    public ResponseEntity<?> comment(@PathVariable String owner, @PathVariable String name, @PathVariable Long issueId,
                                      @RequestBody CommentRequest request, HttpServletRequest httpRequest) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        Issue issue = issues.findById(issueId).orElse(null);
        if (repo == null || issue == null) {
            return ResponseEntity.notFound().build();
        }
        Principal principal = principalResolver.resolve(httpRequest);
        if (!permissionResolver.authorize(principal, repo, Role.READ)) {
            return ResponseEntity.notFound().build();
        }
        if (!(principal instanceof Principal.UserPrincipal up)) {
            return ResponseEntity.status(401).build();
        }
        Comment saved = comments.save(Comment.onIssue(issue, up.user(), request.body()));
        return ResponseEntity.ok(saved);
    }
}
