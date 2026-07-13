package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.collab.Milestone;
import dev.cairn.api.collab.MilestoneState;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.MilestoneJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** PRD Tier 2: milestones on issues, gated at {@code triage} like labels. */
@RestController
@RequestMapping("/api/repos/{owner}/{name}/milestones")
public class MilestoneController {

    private final RepoJpaRepository repos;
    private final MilestoneJpaRepository milestones;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;

    public MilestoneController(RepoJpaRepository repos, MilestoneJpaRepository milestones,
                                PrincipalResolver principalResolver, PermissionResolver permissionResolver) {
        this.repos = repos;
        this.milestones = milestones;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
    }

    public record CreateMilestoneRequest(String title, String description, Instant dueAt) {
    }

    public record MilestoneView(Long id, String title, String description, Instant dueAt, MilestoneState state) {
    }

    static MilestoneView toView(Milestone m) {
        return new MilestoneView(m.id(), m.title(), m.description(), m.dueAt(), m.state());
    }

    private Repo requireRepo(String owner, String name, HttpServletRequest request, Role role) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null || !permissionResolver.authorize(principalResolver.resolve(request), repo, role)) {
            return null;
        }
        return repo;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable String owner, @PathVariable String name, HttpServletRequest request) {
        Repo repo = requireRepo(owner, name, request, Role.READ);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        List<MilestoneView> views = milestones.findByRepo(repo).stream().map(MilestoneController::toView).toList();
        return ResponseEntity.ok(views);
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable String owner, @PathVariable String name,
                                     @RequestBody CreateMilestoneRequest body, HttpServletRequest request) {
        Repo repo = requireRepo(owner, name, request, Role.TRIAGE);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.title() == null || body.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title is required"));
        }
        Milestone saved = milestones.save(new Milestone(repo, body.title(), body.description(), body.dueAt()));
        return ResponseEntity.ok(toView(saved));
    }

    @PutMapping("/{id}/state")
    public ResponseEntity<?> setState(@PathVariable String owner, @PathVariable String name, @PathVariable Long id,
                                       @RequestBody MilestoneState state, HttpServletRequest request) {
        Repo repo = requireRepo(owner, name, request, Role.TRIAGE);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        Milestone milestone = milestones.findById(id).orElse(null);
        if (milestone == null || !milestone.repo().id().equals(repo.id())) {
            return ResponseEntity.notFound().build();
        }
        milestone.transitionTo(state);
        milestones.save(milestone);
        return ResponseEntity.ok(toView(milestone));
    }
}
