package dev.cairn.api.rest;

import dev.cairn.api.auth.PrincipalResolver;
import dev.cairn.api.domain.Organization;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Team;
import dev.cairn.api.domain.TeamMembership;
import dev.cairn.api.domain.User;
import dev.cairn.api.repo.OrganizationJpaRepository;
import dev.cairn.api.repo.TeamJpaRepository;
import dev.cairn.api.repo.TeamMembershipJpaRepository;
import dev.cairn.api.repo.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * FR-REPO-2 and the "org admin manages teams" user story (PRD section 7), previously
 * unreachable: {@link Organization}, {@link Team}, and {@link TeamMembership} existed
 * as a tested domain model with no REST surface at all, so a real user had no way to
 * create an org, create a team, or add a member to one. This controller is that
 * surface. Team-to-repo grants (the write side of {@code effective_role}'s team path)
 * live in {@link AccessController}, alongside collaborator grants, since both are
 * repo-scoped rather than org-scoped.
 */
@RestController
public class OrgController {

    private final OrganizationJpaRepository organizations;
    private final TeamJpaRepository teams;
    private final TeamMembershipJpaRepository memberships;
    private final UserJpaRepository users;
    private final PrincipalResolver principalResolver;

    public OrgController(OrganizationJpaRepository organizations, TeamJpaRepository teams,
                          TeamMembershipJpaRepository memberships, UserJpaRepository users,
                          PrincipalResolver principalResolver) {
        this.organizations = organizations;
        this.teams = teams;
        this.memberships = memberships;
        this.users = users;
        this.principalResolver = principalResolver;
    }

    public record CreateOrgRequest(String name) {
    }

    public record OrgView(Long id, String name, String owner) {
    }

    public record CreateTeamRequest(String name, String parentTeam) {
    }

    public record TeamView(Long id, String name, String parentTeam) {
    }

    public record AddMemberRequest(String username) {
    }

    public record TeamMemberView(String username) {
    }

    private static OrgView toView(Organization org) {
        return new OrgView(org.id(), org.name(), org.owner().username());
    }

    private static TeamView toView(Team team) {
        return new TeamView(team.id(), team.name(), team.parent() != null ? team.parent().name() : null);
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", message);
    }

    @PostMapping("/api/orgs")
    public ResponseEntity<?> createOrg(@RequestBody CreateOrgRequest body, HttpServletRequest request) {
        if (!(principalResolver.resolve(request) instanceof Principal.UserPrincipal up)) {
            return ResponseEntity.status(401).build();
        }
        if (body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(error("name is required"));
        }
        if (organizations.findByName(body.name()).isPresent()) {
            return ResponseEntity.status(409).body(error("an organization named '" + body.name() + "' already exists"));
        }
        Organization org = organizations.save(new Organization(body.name(), up.user()));
        return ResponseEntity.ok(toView(org));
    }

    @GetMapping("/api/orgs/{org}")
    public ResponseEntity<?> getOrg(@PathVariable String org) {
        return organizations.findByName(org)
                .map(o -> ResponseEntity.ok(toView(o)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Null return means "not authorized to manage this org"; the response to send is already set on it. */
    private record OrgAuthResult(Organization organization, ResponseEntity<?> denial) {
    }

    private OrgAuthResult requireOrgAdmin(String orgName, HttpServletRequest request) {
        Organization organization = organizations.findByName(orgName).orElse(null);
        if (organization == null) {
            return new OrgAuthResult(null, ResponseEntity.notFound().build());
        }
        Principal principal = principalResolver.resolve(request);
        if (!(principal instanceof Principal.UserPrincipal up)) {
            return new OrgAuthResult(null, ResponseEntity.status(401).build());
        }
        if (!organization.isAdmin(up.user())) {
            return new OrgAuthResult(null, ResponseEntity.status(403).body(error("admin required")));
        }
        return new OrgAuthResult(organization, null);
    }

    @PostMapping("/api/orgs/{org}/teams")
    public ResponseEntity<?> createTeam(@PathVariable String org, @RequestBody CreateTeamRequest body,
                                         HttpServletRequest request) {
        var auth = requireOrgAdmin(org, request);
        if (auth.denial() != null) {
            return auth.denial();
        }
        if (body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(error("name is required"));
        }
        if (teams.findByOrganizationAndName(auth.organization(), body.name()).isPresent()) {
            return ResponseEntity.status(409).body(error("a team named '" + body.name() + "' already exists in this org"));
        }
        Team parent = null;
        if (body.parentTeam() != null && !body.parentTeam().isBlank()) {
            parent = teams.findByOrganizationAndName(auth.organization(), body.parentTeam()).orElse(null);
            if (parent == null) {
                return ResponseEntity.badRequest().body(error("parent team '" + body.parentTeam() + "' does not exist"));
            }
        }
        Team team = teams.save(new Team(body.name(), auth.organization(), parent));
        return ResponseEntity.ok(toView(team));
    }

    @GetMapping("/api/orgs/{org}/teams")
    public ResponseEntity<?> listTeams(@PathVariable String org, HttpServletRequest request) {
        var auth = requireOrgAdmin(org, request);
        if (auth.denial() != null) {
            return auth.denial();
        }
        List<TeamView> views = teams.findByOrganization(auth.organization()).stream()
                .map(OrgController::toView)
                .toList();
        return ResponseEntity.ok(views);
    }

    private Team requireTeam(Organization org, String teamName) {
        return teams.findByOrganizationAndName(org, teamName).orElse(null);
    }

    @PostMapping("/api/orgs/{org}/teams/{team}/members")
    public ResponseEntity<?> addMember(@PathVariable String org, @PathVariable String team,
                                        @RequestBody AddMemberRequest body, HttpServletRequest request) {
        var auth = requireOrgAdmin(org, request);
        if (auth.denial() != null) {
            return auth.denial();
        }
        Team teamEntity = requireTeam(auth.organization(), team);
        if (teamEntity == null) {
            return ResponseEntity.notFound().build();
        }
        User member = users.findByUsername(body.username()).orElse(null);
        if (member == null) {
            return ResponseEntity.badRequest().body(error("no such user '" + body.username() + "'"));
        }
        if (memberships.findByTeamAndUser(teamEntity, member).isEmpty()) {
            memberships.save(new TeamMembership(member, teamEntity));
        }
        return ResponseEntity.ok(new TeamMemberView(member.username()));
    }

    @GetMapping("/api/orgs/{org}/teams/{team}/members")
    public ResponseEntity<?> listMembers(@PathVariable String org, @PathVariable String team, HttpServletRequest request) {
        var auth = requireOrgAdmin(org, request);
        if (auth.denial() != null) {
            return auth.denial();
        }
        Team teamEntity = requireTeam(auth.organization(), team);
        if (teamEntity == null) {
            return ResponseEntity.notFound().build();
        }
        List<TeamMemberView> views = memberships.findByTeam(teamEntity).stream()
                .map(m -> new TeamMemberView(m.user().username()))
                .toList();
        return ResponseEntity.ok(views);
    }

    @DeleteMapping("/api/orgs/{org}/teams/{team}/members/{username}")
    public ResponseEntity<?> removeMember(@PathVariable String org, @PathVariable String team,
                                           @PathVariable String username, HttpServletRequest request) {
        var auth = requireOrgAdmin(org, request);
        if (auth.denial() != null) {
            return auth.denial();
        }
        Team teamEntity = requireTeam(auth.organization(), team);
        User member = teamEntity == null ? null : users.findByUsername(username).orElse(null);
        if (teamEntity == null || member == null) {
            return ResponseEntity.notFound().build();
        }
        memberships.findByTeamAndUser(teamEntity, member).ifPresent(memberships::delete);
        return ResponseEntity.noContent().build();
    }
}
