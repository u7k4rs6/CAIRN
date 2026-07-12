package dev.cairn.api.permission;

import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;

/**
 * Strategy pattern (architecture doc, section 6.2): resolves a principal's effective
 * role on a repo. Direct grants, team grants, ownership, and visibility all feed one
 * resolution, so every caller asks one question instead of re-deriving the algorithm.
 */
public interface PermissionResolver {

    Role effectiveRole(Principal principal, Repo repo);

    default boolean authorize(Principal principal, Repo repo, Role required) {
        return effectiveRole(principal, repo).atLeast(required);
    }
}
