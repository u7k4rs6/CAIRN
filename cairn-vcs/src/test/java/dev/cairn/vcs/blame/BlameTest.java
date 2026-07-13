package dev.cairn.vcs.blame;

import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.repository.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-BROWSE-1: attribute each line to the commit that last changed it. */
class BlameTest {

    private static final PersonIdent AUTHOR = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    @Test
    void everyLineOfARootCommitIsAttributedToIt(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\n");
        repo.add("a.txt");
        ObjectId commit = repo.commit("initial", AUTHOR, AUTHOR);

        List<Blame.LineBlame> blame = Blame.blame(repo.objectStore(), commit, "a.txt");
        assertThat(blame).hasSize(3);
        assertThat(blame).allSatisfy(l -> assertThat(l.commitId()).isEqualTo(commit));
        assertThat(blame.get(0).line()).isEqualTo("one\n");
        assertThat(blame.get(1).lineNumber()).isEqualTo(2);
    }

    @Test
    void anUnchangedLineKeepsItsOriginalCommitAcrossLaterEdits(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\n");
        repo.add("a.txt");
        ObjectId first = repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "one\nTWO-CHANGED\nthree\n");
        repo.add("a.txt");
        ObjectId second = repo.commit("second", AUTHOR, AUTHOR);

        List<Blame.LineBlame> blame = Blame.blame(repo.objectStore(), second, "a.txt");
        assertThat(blame).hasSize(3);
        assertThat(blame.get(0).commitId()).isEqualTo(first);
        assertThat(blame.get(0).line()).isEqualTo("one\n");
        assertThat(blame.get(1).commitId()).isEqualTo(second);
        assertThat(blame.get(1).line()).isEqualTo("TWO-CHANGED\n");
        assertThat(blame.get(2).commitId()).isEqualTo(first);
        assertThat(blame.get(2).line()).isEqualTo("three\n");
    }

    @Test
    void anAppendedLineIsAttributedToTheCommitThatAddedItOnly(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "one\ntwo\n");
        repo.add("a.txt");
        ObjectId first = repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "one\ntwo\nthree\n");
        repo.add("a.txt");
        ObjectId second = repo.commit("second", AUTHOR, AUTHOR);

        List<Blame.LineBlame> blame = Blame.blame(repo.objectStore(), second, "a.txt");
        assertThat(blame).hasSize(3);
        assertThat(blame.get(0).commitId()).isEqualTo(first);
        assertThat(blame.get(1).commitId()).isEqualTo(first);
        assertThat(blame.get(2).commitId()).isEqualTo(second);
        assertThat(blame.get(2).line()).isEqualTo("three\n");
    }

    @Test
    void survivesThreeGenerationsAttributingEachLineToItsTrueOrigin(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("a.txt"), "alpha\nbeta\ngamma\n");
        repo.add("a.txt");
        ObjectId gen1 = repo.commit("gen1", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "alpha\nBETA-2\ngamma\n");
        repo.add("a.txt");
        ObjectId gen2 = repo.commit("gen2", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("a.txt"), "alpha\nBETA-2\nGAMMA-3\n");
        repo.add("a.txt");
        ObjectId gen3 = repo.commit("gen3", AUTHOR, AUTHOR);

        List<Blame.LineBlame> blame = Blame.blame(repo.objectStore(), gen3, "a.txt");
        assertThat(blame.get(0).commitId()).isEqualTo(gen1);
        assertThat(blame.get(1).commitId()).isEqualTo(gen2);
        assertThat(blame.get(2).commitId()).isEqualTo(gen3);
    }

    @Test
    void aFileAddedPartwayThroughHistoryIsNotBlamedBeforeItExisted(@TempDir Path dir) throws IOException {
        Repository repo = Repository.init(dir);
        Files.writeString(dir.resolve("first.txt"), "unrelated\n");
        repo.add("first.txt");
        repo.commit("first", AUTHOR, AUTHOR);

        Files.writeString(dir.resolve("second.txt"), "brand new file\n");
        repo.add("second.txt");
        ObjectId adding = repo.commit("adds second.txt", AUTHOR, AUTHOR);

        List<Blame.LineBlame> blame = Blame.blame(repo.objectStore(), adding, "second.txt");
        assertThat(blame).hasSize(1);
        assertThat(blame.get(0).commitId()).isEqualTo(adding);
    }
}
