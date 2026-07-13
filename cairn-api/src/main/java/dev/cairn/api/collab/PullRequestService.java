package dev.cairn.api.collab;

import dev.cairn.api.activity.ActivityEvent;
import dev.cairn.api.activity.ActivityPublisher;
import dev.cairn.api.domain.BranchProtectionRule;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Role;
import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.BranchProtectionRuleJpaRepository;
import dev.cairn.vcs.dag.GenerationNumbers;
import dev.cairn.vcs.merge.Conflict;
import dev.cairn.vcs.merge.MergeEngine;
import dev.cairn.vcs.merge.Rebase;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.ref.RefStore;
import dev.cairn.vcs.store.ObjectStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Opens and merges pull requests (architecture doc, section 8: "Open and merge a
 * pull request"). Merging is the one action that reaches across every earlier
 * milestone at once: {@link PermissionResolver} for access, {@link BranchProtectionRule}
 * for approval requirements, {@link MergeEngine} (or {@link Rebase}, for the
 * {@code REBASE} strategy) for the actual engine work, and the
 * {@link PullRequestState} machine for the legal-transition check that makes
 * merging an already-merged or closed PR structurally impossible rather than a bug
 * waiting to happen.
 */
@Component
public class PullRequestService {

    private final PermissionResolver permissionResolver;
    private final RepositoryRegistry repositories;
    private final BranchProtectionRuleJpaRepository branchProtectionRules;
    private final ActivityPublisher activityPublisher;

    public PullRequestService(PermissionResolver permissionResolver, RepositoryRegistry repositories,
                               BranchProtectionRuleJpaRepository branchProtectionRules,
                               ActivityPublisher activityPublisher) {
        this.permissionResolver = permissionResolver;
        this.repositories = repositories;
        this.branchProtectionRules = branchProtectionRules;
        this.activityPublisher = activityPublisher;
    }

    public record MergeResult(Optional<ObjectId> commitId, List<Conflict> conflicts) {
        public boolean isClean() {
            return conflicts.isEmpty();
        }
    }

    public MergeResult merge(PullRequest pr, Principal principal, MergeStrategy strategy,
                              PersonIdent author, PersonIdent committer, String message) {
        Role role = permissionResolver.effectiveRole(principal, pr.repo());
        if (!role.atLeast(Role.WRITE)) {
            throw new IllegalStateException("insufficient role to merge this pull request");
        }

        Optional<BranchProtectionRule> rule = branchProtectionRules.findByRepoAndRef(pr.repo(), pr.targetRef());
        if (rule.map(BranchProtectionRule::requireApprovalBeforeMerge).orElse(false)
                && pr.state() != PullRequestState.APPROVED) {
            throw new IllegalStateException("this branch requires an approving review before merge");
        }

        // Throws if the PR's current state does not legally allow merging (e.g. already MERGED or CLOSED),
        // before any of the engine work below runs.
        PullRequestState nextState = pr.state().merge();

        var handle = repositories.resolve(pr.repo().ownerName(), pr.repo().name());
        ObjectStore store = handle.objectStore();
        RefStore refs = handle.refStore();

        ObjectId targetCommit = refs.resolve(pr.targetRef())
                .orElseThrow(() -> new IllegalStateException("target ref not found: " + pr.targetRef()));
        ObjectId sourceCommit = refs.resolve(pr.sourceRef())
                .orElseThrow(() -> new IllegalStateException("source ref not found: " + pr.sourceRef()));

        ObjectId newCommit;
        if (strategy == MergeStrategy.REBASE) {
            Rebase rebase = new Rebase(store, handle.generations());
            Rebase.Outcome outcome = rebase.rebase(targetCommit, sourceCommit, committer);
            if (!outcome.isClean()) {
                return new MergeResult(Optional.empty(), outcome.conflicts());
            }
            newCommit = outcome.newTip();
        } else {
            MergeEngine engine = new MergeEngine(store, handle.generations());
            MergeEngine.Outcome outcome = engine.merge(targetCommit, sourceCommit);
            if (!outcome.isClean()) {
                return new MergeResult(Optional.empty(), outcome.conflicts());
            }
            List<ObjectId> parents = strategy == MergeStrategy.SQUASH
                    ? List.of(targetCommit)
                    : List.of(targetCommit, sourceCommit);
            newCommit = store.put(new Commit(outcome.mergedTreeId(), parents, author, committer, message));
        }
        GenerationNumbers.computeAndStore(store, handle.generations(), newCommit);
        refs.update(pr.targetRef(), newCommit);

        pr.setMergeStrategy(strategy);
        pr.transitionTo(nextState);

        activityPublisher.publish(new ActivityEvent(pr.repo().id(),
                pr.repo().ownerName() + "/" + pr.repo().name(),
                principal instanceof Principal.UserPrincipal up ? up.user().username() : "unknown",
                "pr_merged", "merged \"" + pr.title() + "\" into " + pr.targetRef(),
                committer.epochSeconds()));

        return new MergeResult(Optional.of(newCommit), List.of());
    }
}
