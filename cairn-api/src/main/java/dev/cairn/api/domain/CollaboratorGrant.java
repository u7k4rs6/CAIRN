package dev.cairn.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A direct grant of a role to one user on one repo (security doc, section 3.3.2). */
@Entity
@Table(name = "collaborator_grants")
public class CollaboratorGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Repo repo;

    @Enumerated(EnumType.STRING)
    private Role role;

    protected CollaboratorGrant() {
    }

    public CollaboratorGrant(User user, Repo repo, Role role) {
        this.user = user;
        this.repo = repo;
        this.role = role;
    }

    public User user() {
        return user;
    }

    public Repo repo() {
        return repo;
    }

    public Role role() {
        return role;
    }
}
