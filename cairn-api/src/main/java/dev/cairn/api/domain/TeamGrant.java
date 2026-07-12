package dev.cairn.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A grant of a role to a team on a repo, inherited down the team's nested descendants (security doc, section 3.3.3). */
@Entity
@Table(name = "team_grants")
public class TeamGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Team team;

    @ManyToOne
    private Repo repo;

    @Enumerated(EnumType.STRING)
    private Role role;

    protected TeamGrant() {
    }

    public TeamGrant(Team team, Repo repo, Role role) {
        this.team = team;
        this.repo = repo;
        this.role = role;
    }

    public Team team() {
        return team;
    }

    public Repo repo() {
        return repo;
    }

    public Role role() {
        return role;
    }
}
