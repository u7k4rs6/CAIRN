package dev.cairn.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A team within an org: can nest under a parent team, holds repository grants, and
 * has members (architecture doc, section 5). This is the Composite structure named
 * in section 6: a team and its parent chain are treated uniformly by permission
 * resolution, whether the team is a leaf or itself has children, since resolution
 * only ever needs to ask "what is this team's own grant, and what is its parent."
 */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    private Organization organization;

    @ManyToOne
    private Team parent;

    protected Team() {
    }

    public Team(String name, Organization organization, Team parent) {
        this.name = name;
        this.organization = organization;
        this.parent = parent;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Organization organization() {
        return organization;
    }

    public Team parent() {
        return parent;
    }

    public void assignIdForTesting(Long id) {
        this.id = id;
    }
}
