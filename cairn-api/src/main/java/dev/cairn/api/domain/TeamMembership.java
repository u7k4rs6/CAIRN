package dev.cairn.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A user's direct membership in a team; permission resolution then walks that team's parent chain. */
@Entity
@Table(name = "team_memberships")
public class TeamMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Team team;

    protected TeamMembership() {
    }

    public TeamMembership(User user, Team team) {
        this.user = user;
        this.team = team;
    }

    public User user() {
        return user;
    }

    public Team team() {
        return team;
    }
}
