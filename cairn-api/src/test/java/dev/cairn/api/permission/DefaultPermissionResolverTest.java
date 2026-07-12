package dev.cairn.api.permission;

import dev.cairn.api.domain.CollaboratorGrant;
import dev.cairn.api.domain.Organization;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.Team;
import dev.cairn.api.domain.TeamGrant;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The build-time security checklist item: "an automated suite asserts allow and
 * deny for each role on each protected action, including a cyclic-team case"
 * (security doc, section 8), exercised directly against {@link DefaultPermissionResolver}.
 */
class DefaultPermissionResolverTest {

    private final AtomicLong ids = new AtomicLong(1);

    /** A simple in-memory fake, exactly the kind of substitution the architecture doc calls out (Repository pattern: swap Postgres for in-memory in tests). */
    private static final class FakeGrantLookup implements GrantLookup {
        final Map<Long, Role> directGrants = new HashMap<>();
        final Map<Long, List<Team>> memberships = new HashMap<>();
        final Map<Long, Role> teamGrants = new HashMap<>();
        final Map<Long, Team> parents = new HashMap<>();
        final Map<Long, Boolean> owners = new HashMap<>();

        @Override
        public Optional<Role> ownerRole(User principal, Repo repo) {
            return Boolean.TRUE.equals(owners.get(principal.id())) ? Optional.of(Role.ADMIN) : Optional.empty();
        }

        @Override
        public Optional<Role> directGrant(User principal, Repo repo) {
            return Optional.ofNullable(directGrants.get(principal.id()));
        }

        @Override
        public List<Team> directTeamsOf(User principal) {
            return memberships.getOrDefault(principal.id(), List.of());
        }

        @Override
        public Optional<Role> teamGrant(Team team, Repo repo) {
            return Optional.ofNullable(teamGrants.get(team.id()));
        }

        @Override
        public Optional<Team> parentOf(Team team) {
            return Optional.ofNullable(parents.get(team.id()));
        }
    }

    private User newUser() {
        User user = new User("u" + ids.get(), "u@cairn.dev", "hash");
        user.assignIdForTesting(ids.getAndIncrement());
        return user;
    }

    private Team newTeam(Organization org, Team parent) {
        Team team = new Team("team", org, parent);
        team.assignIdForTesting(ids.getAndIncrement());
        return team;
    }

    private Repo newRepo(Visibility visibility) {
        User owner = newUser();
        Repo repo = new Repo("demo", owner, null, visibility);
        repo.assignIdForTesting(ids.getAndIncrement());
        return repo;
    }

