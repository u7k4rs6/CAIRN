package dev.cairn.vcs.merge;

import dev.cairn.vcs.dag.FileGenerationStore;
import dev.cairn.vcs.dag.GenerationNumbers;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.repository.Repository;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The REBASE merge strategy: replays a branch's unique commits onto the target's current tip. */
class RebaseTest {

    private static final PersonIdent AUTHOR = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");
    private static final PersonIdent REBASE_COMMITTER = new PersonIdent("Bot", "bot@cairn.dev", 1_700_001_000L, "+0000");

    private Rebase rebaseFor(Repository repo) {
        ObjectStore store = repo.objectStore();
        return new Rebase(store, repo.generations());
    }

    @Test
    void aSourceAlreadyContainedInTargetIsAlreadyUpToDate(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one");
        repo.add("a.txt");
        ObjectId base = repo.commit("base", AUTHOR, AUTHOR);

        Rebase.Outcome outcome = rebaseFor(repo).rebase(base, base, REBASE_COMMITTER);
        assertThat(outcome.alreadyUpToDate()).isTrue();
        assertThat(outcome.newTip()).isEqualTo(base);
    }

    @Test
    void replaysASingleCommitOntoANewTargetTip(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("shared.txt"), "base\n");
        repo.add("shared.txt");
        ObjectId base = repo.commit("base", AUTHOR, AUTHOR);

        repo.createBranch("feature", base);
        repo.checkout("feature");
        Files.writeString(dir.resolve("feature.txt"), "feature work\n");
        repo.add("feature.txt");
        ObjectId featureCommit = repo.commit("add feature file", AUTHOR, AUTHOR);

        repo.checkout("main");
        Files.writeString(dir.resolve("main-only.txt"), "main moved on\n");
        repo.add("main-only.txt");
        ObjectId targetTip = repo.commit("advance main", AUTHOR, AUTHOR);

        Rebase.Outcome outcome = rebaseFor(repo).rebase(targetTip, featureCommit, REBASE_COMMITTER);
        assertThat(outcome.isClean()).isTrue();
        assertThat(outcome.alreadyUpToDate()).isFalse();

        Commit rebased = (Commit) repo.objectStore().get(outcome.newTip()).orElseThrow();
        assertThat(rebased.parents()).containsExactly(targetTip);
        assertThat(rebased.author().name()).isEqualTo("Ada");
        assertThat(rebased.committer().name()).isEqualTo("Bot");
        assertThat(rebased.message()).isEqualTo("add feature file");

        // The rebased commit's tree must contain both main's new file and the
        // feature's own file: proof the replay is a real three-way merge onto the
        // moved target, not just a reparented copy of the original commit.
        var tree = (dev.cairn.vcs.object.Tree) repo.objectStore().get(rebased.treeId()).orElseThrow();
        assertThat(tree.entry("main-only.txt")).isPresent();
        assertThat(tree.entry("feature.txt")).isPresent();
        assertThat(tree.entry("shared.txt")).isPresent();
    }

    @Test
    void replaysMultipleCommitsInOrder(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("shared.txt"), "base\n");
        repo.add("shared.txt");
        ObjectId base = repo.commit("base", AUTHOR, AUTHOR);

        repo.createBranch("feature", base);
        repo.checkout("feature");
        Files.writeString(dir.resolve("f1.txt"), "one\n");
        repo.add("f1.txt");
        repo.commit("first feature commit", AUTHOR, AUTHOR);
        Files.writeString(dir.resolve("f2.txt"), "two\n");
        repo.add("f2.txt");
        ObjectId featureTip = repo.commit("second feature commit", AUTHOR, AUTHOR);

        repo.checkout("main");
        Files.writeString(dir.resolve("main.txt"), "main\n");
        repo.add("main.txt");
        ObjectId targetTip = repo.commit("advance main", AUTHOR, AUTHOR);

        Rebase.Outcome outcome = rebaseFor(repo).rebase(targetTip, featureTip, REBASE_COMMITTER);
        assertThat(outcome.isClean()).isTrue();

        Commit second = (Commit) repo.objectStore().get(outcome.newTip()).orElseThrow();
        assertThat(second.message()).isEqualTo("second feature commit");
        Commit first = (Commit) repo.objectStore().get(second.parents().get(0)).orElseThrow();
        assertThat(first.message()).isEqualTo("first feature commit");
        assertThat(first.parents()).containsExactly(targetTip);

        var finalTree = (dev.cairn.vcs.object.Tree) repo.objectStore().get(second.treeId()).orElseThrow();
        assertThat(finalTree.entry("main.txt")).isPresent();
        assertThat(finalTree.entry("f1.txt")).isPresent();
        assertThat(finalTree.entry("f2.txt")).isPresent();
    }

    @Test
    void aGenuineConflictAbortsTheWholeRebaseWithNoPartialHistory(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("shared.txt"), "line one\nline two\nline three\n");
        repo.add("shared.txt");
        ObjectId base = repo.commit("base", AUTHOR, AUTHOR);

        repo.createBranch("feature", base);
        repo.checkout("feature");
        Files.writeString(dir.resolve("shared.txt"), "line one\nFEATURE CHANGED\nline three\n");
        repo.add("shared.txt");
        ObjectId featureCommit = repo.commit("feature changes line two", AUTHOR, AUTHOR);

        repo.checkout("main");
        Files.writeString(dir.resolve("shared.txt"), "line one\nMAIN CHANGED DIFFERENTLY\nline three\n");
        repo.add("shared.txt");
        ObjectId targetTip = repo.commit("main changes line two differently", AUTHOR, AUTHOR);

        Rebase.Outcome outcome = rebaseFor(repo).rebase(targetTip, featureCommit, REBASE_COMMITTER);
        assertThat(outcome.isClean()).isFalse();
        assertThat(outcome.conflictingSourceCommit()).isEqualTo(featureCommit);
        assertThat(outcome.newTip()).isEqualTo(targetTip);
    }

    @Test
    void generationNumbersAreRecomputableAfterFileGenerationStoreBacked(@TempDir Path dir) throws IOException {
        // Sanity check that Rebase works against the same FileGenerationStore-backed
        // GenerationStore a real Repository uses, not just an in-memory fake.
        Repository repo = Repository.init(dir);
        assertThat(repo.generations()).isInstanceOf(FileGenerationStore.class);
        Files.writeString(dir.resolve("a.txt"), "x");
        repo.add("a.txt");
        ObjectId commit = repo.commit("only", AUTHOR, AUTHOR);
        GenerationNumbers.computeAndStore(repo.objectStore(), repo.generations(), commit);
        Rebase.Outcome outcome = rebaseFor(repo).rebase(commit, commit, REBASE_COMMITTER);
        assertThat(outcome.alreadyUpToDate()).isTrue();
    }
}
