package dev.cairn.api.permission;

import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.Team;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The resolution algorithm exactly as specified (security doc, section 3.4):
 *
 * <pre>
 * effective_role(principal, repo):
 *     if principal is anonymous:
 *         return read if repo.visibility == public else none
 *     roles = empty set
 *     if principal owns repo or is admin of repo.owner_org: roles.add(admin)
 *     if direct grant g exists for (principal, repo): roles.add(g.role)
 *     for team in teams_of(principal):            # walks nested teams
 *         if team has grant t on repo: roles.add(t.role)
 *     if repo.visibility in {public, internal} and principal is eligible: roles.add(read)
 *     return max(roles) or none
 * </pre>
 *
 * <p><b>Complexity and bound.</b> O(D + T): D direct grants (O(1) here) plus T edges
 * walked in the nested team hierarchy. The walk is bounded by both a depth cap
 * ({@link #MAX_TEAM_DEPTH}) and a visited set, so a pathological or cyclic team
 * structure cannot turn a permission check into an unbounded traversal (security
 * doc, sections 3.4 and 6.3).
 *
 * <p><b>Caching tradeoff.</b> This resolves fresh on every call rather than caching
 * per (principal, repo). The security doc names this explicitly as the v1 default:
 * recompute is simple and always correct; caching would need a clear invalidation
 * story (any grant, membership, or ownership change must invalidate the affected
 * entries) that this milestone does not build.
 */
public final class DefaultPermissionResolver implements PermissionResolver {

    static final int MAX_TEAM_DEPTH = 50;

    private final GrantLookup lookup;

    public DefaultPermissionResolver(GrantLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public Role effectiveRole(Principal principal, Repo repo) {
        if (principal instanceof Principal.AnonymousPrincipal) {
            return repo.visibility() == Visibility.PUBLIC ? Role.READ : Role.NONE;
        }
        User user = ((Principal.UserPrincipal) principal).user();

        List<Role> roles = new ArrayList<>();
        lookup.ownerRole(user, repo).ifPresent(roles::add);
        lookup.directGrant(user, repo).ifPresent(roles::add);
        for (Team team : lookup.directTeamsOf(user)) {
            collectTeamGrants(team, repo, roles, new HashSet<>(), 0);
        }
        if (repo.visibility() == Visibility.PUBLIC || repo.visibility() == Visibility.INTERNAL) {
            roles.add(Role.READ);
        }
        return roles.stream().max(Role::compareTo).orElse(Role.NONE);
    }

    /** Walks {@code team}'s own grant, then its parent, then its parent's parent, bounded by depth and a visited set. */
    private void collectTeamGrants(Team team, Repo repo, List<Role> roles, Set<Long> visited, int depth) {
        if (depth > MAX_TEAM_DEPTH || team.id() == null || !visited.add(team.id())) {
            return;
        }
        lookup.teamGrant(team, repo).ifPresent(roles::add);
        lookup.parentOf(team).ifPresent(parent -> collectTeamGrants(parent, repo, roles, visited, depth + 1));
    }
}
