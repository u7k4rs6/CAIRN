package dev.cairn.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Repository metadata: owner, visibility, and the settings the permission model and
 * Git HTTP layer consult. Deliberately distinct from {@code dev.cairn.vcs.repository.Repository}
 * (the engine's porcelain handle on one repo's objects/refs/index): this class is a
 * platform record about a repo; that one is the repo's actual content.
 */
@Entity
@Table(name = "repos")
public class Repo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    private User ownerUser;

    @ManyToOne
    private Organization ownerOrg;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    protected Repo() {
    }

    public Repo(String name, User ownerUser, Organization ownerOrg, Visibility visibility) {
        if ((ownerUser == null) == (ownerOrg == null)) {
            throw new IllegalArgumentException("a repo has exactly one owner: a user or an organization");
        }
        this.name = name;
        this.ownerUser = ownerUser;
        this.ownerOrg = ownerOrg;
        this.visibility = visibility;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public User ownerUser() {
        return ownerUser;
    }

    public Organization ownerOrg() {
        return ownerOrg;
    }

    public Visibility visibility() {
        return visibility;
    }

    /** FR-REPO-1's visibility control (frontend spec, section 5.9), exercised by {@code AccessController}. */
    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public String ownerName() {
        return ownerUser != null ? ownerUser.username() : ownerOrg.name();
    }

    public boolean isOwnedBy(User user) {
        if (user == null || user.id() == null) {
            return false;
        }
        if (ownerUser != null) {
            return user.id().equals(ownerUser.id());
        }
        return ownerOrg != null && ownerOrg.isAdmin(user);
    }

    public void assignIdForTesting(Long id) {
        this.id = id;
    }
}
