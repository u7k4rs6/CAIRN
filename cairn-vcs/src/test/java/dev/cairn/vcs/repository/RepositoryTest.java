package dev.cairn.vcs.repository;

import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryTest {

    private static final PersonIdent AUTHOR = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    @Test
    void initCreatesAnEmptyRepositoryPointingAtMain(@TempDir Path dir) {
        Repository repo = Repository.init(dir);
        assertThat(Repository.isRepository(dir)).isTrue();
        assertThat(repo.head().currentBranch()).contains(Repository.DEFAULT_BRANCH);
        assertThat(repo.head().resolve()).isEmpty();
        assertThat(repo.log()).isEmpty();
    }

    @Test
    void addAndCommitCreatesReachableObjects(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("README.md"), "# Cairn\n");

        repo.add("README.md");
        ObjectId commitId = repo.commit("Initial commit", AUTHOR, AUTHOR);

        assertThat(repo.objectStore().has(commitId)).isTrue();
        Commit commit = (Commit) repo.objectStore().get(commitId).orElseThrow();
        assertThat(commit.isRoot()).isTrue();
        assertThat(repo.objectStore().has(commit.treeId())).isTrue();

        assertThat(repo.refStore().resolve(Repository.DEFAULT_BRANCH)).contains(commitId);
        assertThat(repo.head().resolve()).contains(commitId);
    }

    @Test
    void secondCommitRecordsFirstAsParent(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one");
        repo.add("a.txt");
        ObjectId first = repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "two");
        repo.add("a.txt");
        ObjectId second = repo.commit("second", AUTHOR, AUTHOR);

        Commit secondCommit = (Commit) repo.objectStore().get(second).orElseThrow();
        assertThat(secondCommit.parents()).containsExactly(first);
    }

    @Test
    void logReturnsHistoryNewestFirst(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one");
        repo.add("a.txt");
        ObjectId first = repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "two");
        repo.add("a.txt");
        ObjectId second = repo.commit("second", AUTHOR,
                new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_100L, "+0000"));

        List<Commit> log = repo.log();
        assertThat(log).hasSize(2);
        assertThat(log.get(0).id()).isEqualTo(second);
        assertThat(log.get(1).id()).isEqualTo(first);
    }

    @Test
    void nestedPathsBuildIntermediateTrees(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.createDirectories(dir.resolve("src/main"));
        Files.writeString(dir.resolve("src/main/App.java"), "class App {}");

        repo.add("src/main/App.java");
        ObjectId commitId = repo.commit("nested", AUTHOR, AUTHOR);

        Commit commit = (Commit) repo.objectStore().get(commitId).orElseThrow();
        var rootTree = (dev.cairn.vcs.object.Tree) repo.objectStore().get(commit.treeId()).orElseThrow();
        assertThat(rootTree.entry("src")).isPresent();
        var srcId = rootTree.entry("src").get().id();
        var srcTree = (dev.cairn.vcs.object.Tree) repo.objectStore().get(srcId).orElseThrow();
        assertThat(srcTree.entry("main")).isPresent();
    }

    @Test
    void openRejectsNonRepository(@TempDir Path dir) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> Repository.open(dir));
    }
}
