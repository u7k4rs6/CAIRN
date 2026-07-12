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
 * Rules evaluated at ref-update time for a protected branch (security doc, section
 * 3.6): no force-push, no deletion, and a floor on who may push directly at all.
 * PR-approval-before-merge is part of this doc's model too but is enforced once
 * pull requests exist (M7); this rule shape already has a field for it so M7 only
 * has to read it, not redesign this class.
 */
@Entity
@Table(name = "branch_protection_rules")
public class BranchProtectionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Repo repo;

    /** The exact ref this rule protects, e.g. {@code refs/heads/main}. */
    private String ref;

    private boolean preventForcePush;
    private boolean preventDeletion;
    private boolean requireApprovalBeforeMerge;

    /** Nobody below this role may push directly to the ref at all. */
    @Enumerated(EnumType.STRING)
    private Role minimumPushRole;

    protected BranchProtectionRule() {
    }

    public BranchProtectionRule(Repo repo, String ref, boolean preventForcePush, boolean preventDeletion,
                                 boolean requireApprovalBeforeMerge, Role minimumPushRole) {
        this.repo = repo;
        this.ref = ref;
        this.preventForcePush = preventForcePush;
        this.preventDeletion = preventDeletion;
        this.requireApprovalBeforeMerge = requireApprovalBeforeMerge;
        this.minimumPushRole = minimumPushRole;
    }

    public Repo repo() {
        return repo;
    }

    public String ref() {
        return ref;
    }

    public boolean preventForcePush() {
        return preventForcePush;
    }

    public boolean preventDeletion() {
        return preventDeletion;
    }

    public boolean requireApprovalBeforeMerge() {
        return requireApprovalBeforeMerge;
    }

    public Role minimumPushRole() {
        return minimumPushRole;
    }
}
