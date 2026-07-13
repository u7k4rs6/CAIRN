package dev.cairn.api.collab;

import dev.cairn.api.activity.ActivityPublisher;
import dev.cairn.api.domain.BranchProtectionRule;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.api.repo.BranchProtectionRuleJpaRepository;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PRD acceptance criterion, directly: "A pull request moves through its lifecycle
 * states and cannot be merged by a principal lacking write." Exercises the real
 * {@link dev.cairn.vcs.merge.MergeEngine} and a real on-disk repo, not a mock of the
 * engine, since the whole point of {@link PullRequestService} is wiring the real
 * pieces together.
 */
class PullRequestServiceTest {

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    private RepositoryRegistry.RepositoryHandle seedRepo(Path dir) {
        RepositoryRegistry registry = new RepositoryRegistry(dir.toString());
        var handle = registry.resolve("acme", "demo");
        ObjectStore store = handle.objectStore();

        ObjectId baseTree = store.put(new Tree(List.of()));
        ObjectId root = store.put(new Commit(baseTree, List.of(), PERSON, PERSON, "root"));
        handle.refStore().update("refs/heads/main", root);
        dev.cairn.vcs.dag.GenerationNumbers.computeAndStore(store, handle.generations(), root);

        ObjectId blob = store.put(new Blob("feature content\n".getBytes()));
        ObjectId featureTree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "feature.txt", blob))));
        ObjectId featureCommit = store.put(new Commit(featureTree, List.of(root), PERSON, PERSON, "feature work"));
        handle.refStore().update("refs/heads/feature", featureCommit);
        dev.cairn.vcs.dag.GenerationNumbers.computeAndStore(store, handle.generations(), featureCommit);

        return handle;
    }

    private Repo repo() {
        Repo repo = new Repo("demo", new User("acme", "acme@cairn.dev", ""), null, Visibility.PUBLIC);
        repo.assignIdForTesting(1L);
        return repo;
    }

    private RepositoryRegistry registryFor(Path dir) {
        return new RepositoryRegistry(dir.toString());
    }

    @Test
    void cannotBeMergedByAPrincipalLackingWrite(@TempDir Path dir) {
        seedRepo(dir);
        Repo repo = repo();
        PullRequest pr = new PullRequest(repo, repo.ownerUser(), "add feature", "refs/heads/feature", "refs/heads/main");

        PermissionResolver readOnly = (principal, r) -> Role.READ;
        BranchProtectionRuleJpaRepository rules = mock(BranchProtectionRuleJpaRepository.class);
        when(rules.findByRepoAndRef(repo, "refs/heads/main")).thenReturn(Optional.empty());

        PullRequestService service = new PullRequestService(readOnly, registryFor(dir), rules, new ActivityPublisher(List.of()));

        assertThatThrownBy(() -> service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()),
                MergeStrategy.MERGE_COMMIT, PERSON, PERSON, "merge"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insufficient role");
        assertThat(pr.state()).isEqualTo(PullRequestState.OPEN);
    }

    @Test
    void aCleanMergeCreatesARealCommitAndTransitionsToMerged(@TempDir Path dir) {
        var handle = seedRepo(dir);
        Repo repo = repo();
        PullRequest pr = new PullRequest(repo, repo.ownerUser(), "add feature", "refs/heads/feature", "refs/heads/main");

        PermissionResolver alwaysWrite = (principal, r) -> Role.WRITE;
        BranchProtectionRuleJpaRepository rules = mock(BranchProtectionRuleJpaRepository.class);
        when(rules.findByRepoAndRef(repo, "refs/heads/main")).thenReturn(Optional.empty());

        PullRequestService service = new PullRequestService(alwaysWrite, registryFor(dir), rules, new ActivityPublisher(List.of()));
        var result = service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()),
                MergeStrategy.MERGE_COMMIT, PERSON, PERSON, "merge feature");

        assertThat(result.isClean()).isTrue();
        assertThat(pr.state()).isEqualTo(PullRequestState.MERGED);
        ObjectId newTip = handle.refStore().resolve("refs/heads/main").orElseThrow();
        assertThat(result.commitId()).contains(newTip);
        Commit merged = (Commit) handle.objectStore().get(newTip).orElseThrow();
        assertThat(merged.isMerge()).isTrue();
    }

    @Test
    void mergingAnAlreadyMergedPullRequestIsRejected(@TempDir Path dir) {
        seedRepo(dir);
        Repo repo = repo();
        PullRequest pr = new PullRequest(repo, repo.ownerUser(), "add feature", "refs/heads/feature", "refs/heads/main");

        PermissionResolver alwaysWrite = (principal, r) -> Role.WRITE;
        BranchProtectionRuleJpaRepository rules = mock(BranchProtectionRuleJpaRepository.class);
        when(rules.findByRepoAndRef(repo, "refs/heads/main")).thenReturn(Optional.empty());
        PullRequestService service = new PullRequestService(alwaysWrite, registryFor(dir), rules, new ActivityPublisher(List.of()));

        service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()), MergeStrategy.MERGE_COMMIT, PERSON, PERSON, "merge");

        assertThatThrownBy(() -> service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()),
                MergeStrategy.MERGE_COMMIT, PERSON, PERSON, "merge again"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot merge");
    }

    @Test
    void branchProtectionRequiringApprovalBlocksAnUnapprovedMerge(@TempDir Path dir) {
        seedRepo(dir);
        Repo repo = repo();
        PullRequest pr = new PullRequest(repo, repo.ownerUser(), "add feature", "refs/heads/feature", "refs/heads/main");

        PermissionResolver alwaysWrite = (principal, r) -> Role.WRITE;
        BranchProtectionRule protection = new BranchProtectionRule(repo, "refs/heads/main", true, true, true, Role.WRITE);
        BranchProtectionRuleJpaRepository rules = mock(BranchProtectionRuleJpaRepository.class);
        when(rules.findByRepoAndRef(repo, "refs/heads/main")).thenReturn(Optional.of(protection));

        PullRequestService service = new PullRequestService(alwaysWrite, registryFor(dir), rules, new ActivityPublisher(List.of()));

        assertThatThrownBy(() -> service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()),
                MergeStrategy.MERGE_COMMIT, PERSON, PERSON, "merge"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approving review");

        pr.transitionTo(PullRequestState.APPROVED);
        var result = service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()),
                MergeStrategy.MERGE_COMMIT, PERSON, PERSON, "merge");
        assertThat(result.isClean()).isTrue();
    }

    @Test
    void squashStrategyProducesASingleParentCommit(@TempDir Path dir) {
        var handle = seedRepo(dir);
        Repo repo = repo();
        PullRequest pr = new PullRequest(repo, repo.ownerUser(), "add feature", "refs/heads/feature", "refs/heads/main");

        PermissionResolver alwaysWrite = (principal, r) -> Role.WRITE;
        BranchProtectionRuleJpaRepository rules = mock(BranchProtectionRuleJpaRepository.class);
        when(rules.findByRepoAndRef(repo, "refs/heads/main")).thenReturn(Optional.empty());
        PullRequestService service = new PullRequestService(alwaysWrite, registryFor(dir), rules, new ActivityPublisher(List.of()));

        var result = service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()),
                MergeStrategy.SQUASH, PERSON, PERSON, "squash feature");

        Commit squashed = (Commit) handle.objectStore().get(result.commitId().orElseThrow()).orElseThrow();
        assertThat(squashed.parents()).hasSize(1);
    }

    @Test
    void rebaseStrategyReplaysTheSourceCommitOntoAMovedTargetTip(@TempDir Path dir) {
        var handle = seedRepo(dir);
        ObjectStore store = handle.objectStore();

        // Advance main past the root the feature branch forked from, so a rebase
        // must actually replay onto this new tip, not just reuse the root.
        ObjectId rootCommit = handle.refStore().resolve("refs/heads/main").orElseThrow();
        ObjectId mainOnlyBlob = store.put(new Blob("main moved on\n".getBytes()));
        ObjectId mainOnlyTree = store.put(new Tree(List.of(new TreeEntry(FileMode.REGULAR_FILE, "main-only.txt", mainOnlyBlob))));
        ObjectId advancedMain = store.put(new Commit(mainOnlyTree, List.of(rootCommit), PERSON, PERSON, "advance main"));
        dev.cairn.vcs.dag.GenerationNumbers.computeAndStore(store, handle.generations(), advancedMain);
        handle.refStore().update("refs/heads/main", advancedMain);

        Repo repo = repo();
        PullRequest pr = new PullRequest(repo, repo.ownerUser(), "add feature", "refs/heads/feature", "refs/heads/main");

        PermissionResolver alwaysWrite = (principal, r) -> Role.WRITE;
        BranchProtectionRuleJpaRepository rules = mock(BranchProtectionRuleJpaRepository.class);
        when(rules.findByRepoAndRef(repo, "refs/heads/main")).thenReturn(Optional.empty());
        PullRequestService service = new PullRequestService(alwaysWrite, registryFor(dir), rules, new ActivityPublisher(List.of()));

        var result = service.merge(pr, new Principal.UserPrincipal(repo.ownerUser()),
                MergeStrategy.REBASE, PERSON, PERSON, "rebase feature");

        assertThat(result.isClean()).isTrue();
        assertThat(pr.state()).isEqualTo(PullRequestState.MERGED);
        ObjectId newTip = handle.refStore().resolve("refs/heads/main").orElseThrow();
        Commit rebased = (Commit) store.get(newTip).orElseThrow();
        assertThat(rebased.parents()).containsExactly(advancedMain);

        Tree finalTree = (Tree) store.get(rebased.treeId()).orElseThrow();
        assertThat(finalTree.entry("main-only.txt")).isPresent();
        assertThat(finalTree.entry("feature.txt")).isPresent();
    }
}
