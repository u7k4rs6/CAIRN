package dev.cairn.vcs.repository;

import dev.cairn.vcs.merge.MergeEngine;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end porcelain tests mirroring the PRD's MVP gate directly: divergent
 * branches with a shared ancestor merge, a conflicting change is reported rather
 * than silently lost, and a criss-cross case resolves via the recursive merge base.
 */
class RepositoryMergeTest {

    private static final PersonIdent AUTHOR = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    @Test
    void divergentBranchesWithSharedAncestorMergeCleanly(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("base.txt"), "base\n");
        repo.add("base.txt");
        ObjectId base = repo.commit("base", AUTHOR, AUTHOR);

        repo.createBranch("feature", base);
        repo.checkout("feature");
        Files.writeString(dir.resolve("feature.txt"), "feature work\n");
        repo.add("feature.txt");
        ObjectId featureTip = repo.commit("feature work", AUTHOR, AUTHOR);

        repo.checkout("main");
        Files.writeString(dir.resolve("main.txt"), "main work\n");
        repo.add("main.txt");
        repo.commit("main work", AUTHOR, AUTHOR);

        MergeEngine.Outcome outcome = repo.merge("feature", AUTHOR, AUTHOR, "merge feature");

        assertThat(outcome.isClean()).isTrue();
        assertThat(outcome.fastForward()).isFalse();
        assertThat(Files.exists(dir.resolve("main.txt"))).isTrue();
        assertThat(Files.exists(dir.resolve("feature.txt"))).isTrue();
        assertThat(Files.exists(dir.resolve("base.txt"))).isTrue();
        assertThat(featureTip).isNotNull();
    }

    @Test
    void fastForwardMergeAdvancesWithoutAMergeCommit(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("base.txt"), "base\n");
        repo.add("base.txt");
        ObjectId base = repo.commit("base", AUTHOR, AUTHOR);

        repo.createBranch("feature", base);
        repo.checkout("feature");
        Files.writeString(dir.resolve("feature.txt"), "feature\n");
        repo.add("feature.txt");
        repo.commit("feature work", AUTHOR, AUTHOR);

        repo.checkout("main");
        MergeEngine.Outcome outcome = repo.merge("feature", AUTHOR, AUTHOR, "ff");

        assertThat(outcome.fastForward()).isTrue();
        assertThat(Files.exists(dir.resolve("feature.txt"))).isTrue();
    }

    @Test
    void conflictingChangeIsReportedNotSilentlyLost(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("shared.txt"), "line one\nline two\nline three\n");
        repo.add("shared.txt");
        ObjectId base = repo.commit("base", AUTHOR, AUTHOR);

        repo.createBranch("feature", base);
        repo.checkout("feature");
        Files.writeString(dir.resolve("shared.txt"), "line one\nFEATURE CHANGE\nline three\n");
        repo.add("shared.txt");
        repo.commit("feature edit", AUTHOR, AUTHOR);

        repo.checkout("main");
        Files.writeString(dir.resolve("shared.txt"), "line one\nMAIN CHANGE\nline three\n");
        repo.add("shared.txt");
        repo.commit("main edit", AUTHOR, AUTHOR);

        MergeEngine.Outcome outcome = repo.merge("feature", AUTHOR, AUTHOR, "merge feature");

        assertThat(outcome.isClean()).isFalse();
        assertThat(outcome.conflicts()).hasSize(1);
        var conflict = outcome.conflicts().get(0);
        assertThat(conflict.path()).isEqualTo("shared.txt");
        assertThat(conflict.ours()).contains("MAIN CHANGE\n");
        assertThat(conflict.theirs()).contains("FEATURE CHANGE\n");
        // No commit is created for a conflicted merge: HEAD must not have advanced past "main edit".
        assertThat(repo.log()).hasSize(2);
    }

    @Test
    void crissCrossHistoryResolvesViaRecursiveMergeBase(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("base.txt"), "base\n");
        repo.add("base.txt");
        ObjectId c0 = repo.commit("base", AUTHOR, AUTHOR);

        repo.createBranch("a", c0);
        repo.createBranch("b", c0);

        repo.checkout("a");
        Files.writeString(dir.resolve("a.txt"), "on a\n");
        repo.add("a.txt");
        ObjectId a1 = repo.commit("a1", AUTHOR, AUTHOR);

        repo.checkout("b");
        Files.writeString(dir.resolve("b.txt"), "on b\n");
        repo.add("b.txt");
        ObjectId b1 = repo.commit("b1", AUTHOR, AUTHOR);

        // First crossing merge: on branch a, merge b (parents a1, b1).
        repo.checkout("a");
        MergeEngine.Outcome m1 = repo.merge("b", AUTHOR, AUTHOR, "merge b into a");
        assertThat(m1.isClean()).isTrue();
        ObjectId m1Id = repo.head().resolve().orElseThrow();

        // Second crossing merge: on branch b, merge in a1 directly (not branch "a",
        // which has already moved to m1) so the result does not descend from m1 -
        // this is what produces two lowest common ancestors instead of one.
        repo.checkout("b");
        MergeEngine.Outcome m2 = repo.merge(a1.hex(), AUTHOR, AUTHOR, "merge a1 into b");
        assertThat(m2.isClean()).isTrue();
        ObjectId m2Id = repo.head().resolve().orElseThrow();

        assertThat(m1Id).isNotEqualTo(m2Id);

        // Now merging m1 and m2 must go through the recursive strategy: they share
        // exactly two lowest common ancestors, a1 and b1.
        repo.checkout("a");
        MergeEngine.Outcome criss = repo.merge(m2Id.hex(), AUTHOR, AUTHOR, "criss-cross merge");

        assertThat(criss.isClean()).isTrue();
        assertThat(Files.exists(dir.resolve("a.txt"))).isTrue();
        assertThat(Files.exists(dir.resolve("b.txt"))).isTrue();
    }
}
