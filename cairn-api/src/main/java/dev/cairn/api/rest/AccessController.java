package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.BranchProtectionRule;
import dev.cairn.api.domain.CollaboratorGrant;
import dev.cairn.api.domain.Organization;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.Team;
import dev.cairn.api.domain.TeamGrant;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.BranchProtectionRuleJpaRepository;
import dev.cairn.api.repo.CollaboratorGrantJpaRepository;
import dev.cairn.api.repo.OrganizationJpaRepository;
import dev.cairn.api.repo.RepoJpaRepository;
import dev.cairn.api.repo.TeamGrantJpaRepository;
import dev.cairn.api.repo.TeamJpaRepository;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The write side of FR-REPO-2/3/4 and the frontend spec's {@code settings/access}
 * screen (section 5.9): grant and revoke collaborator and team roles, change
 * visibility, and manage branch protection. Every action requires {@code admin} on
 * the repo (security doc, section 3.2), resolved through the same
 * {@link PermissionResolver} the read paths use, so there is exactly one algorithm
 * for "can this principal do X here," not a second copy for the management screen.
 */
@RestController
@RequestMapping("/api/repos/{owner}/{name}")
public class AccessController {

    private final RepoJpaRepository repos;
    private final CollaboratorGrantJpaRepository collaboratorGrants;
    private final TeamGrantJpaRepository teamGrants;
    private final TeamJpaRepository teams;
    private final OrganizationJpaRepository organizations;
    private final UserJpaRepository users;
    private final BranchProtectionRuleJpaRepository branchProtectionRules;
    private final PrincipalResolver principalResolver;
    private final PermissionResolver permissionResolver;

    public AccessController(RepoJpaRepository repos, CollaboratorGrantJpaRepository collaboratorGrants,
                             TeamGrantJpaRepository teamGrants, TeamJpaRepository teams,
                             OrganizationJpaRepository organizations, UserJpaRepository users,
                             BranchProtectionRuleJpaRepository branchProtectionRules,
                             PrincipalResolver principalResolver, PermissionResolver permissionResolver) {
        this.repos = repos;
        this.collaboratorGrants = collaboratorGrants;
        this.teamGrants = teamGrants;
        this.teams = teams;
        this.organizations = organizations;
        this.users = users;
        this.branchProtectionRules = branchProtectionRules;
        this.principalResolver = principalResolver;
        this.permissionResolver = permissionResolver;
    }

    public record CollaboratorGrantRequest(String username, Role role) {
    }

    public record TeamGrantRequest(String org, String team, Role role) {
    }

    public record VisibilityRequest(Visibility visibility) {
    }

    public record BranchProtectionRequest(boolean preventForcePush, boolean preventDeletion,
                                           boolean requireApprovalBeforeMerge, Role minimumPushRole) {
    }

    public record CollaboratorView(String username, Role role) {
    }

    public record TeamGrantView(String org, String team, Role role) {
    }

    public record AccessView(Visibility visibility, List<CollaboratorView> collaborators, List<TeamGrantView> teamGrants) {
    }

    public record BranchProtectionView(String ref, boolean preventForcePush, boolean preventDeletion,
                                        boolean requireApprovalBeforeMerge, Role minimumPushRole) {
    }


    private static Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    /** Masked like every other repo endpoint (security doc, section 6.3): a repo that doesn't exist and one the caller can't administer look identical. */
    private Repo requireAdminRepo(String owner, String name, HttpServletRequest request) {
        Repo repo = repos.findByOwnerAndName(owner, name).orElse(null);
        if (repo == null) {
            return null;
        }
        Principal principal = principalResolver.resolve(request);
        return permissionResolver.authorize(principal, repo, Role.ADMIN) ? repo : null;
    }

    @GetMapping("/access")
    public ResponseEntity<?> getAccess(@PathVariable String owner, @PathVariable String name, HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        List<CollaboratorView> collaborators = collaboratorGrants.findByRepo(repo).stream()
                .map(g -> new CollaboratorView(g.user().username(), g.role()))
                .toList();
        List<TeamGrantView> grants = teamGrants.findByRepo(repo).stream()
                .map(g -> new TeamGrantView(g.team().organization().name(), g.team().name(), g.role()))
                .toList();
        return ResponseEntity.ok(new AccessView(repo.visibility(), collaborators, grants));
    }

