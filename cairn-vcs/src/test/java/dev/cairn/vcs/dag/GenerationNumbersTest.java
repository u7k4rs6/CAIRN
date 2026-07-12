package dev.cairn.vcs.dag;

import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.ref.FileRefStore;
import dev.cairn.vcs.ref.RefStore;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationNumbersTest {

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");

    @Test
    void rootCommitHasGenerationOne(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId tree = store.put(new Tree(List.of()));
        ObjectId root = store.put(new Commit(tree, List.of(), PERSON, PERSON, "root"));

        int gen = GenerationNumbers.computeAndStore(store, generations, root);

        assertThat(gen).isEqualTo(1);
    }

    @Test
    void mergeCommitGenerationIsOneMoreThanTheDeeperParent(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId tree = store.put(new Tree(List.of()));

        ObjectId root = store.put(new Commit(tree, List.of(), PERSON, PERSON, "root"));
        ObjectId shortSide = store.put(new Commit(tree, List.of(root), PERSON, PERSON, "short"));
        ObjectId mid = store.put(new Commit(tree, List.of(root), PERSON, PERSON, "mid"));
        ObjectId longSide = store.put(new Commit(tree, List.of(mid), PERSON, PERSON, "long"));
        ObjectId merge = store.put(new Commit(tree, List.of(shortSide, longSide), PERSON, PERSON, "merge"));

        assertThat(GenerationNumbers.computeAndStore(store, generations, merge)).isEqualTo(4);
        assertThat(generations.get(root)).hasValue(1);
        assertThat(generations.get(shortSide)).hasValue(2);
        assertThat(generations.get(mid)).hasValue(2);
        assertThat(generations.get(longSide)).hasValue(3);
    }

    @Test
    void recomputeAllPopulatesEveryReachableCommitFromRefs(@TempDir Path dir) {
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        RefStore refs = new FileRefStore(dir.resolve("refs"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        ObjectId tree = store.put(new Tree(List.of()));

        ObjectId c0 = store.put(new Commit(tree, List.of(), PERSON, PERSON, "c0"));
        ObjectId c1 = store.put(new Commit(tree, List.of(c0), PERSON, PERSON, "c1"));
        ObjectId c2 = store.put(new Commit(tree, List.of(c1), PERSON, PERSON, "c2"));
        refs.update("refs/heads/main", c2);

        GenerationNumbers.recomputeAll(store, refs, generations);

        assertThat(generations.get(c0)).hasValue(1);
        assertThat(generations.get(c1)).hasValue(2);
        assertThat(generations.get(c2)).hasValue(3);
    }
}