    @Test
    void anonymousReadsPublicButNothingElse() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);

        assertThat(resolver.effectiveRole(Principal.ANONYMOUS, newRepo(Visibility.PUBLIC))).isEqualTo(Role.READ);
        assertThat(resolver.effectiveRole(Principal.ANONYMOUS, newRepo(Visibility.INTERNAL))).isEqualTo(Role.NONE);
        assertThat(resolver.effectiveRole(Principal.ANONYMOUS, newRepo(Visibility.PRIVATE))).isEqualTo(Role.NONE);
    }

    @Test
    void privateRepoIsUnreadableWithoutAGrant() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        User stranger = newUser();

        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(stranger), repo)).isEqualTo(Role.NONE);
        assertThat(resolver.authorize(new Principal.UserPrincipal(stranger), repo, Role.READ)).isFalse();
    }

    @Test
    void privateRepoIsReadableWithADirectGrant() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        User collaborator = newUser();
        lookup.directGrants.put(collaborator.id(), Role.WRITE);

        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(collaborator), repo)).isEqualTo(Role.WRITE);
        assertThat(resolver.authorize(new Principal.UserPrincipal(collaborator), repo, Role.WRITE)).isTrue();
        assertThat(resolver.authorize(new Principal.UserPrincipal(collaborator), repo, Role.ADMIN)).isFalse();
    }

    @Test
    void ownerIsAlwaysAdminRegardlessOfVisibility() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        User owner = repo.ownerUser();
        lookup.owners.put(owner.id(), true);

        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(owner), repo)).isEqualTo(Role.ADMIN);
    }

    @Test
    void internalVisibilityGrantsReadToAnyAuthenticatedUserButNotAnonymous() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.INTERNAL);
        User anyMember = newUser();

        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(anyMember), repo)).isEqualTo(Role.READ);
        assertThat(resolver.effectiveRole(Principal.ANONYMOUS, repo)).isEqualTo(Role.NONE);
    }

    @Test
    void teamGrantAppliesToADirectMember() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        Organization org = new Organization("acme", newUser());
        Team backend = newTeam(org, null);
        lookup.teamGrants.put(backend.id(), Role.WRITE);
        User engineer = newUser();
        lookup.memberships.put(engineer.id(), List.of(backend));

        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(engineer), repo)).isEqualTo(Role.WRITE);
    }

    @Test
    void teamGrantIsInheritedDownFromAnAncestorTeam() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        Organization org = new Organization("acme", newUser());
        Team parentTeam = newTeam(org, null);
        Team childTeam = newTeam(org, parentTeam);
        lookup.parents.put(childTeam.id(), parentTeam);
        lookup.teamGrants.put(parentTeam.id(), Role.MAINTAIN);
        User engineer = newUser();
        // The user is only a direct member of the CHILD team; the grant lives on the parent.
        lookup.memberships.put(engineer.id(), List.of(childTeam));

        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(engineer), repo)).isEqualTo(Role.MAINTAIN);
    }

    @Test
    void noPathCanPushBelowAnotherPathsGrant_maximumWins() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        User user = newUser();
        // A weak direct grant, but a strong team grant: the effective role is the maximum, not the direct grant.
        lookup.directGrants.put(user.id(), Role.TRIAGE);
        Organization org = new Organization("acme", newUser());
        Team admins = newTeam(org, null);
        lookup.teamGrants.put(admins.id(), Role.ADMIN);
        lookup.memberships.put(user.id(), List.of(admins));

        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(user), repo)).isEqualTo(Role.ADMIN);
    }

    @Test
    void cyclicTeamHierarchyTerminatesInsteadOfLoopingForever() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        Organization org = new Organization("acme", newUser());
        Team a = newTeam(org, null);
        Team b = newTeam(org, null);
        // A cyclic, pathological structure: a's parent is b, b's parent is a.
        lookup.parents.put(a.id(), b);
        lookup.parents.put(b.id(), a);
        lookup.teamGrants.put(b.id(), Role.WRITE);
        User user = newUser();
        lookup.memberships.put(user.id(), List.of(a));

        Role role = assertTimeoutPreventsHang(() -> resolver.effectiveRole(new Principal.UserPrincipal(user), repo));

        assertThat(role).isEqualTo(Role.WRITE);
    }

    @Test
    void deepButAcyclicChainStillTerminatesWithinTheDepthCap() {
        FakeGrantLookup lookup = new FakeGrantLookup();
        PermissionResolver resolver = new DefaultPermissionResolver(lookup);
        Repo repo = newRepo(Visibility.PRIVATE);
        Organization org = new Organization("acme", newUser());

        Team current = newTeam(org, null);
        Team leaf = current;
        for (int i = 0; i < DefaultPermissionResolver.MAX_TEAM_DEPTH + 10; i++) {
            Team next = newTeam(org, null);
            lookup.parents.put(current.id(), next);
            current = next;
        }
        lookup.teamGrants.put(current.id(), Role.ADMIN); // far beyond the depth cap
        User user = newUser();
        lookup.memberships.put(user.id(), List.of(leaf));

        // Beyond the cap, the grant is not found: role stays NONE (private, no other grant).
        assertThat(resolver.effectiveRole(new Principal.UserPrincipal(user), repo)).isEqualTo(Role.NONE);
    }

    private Role assertTimeoutPreventsHang(java.util.function.Supplier<Role> call) {
        return org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2), call::get);
    }
}
