package dev.cairn.api.rest;

import dev.cairn.api.activity.ActivityEvent;
import dev.cairn.api.activity.ActivityPublisher;
import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.collab.Comment;
import dev.cairn.api.collab.Issue;
import dev.cairn.api.collab.IssueState;
import dev.cairn.api.collab.Label;
import dev.cairn.api.collab.Milestone;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.User;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.CommentJpaRepository;
import dev.cairn.api.repo.IssueJpaRepository;
import dev.cairn.api.repo.LabelJpaRepository;
import dev.cairn.api.repo.MilestoneJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** FR-COLLAB-1: open and read issues on a repo the caller can read. */
@RestController
@RequestMapping("/api/repos/{owner}/{name}/issues")
public class IssueController {

    private final RepoJpaRepository repos;
    private final IssueJpaRepository issues;
    private final CommentJpaRepository comments;
    private final LabelJpaRepository labels;
    private final MilestoneJpaRepository milestones;
    private final UserJpaRepository users;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;
    private final ActivityPublisher activityPublisher;

    public IssueController(RepoJpaRepository repos, IssueJpaRepository issues, CommentJpaRepository comments,
                            LabelJpaRepository labels, MilestoneJpaRepository milestones, UserJpaRepository users,
                            PrincipalResolver principalResolver, PermissionResolver permissionResolver,
                            ActivityPublisher activityPublisher) {
        this.repos = repos;
        this.issues = issues;
        this.comments = comments;
        this.labels = labels;
        this.milestones = milestones;
        this.users = users;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
        this.activityPublisher = activityPublisher;
    }

    public record CreateIssueRequest(String title, String body) {
    }

    public record CommentRequest(String body) {
    }

    public record LabelIdRequest(Long labelId) {
    }

    public record AssigneeRequest(String username) {
    }

    public record MilestoneIdRequest(Long milestoneId) {
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

    /** Frontend spec, section 5.7's FilterBar: open/closed, label, and assignee, applied in whatever combination the caller asks for. */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable String owner, @PathVariable String name,
                                   @RequestParam(required = false) IssueState state,
                                   @RequestParam(required = false) String label,
                                   @RequestParam(required = false) String assignee,
                                   @RequestParam(required = false) Long milestone,
                                   HttpServletRequest httpRequest) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null || !permissionResolver.authorize(principalResolver.resolve(httpRequest), repo, Role.READ)) {
            return ResponseEntity.notFound().build();
        }
        List<Issue> filtered = issues.findAll().stream()
                .filter(i -> i.repo().id().equals(repo.id()))
                .filter(i -> state == null || i.state() == state)
                .filter(i -> label == null || i.labels().stream().anyMatch(l -> l.name().equals(label)))
                .filter(i -> assignee == null || i.assignees().stream().anyMatch(u -> u.username().equals(assignee)))
                .filter(i -> milestone == null || (i.milestone() != null && milestone.equals(i.milestone().id())))
                .toList();
        return ResponseEntity.ok(filtered);
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

    private Issue requireTriageIssue(String owner, String name, Long issueId, HttpServletRequest request) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        Issue issue = issues.findById(issueId).orElse(null);
        if (repo == null || issue == null || !issue.repo().id().equals(repo.id())) {
            return null;
        }
        return permissionResolver.authorize(principalResolver.resolve(request), repo, Role.TRIAGE) ? issue : null;
    }

    @PostMapping("/{issueId}/labels")
    public ResponseEntity<?> addLabel(@PathVariable String owner, @PathVariable String name, @PathVariable Long issueId,
                                       @RequestBody LabelIdRequest body, HttpServletRequest request) {
        Issue issue = requireTriageIssue(owner, name, issueId, request);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        Label label = labels.findById(body.labelId()).orElse(null);
        if (label == null || !label.repo().id().equals(issue.repo().id())) {
            return ResponseEntity.badRequest().body(Map.of("error", "no such label on this repo"));
        }
        issue.addLabel(label);
        issues.save(issue);
        return ResponseEntity.ok(issue);
    }

    @DeleteMapping("/{issueId}/labels/{labelId}")
    public ResponseEntity<?> removeLabel(@PathVariable String owner, @PathVariable String name, @PathVariable Long issueId,
                                          @PathVariable Long labelId, HttpServletRequest request) {
        Issue issue = requireTriageIssue(owner, name, issueId, request);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        labels.findById(labelId).ifPresent(issue::removeLabel);
        issues.save(issue);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{issueId}/assignees")
    public ResponseEntity<?> addAssignee(@PathVariable String owner, @PathVariable String name, @PathVariable Long issueId,
                                          @RequestBody AssigneeRequest body, HttpServletRequest request) {
        Issue issue = requireTriageIssue(owner, name, issueId, request);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        User user = users.findByUsername(body.username()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "no such user '" + body.username() + "'"));
        }
        issue.addAssignee(user);
        issues.save(issue);
        return ResponseEntity.ok(issue);
    }

    @DeleteMapping("/{issueId}/assignees/{username}")
    public ResponseEntity<?> removeAssignee(@PathVariable String owner, @PathVariable String name, @PathVariable Long issueId,
                                             @PathVariable String username, HttpServletRequest request) {
        Issue issue = requireTriageIssue(owner, name, issueId, request);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        users.findByUsername(username).ifPresent(issue::removeAssignee);
        issues.save(issue);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{issueId}/milestone")
    public ResponseEntity<?> setMilestone(@PathVariable String owner, @PathVariable String name, @PathVariable Long issueId,
                                           @RequestBody MilestoneIdRequest body, HttpServletRequest request) {
        Issue issue = requireTriageIssue(owner, name, issueId, request);
        if (issue == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.milestoneId() == null) {
            issue.setMilestone(null);
        } else {
            Milestone milestone = milestones.findById(body.milestoneId()).orElse(null);
            if (milestone == null || !milestone.repo().id().equals(issue.repo().id())) {
                return ResponseEntity.badRequest().body(Map.of("error", "no such milestone on this repo"));
            }
            issue.setMilestone(milestone);
        }
        issues.save(issue);
        return ResponseEntity.ok(issue);
    }
}
