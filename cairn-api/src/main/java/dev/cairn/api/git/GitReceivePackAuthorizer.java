package dev.cairn.api.git;

import dev.cairn.api.domain.BranchProtectionRule;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.transfer.ReceivePackHandler;
import dev.cairn.vcs.dag.Ancestry;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.store.ObjectStore;

import java.util.List;
import java.util.Optional;

/**
 * The real ref-update authorizer (security doc, section 4.3): requires write, then
 * enforces branch protection if the ref has a rule. Lives in {@code cairn-api}, not
 * {@code cairn-transfer}, since it depends on the permission model; {@code cairn-transfer}
 * only knows the {@link ReceivePackHandler.RefUpdateAuthorizer} shape.
 */
public final class GitReceivePackAuthorizer implements ReceivePackHandler.RefUpdateAuthorizer {

    private final PermissionResolver permissionResolver;
    private final Principal principal;
    private final Repo repo;
    private final List<BranchProtectionRule> rules;
    private final ObjectStore store;
    private final GenerationStore generations;

    public GitReceivePackAuthorizer(PermissionResolver permissionResolver, Principal principal, Repo repo,
                                     List<BranchProtectionRule> rules, ObjectStore store, GenerationStore generations) {
        this.permissionResolver = permissionResolver;
        this.principal = principal;
        this.repo = repo;
        this.rules = rules;
        this.store = store;
        this.generations = generations;
    }

    @Override
    public Decision authorize(ReceivePackHandler.RefUpdateCommand command) {
        Role role = permissionResolver.effectiveRole(principal, repo);
        if (!role.atLeast(Role.WRITE)) {
            return Decision.deny("insufficient role");
        }

        Optional<BranchProtectionRule> rule = rules.stream().filter(r -> r.ref().equals(command.ref())).findFirst();
        if (rule.isEmpty()) {
            return Decision.allow();
        }
        BranchProtectionRule protection = rule.get();

        if (!role.atLeast(protection.minimumPushRole())) {
            return Decision.deny("push to this branch is restricted to a higher role");
        }
        if (command.isDelete()) {
            return protection.preventDeletion() ? Decision.deny("branch is protected from deletion") : Decision.allow();
        }
        if (protection.preventForcePush() && command.oldId().isPresent()) {
            Ancestry ancestry = new Ancestry(store, generations);
            if (!ancestry.isAncestor(command.oldId().get(), command.newId().orElseThrow())) {
                return Decision.deny("force-push is not allowed on a protected branch");
            }
        }
        return Decision.allow();
    }
}
