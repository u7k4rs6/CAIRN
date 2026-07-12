package dev.cairn.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Owns repositories and contains teams (architecture doc, section 5). */
@Entity
@Table(name = "organizations", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    private User owner;

    protected Organization() {
    }

    public Organization(String name, User owner) {
        this.name = name;
        this.owner = owner;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public User owner() {
        return owner;
    }

    public boolean isAdmin(User user) {
        return owner != null && user != null && owner.id() != null && owner.id().equals(user.id());
    }

    public void assignIdForTesting(Long id) {
        this.id = id;
    }
}
