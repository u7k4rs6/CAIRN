package dev.cairn.vcs.repository;

import dev.cairn.vcs.object.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** FR: porcelain {@code status}, previously missing despite the PRD's Tier 1 list naming it alongside init/add/commit/branch/checkout/log/diff/merge. */
class RepositoryStatusTest {

    private static final PersonIdent AUTHOR = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    @Test
    void aFreshRepositoryIsClean(@TempDir Path dir) {
        Repository repo = Repository.init(dir);
        assertThat(repo.status().isClean()).isTrue();
    }

    @Test
    void anUnwrittenFileIsUntracked(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("README.md"), "# Cairn\n");

        Repository.Status status = repo.status();
        assertThat(status.untracked()).containsExactly("README.md");
        assertThat(status.isClean()).isFalse();
    }

    @Test
    void aStagedNewFileIsStagedAdded(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("README.md"), "# Cairn\n");
        repo.add("README.md");

        Repository.Status status = repo.status();
        assertThat(status.stagedAdded()).containsExactly("README.md");
        assertThat(status.untracked()).isEmpty();
    }

    @Test
    void aCommittedFileWithNoFurtherChangesIsClean(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("README.md"), "# Cairn\n");
        repo.add("README.md");
        repo.commit("initial", AUTHOR, AUTHOR);

        assertThat(repo.status().isClean()).isTrue();
    }

    @Test
    void editingAWorkingFileWithoutReAddingIsUnstagedModified(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one");
        repo.add("a.txt");
        repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "two");

        Repository.Status status = repo.status();
        assertThat(status.unstagedModified()).containsExactly("a.txt");
        assertThat(status.stagedModified()).isEmpty();
    }

    @Test
    void reAddingAnEditedFileMovesItToStagedModified(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one");
        repo.add("a.txt");
        repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "two");
        repo.add("a.txt");

        Repository.Status status = repo.status();
        assertThat(status.stagedModified()).containsExactly("a.txt");
        assertThat(status.unstagedModified()).isEmpty();
    }

    @Test
    void deletingATrackedFileFromDiskIsUnstagedDeleted(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one");
        repo.add("a.txt");
        repo.commit("first", AUTHOR, AUTHOR);

        Files.delete(dir.resolve("a.txt"));

        Repository.Status status = repo.status();
        assertThat(status.unstagedDeleted()).containsExactly("a.txt");
    }

    @Test
    void removingAFileFromTheIndexAfterACommitIsStagedDeleted(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one");
        repo.add("a.txt");
        repo.commit("first", AUTHOR, AUTHOR);

        repo.index().remove("a.txt");

        Repository.Status status = repo.status();
        assertThat(status.stagedDeleted()).containsExactly("a.txt");
    }

    @Test
    void multipleBucketsCanApplySimultaneously(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("tracked.txt"), "one");
        repo.add("tracked.txt");
        repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("tracked.txt"), "two");
        Files.writeString(dir.resolve("new-untracked.txt"), "brand new");

        Repository.Status status = repo.status();
        assertThat(status.unstagedModified()).containsExactly("tracked.txt");
        assertThat(status.untracked()).containsExactly("new-untracked.txt");
        assertThat(status.isClean()).isFalse();
    }

    @Test
    void theCairnDirectoryItselfIsNeverReportedAsUntracked(@TempDir Path dir) {
        Repository repo = Repository.init(dir);
        assertThat(repo.status().untracked()).isEmpty();
    }
}
