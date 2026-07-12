package dev.cairn.api.permission;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.Team;
import dev.cairn.api.domain.User;

import java.util.List;
import java.util.Optional;

/**
 * The narrow read model {@link DefaultPermissionResolver} needs, independent of any
 * persistence technology (architecture doc, section 6.3: narrow interfaces over one
 * fat service; Repository pattern isolates the domain from the database). Tests
 * inject an in-memory implementation; {@code JpaGrantLookup} is the real one.
 */
public interface GrantLookup {

    /** {@code Role.ADMIN} if the principal owns the repo or administers its owning org, else empty. */
    Optional<Role> ownerRole(User principal, Repo repo);

    Optional<Role> directGrant(User principal, Repo repo);

    List<Team> directTeamsOf(User principal);

    Optional<Role> teamGrant(Team team, Repo repo);

    Optional<Team> parentOf(Team team);
}
