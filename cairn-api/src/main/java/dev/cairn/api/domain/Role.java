package dev.cairn.api.domain;

/**
 * Roles, in ascending order (security doc, section 3.2): {@code NONE} (added here as
 * the identity element for "no applicable grant", not itself a grantable role) then
 * {@code read < triage < write < maintain < admin}. Declaring them in this order lets
 * {@link #compareTo} (inherited from {@link Enum}) implement the total order directly,
 * which is what makes {@code effective_role} resolution a simple maximum.
 */
public enum Role {
    NONE,
    READ,
    TRIAGE,
    WRITE,
    MAINTAIN,
    ADMIN;

    public boolean atLeast(Role required) {
        return this.compareTo(required) >= 0;
    }
}
