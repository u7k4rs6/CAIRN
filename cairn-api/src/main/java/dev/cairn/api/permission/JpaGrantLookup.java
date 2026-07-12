package dev.cairn.api.permission;

import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.Team;
import dev.cairn.api.domain.User;
import dev.cairn.api.repo.CollaboratorGrantJpaRepository;
import dev.cairn.api.repo.TeamGrantJpaRepository;
import dev.cairn.api.repo.TeamMembershipJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** The real {@link GrantLookup}, backed by Spring Data JPA repositories (the persistence adapter behind the domain, per the architecture doc's Repository-pattern row). */
@Component
public class JpaGrantLookup implements GrantLookup {

    private final CollaboratorGrantJpaRepository collaboratorGrants;
    private final TeamGrantJpaRepository teamGrants;
    private final TeamMembershipJpaRepository teamMemberships;

    public JpaGrantLookup(CollaboratorGrantJpaRepository collaboratorGrants, TeamGrantJpaRepository teamGrants,
                           TeamMembershipJpaRepository teamMemberships) {
        this.collaboratorGrants = collaboratorGrants;
        this.teamGrants = teamGrants;
        this.teamMemberships = teamMemberships;
    }

    @Override
    public Optional<Role> ownerRole(User principal, Repo repo) {
        return repo.isOwnedBy(principal) ? Optional.of(Role.ADMIN) : Optional.empty();
    }

    @Override
    public Optional<Role> directGrant(User principal, Repo repo) {
        return collaboratorGrants.findByUserAndRepo(principal, repo).map(g -> g.role());
    }

    @Override
    public List<Team> directTeamsOf(User principal) {
        return teamMemberships.findByUser(principal).stream().map(m -> m.team()).toList();
    }

    @Override
    public Optional<Role> teamGrant(Team team, Repo repo) {
        return teamGrants.findByTeamAndRepo(team, repo).map(g -> g.role());
    }

    @Override
    public Optional<Team> parentOf(Team team) {
        return Optional.ofNullable(team.parent());
    }
}
