package dev.cairn.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A scoped credential (security doc, section 2.3): only its hash is ever stored, the
 * plaintext is shown once at creation and never again. Scopes are named as strings
 * ({@code repo:read}, {@code repo:write}) rather than an enum so new scopes don't
 * require a schema migration; the security doc's scope examples are the only ones
 * this milestone actually checks.
 */
@Entity
@Table(name = "personal_access_tokens")
public class PersonalAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @JsonIgnore
    private String tokenHash;
    private String scopes;
    private Instant expiresAt;
    private Instant revokedAt;

    protected PersonalAccessToken() {
    }

    public PersonalAccessToken(User user, String tokenHash, String scopes, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.scopes = scopes;
        this.expiresAt = expiresAt;
    }

    public User user() {
        return user;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public boolean hasScope(String scope) {
        return java.util.Arrays.asList(scopes.split(",")).contains(scope);
    }

    public boolean isValid(Instant now) {
        return revokedAt == null && (expiresAt == null || now.isBefore(expiresAt));
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}
