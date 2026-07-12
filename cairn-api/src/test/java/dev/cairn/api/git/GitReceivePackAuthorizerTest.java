package dev.cairn.api.git;

import dev.cairn.api.domain.BranchProtectionRule;
import dev.cairn.api.domain.Principal;
import dev.cairn.api.domain.Repo;
import dev.cairn.api.domain.Role;
import dev.cairn.api.domain.User;
import dev.cairn.api.domain.Visibility;
import dev.cairn.api.permission.PermissionResolver;
import dev.cairn.transfer.ReceivePackHandler;
import dev.cairn.vcs.dag.FileGenerationStore;
import dev.cairn.vcs.dag.GenerationNumbers;
import dev.cairn.vcs.dag.GenerationStore;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security doc, section 3.6/4.3: branch protection is evaluated at ref-update time,
 * beneath any UI. This exercises {@link GitReceivePackAuthorizer} directly against
 * real commit ancestry (fast-forward vs. force-push), independent of the HTTP layer.
 */
class GitReceivePackAuthorizerTest {

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    private static final PermissionResolver ALWAYS_WRITE = (principal, repo) -> Role.WRITE;

    private ObjectId commit(ObjectStore store, GenerationStore generations, ObjectId parent, String message) {
        ObjectId tree = store.put(new Tree(List.of()));
        List<ObjectId> parents = parent == null ? List.of() : List.of(parent);
        ObjectId id = store.put(new Commit(tree, parents, PERSON, PERSON, message));
        GenerationNumbers.computeAndStore(store, generations, id);
        return id;
    }

    private Repo repo() {
        Repo repo = new Repo("demo", new User("owner", "owner@cairn.dev", ""), null, Visibility.PRIVATE);
        repo.assignIdForTesting(1L);
        return repo;
    }

    @Test
    void fastForwardIsAllowedOnAProtectedBranch(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId c1 = commit(store, generations, null, "c1");
        ObjectId c2 = commit(store, generations, c1, "c2");

        BranchProtectionRule rule = new BranchProtectionRule(repo(), "refs/heads/main", true, true, false, Role.WRITE);
        var authorizer = new GitReceivePackAuthorizer(ALWAYS_WRITE, dummyPrincipal(), repo(), List.of(rule), store, generations);

        var decision = authorizer.authorize(new ReceivePackHandler.RefUpdateCommand(
                "refs/heads/main", Optional.of(c1), Optional.of(c2)));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void forcePushIsDeniedOnAProtectedBranch(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId c1 = commit(store, generations, null, "c1");
        ObjectId c2 = commit(store, generations, c1, "c2");
        // A rewritten history: c3 is a sibling of c2, not its descendant.
        ObjectId c3 = commit(store, generations, c1, "c3 (rewritten)");

        BranchProtectionRule rule = new BranchProtectionRule(repo(), "refs/heads/main", true, true, false, Role.WRITE);
        var authorizer = new GitReceivePackAuthorizer(ALWAYS_WRITE, dummyPrincipal(), repo(), List.of(rule), store, generations);

        var decision = authorizer.authorize(new ReceivePackHandler.RefUpdateCommand(
                "refs/heads/main", Optional.of(c2), Optional.of(c3)));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("force-push");
    }

    @Test
    void deletionIsDeniedOnAProtectedBranch(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId c1 = commit(store, generations, null, "c1");

        BranchProtectionRule rule = new BranchProtectionRule(repo(), "refs/heads/main", true, true, false, Role.WRITE);
        var authorizer = new GitReceivePackAuthorizer(ALWAYS_WRITE, dummyPrincipal(), repo(), List.of(rule), store, generations);

        var decision = authorizer.authorize(new ReceivePackHandler.RefUpdateCommand(
                "refs/heads/main", Optional.of(c1), Optional.empty()));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("deletion");
    }

    @Test
    void unprotectedBranchAllowsAnythingWithWriteAccess(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId c1 = commit(store, generations, null, "c1");
        ObjectId c2 = commit(store, generations, null, "unrelated");

        var authorizer = new GitReceivePackAuthorizer(ALWAYS_WRITE, dummyPrincipal(), repo(), List.of(), store, generations);

        var decision = authorizer.authorize(new ReceivePackHandler.RefUpdateCommand(
                "refs/heads/feature", Optional.of(c1), Optional.of(c2)));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void insufficientRoleIsDeniedRegardlessOfBranchProtection() {
        PermissionResolver alwaysRead = (principal, repo) -> Role.READ;
        var authorizer = new GitReceivePackAuthorizer(alwaysRead, dummyPrincipal(), repo(), List.of(), null, null);

        var decision = authorizer.authorize(new ReceivePackHandler.RefUpdateCommand(
                "refs/heads/main", Optional.empty(), Optional.of(ObjectId.hash("x".getBytes()))));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("insufficient role");
    }

    private Principal dummyPrincipal() {
        return new Principal.UserPrincipal(new User("someone", "someone@cairn.dev", ""));
    }
}
