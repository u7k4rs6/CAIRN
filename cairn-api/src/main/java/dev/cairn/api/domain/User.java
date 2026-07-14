package dev.cairn.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String email;

    /**
     * BCrypt hash (security doc, section 2.2); never the plaintext password.
     * {@code @JsonIgnore} because {@code User} is serialized indirectly all over the
     * API (as a repo's owner, an issue's author, ...) with field-visibility Jackson
     * config (see application.yml), which would otherwise expose this in every one
     * of those responses.
     */
    @JsonIgnore
    private String passwordHash;

    protected User() {
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    /** Test/bootstrap convenience: assigns an id without a database round trip. */
    public void assignIdForTesting(Long id) {
        this.id = id;
    }

    /** Self-healing seed repair (DevDataSeeder): replaces a stored hash that's missing or unusable, never a live account's real password. */
    public void repairPasswordHash(String newHash) {
        this.passwordHash = newHash;
    }
}