    @PostMapping("/access/collaborators")
    public ResponseEntity<?> upsertCollaborator(@PathVariable String owner, @PathVariable String name,
                                                 @RequestBody CollaboratorGrantRequest body, HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.role() == null || body.role() == Role.NONE) {
            return ResponseEntity.badRequest().body(error("a grantable role is required"));
        }
        User target = users.findByUsername(body.username()).orElse(null);
        if (target == null) {
            return ResponseEntity.badRequest().body(error("no such user '" + body.username() + "'"));
        }
        collaboratorGrants.findByUserAndRepo(target, repo).ifPresent(collaboratorGrants::delete);
        CollaboratorGrant saved = collaboratorGrants.save(new CollaboratorGrant(target, repo, body.role()));
        return ResponseEntity.ok(new CollaboratorView(saved.user().username(), saved.role()));
    }

    @DeleteMapping("/access/collaborators/{username}")
    public ResponseEntity<?> removeCollaborator(@PathVariable String owner, @PathVariable String name,
                                                 @PathVariable String username, HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        User target = users.findByUsername(username).orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if (repo.isOwnedBy(target)) {
            // The owner's admin comes from ownership, not a grant row, and is not
            // revocable through this endpoint (frontend spec 5.9's "last-admin
            // guard"): there is no grant row for the owner to remove in the first
            // place, so the UI never offers one.
            return ResponseEntity.status(400).body(error("cannot remove the repository owner's access"));
        }
        collaboratorGrants.findByUserAndRepo(target, repo).ifPresent(collaboratorGrants::delete);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/access/teams")
    public ResponseEntity<?> upsertTeamGrant(@PathVariable String owner, @PathVariable String name,
                                              @RequestBody TeamGrantRequest body, HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.role() == null || body.role() == Role.NONE) {
            return ResponseEntity.badRequest().body(error("a grantable role is required"));
        }
        Organization org = organizations.findByName(body.org()).orElse(null);
        Team team = org == null ? null : teams.findByOrganizationAndName(org, body.team()).orElse(null);
        if (team == null) {
            return ResponseEntity.badRequest().body(error("no such team '" + body.org() + "/" + body.team() + "'"));
        }
        teamGrants.findByTeamAndRepo(team, repo).ifPresent(teamGrants::delete);
        TeamGrant saved = teamGrants.save(new TeamGrant(team, repo, body.role()));
        return ResponseEntity.ok(new TeamGrantView(org.name(), team.name(), saved.role()));
    }

    @DeleteMapping("/access/teams/{org}/{team}")
    public ResponseEntity<?> removeTeamGrant(@PathVariable String owner, @PathVariable String name,
                                              @PathVariable String org, @PathVariable String team,
                                              HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        Organization organization = organizations.findByName(org).orElse(null);
        Team teamEntity = organization == null ? null : teams.findByOrganizationAndName(organization, team).orElse(null);
        if (teamEntity == null) {
            return ResponseEntity.notFound().build();
        }
        teamGrants.findByTeamAndRepo(teamEntity, repo).ifPresent(teamGrants::delete);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/visibility")
    public ResponseEntity<?> setVisibility(@PathVariable String owner, @PathVariable String name,
                                            @RequestBody VisibilityRequest body, HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.visibility() == null) {
            return ResponseEntity.badRequest().body(error("visibility is required"));
        }
        repo.changeVisibility(body.visibility());
        repos.save(repo);
        return ResponseEntity.ok(new VisibilityRequest(repo.visibility()));
    }

    @GetMapping("/branch-protection")
    public ResponseEntity<?> listBranchProtection(@PathVariable String owner, @PathVariable String name,
                                                   HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        List<BranchProtectionView> views = branchProtectionRules.findByRepo(repo).stream()
                .map(r -> new BranchProtectionView(r.ref(), r.preventForcePush(), r.preventDeletion(),
                        r.requireApprovalBeforeMerge(), r.minimumPushRole()))
                .toList();
        return ResponseEntity.ok(views);
    }

    @PutMapping("/branch-protection/{branch}")
    public ResponseEntity<?> setBranchProtection(@PathVariable String owner, @PathVariable String name,
                                                  @PathVariable String branch, @RequestBody BranchProtectionRequest body,
                                                  HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        String ref = "refs/heads/" + branch;
        branchProtectionRules.findByRepoAndRef(repo, ref).ifPresent(branchProtectionRules::delete);
        Role minimumPushRole = body.minimumPushRole() == null ? Role.WRITE : body.minimumPushRole();
        BranchProtectionRule saved = branchProtectionRules.save(new BranchProtectionRule(repo, ref,
                body.preventForcePush(), body.preventDeletion(), body.requireApprovalBeforeMerge(), minimumPushRole));
        return ResponseEntity.ok(new BranchProtectionView(saved.ref(), saved.preventForcePush(), saved.preventDeletion(),
                saved.requireApprovalBeforeMerge(), saved.minimumPushRole()));
    }

    @DeleteMapping("/branch-protection/{branch}")
    public ResponseEntity<?> removeBranchProtection(@PathVariable String owner, @PathVariable String name,
                                                     @PathVariable String branch, HttpServletRequest request) {
        Repo repo = requireAdminRepo(owner, name, request);
        if (repo == null) {
            return ResponseEntity.notFound().build();
        }
        branchProtectionRules.findByRepoAndRef(repo, "refs/heads/" + branch).ifPresent(branchProtectionRules::delete);
        return ResponseEntity.noContent().build();
    }
}
