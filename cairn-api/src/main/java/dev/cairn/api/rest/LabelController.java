package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.collab.Label;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.LabelJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * PRD Tier 2: labels on issues. Gated at {@code triage} (security doc, section
 * 3.2: "manage issues and PRs without code write"), the same role real GitHub
 * requires for label administration.
 */
@RestController
@RequestMapping("/api/repos/{owner}/{name}/labels")
public class LabelController {

    private final RepoJpaRepository repos;
    private final LabelJpaRepository labels;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;

    public LabelController(RepoJpaRepository repos, LabelJpaRepository labels, PrincipalResolver principalResolver,
                            PermissionResolver permissionResolver) {
        this.repos = repos;
        this.labels = labels;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
    }

    public record CreateLabelRequest(String name, String color) {
    }

    public record LabelView(Long id, String name, String color) {
    }

    static LabelView toView(Label label) {
        return new LabelView(label.id(), label.name(), label.color());
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
        List<LabelView> views = labels.findByRepo(repo).stream().map(LabelController::toView).toList();
        return ResponseEntity.ok(views);
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable String owner, @PathVariable String name,
                                     @RequestBody CreateLabelRequest body, HttpServletRequest request) {
        Repo repo = requireRepo(owner, name, request, Role.TRIAGE);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        if (labels.findByRepoAndName(repo, body.name()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "a label named '" + body.name() + "' already exists"));
        }
        String color = (body.color() == null || body.color().isBlank()) ? "d4d4d4" : body.color();
        Label saved = labels.save(new Label(repo, body.name(), color));
        return ResponseEntity.ok(toView(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String owner, @PathVariable String name, @PathVariable Long id,
                                     HttpServletRequest request) {
        Repo repo = requireRepo(owner, name, request, Role.TRIAGE);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        Label label = labels.findById(id).orElse(null);
        if (label == null || !label.repo().id().equals(repo.id())) {
            return ResponseEntity.notFound().build();
        }
        labels.delete(label);
        return ResponseEntity.noContent().build();
    }
}
