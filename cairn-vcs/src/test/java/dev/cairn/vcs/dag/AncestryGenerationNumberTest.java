package dev.cairn.vcs.dag;

import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.PersonIdent;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.store.LooseObjectStore;
import dev.cairn.vcs.store.ObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the PRD acceptance criterion directly: on a deep history, ancestry and
 * merge-base terminate early using generation numbers instead of walking the whole
 * graph. A counting {@link ObjectStore} wrapper stands in for "instrumenting the
 * walk" (architecture doc, section 4.4): every commit visited requires at least one
 * {@code get}, so the count is a faithful lower bound on nodes touched.
 */
class AncestryGenerationNumberTest {

    private static final PersonIdent PERSON = new PersonIdent("Ada", "ada@cairn.dev", 1_700_000_000L, "+0000");
    private static final int CHAIN_LENGTH = 1000;

    private static final class CountingObjectStore implements ObjectStore {
        private final ObjectStore delegate;
        final AtomicInteger getCalls = new AtomicInteger();

        CountingObjectStore(ObjectStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public ObjectId put(GitObject obj) {
            return delegate.put(obj);
        }

        @Override
        public Optional<GitObject> get(ObjectId id) {
            getCalls.incrementAndGet();
            return delegate.get(id);
        }

        @Override
        public boolean has(ObjectId id) {
            return delegate.has(id);
        }
    }

    /** Builds a linear chain of {@code length} commits (each with an empty tree) and returns their ids, root first. */
    private List<ObjectId> buildLinearChain(ObjectStore store, GenerationStore generations, int length) {
        List<ObjectId> chain = new ArrayList<>();
        ObjectId treeId = store.put(new Tree(List.of()));
        ObjectId parent = null;
        for (int i = 0; i < length; i++) {
            List<ObjectId> parents = parent == null ? List.of() : List.of(parent);
            Commit commit = new Commit(treeId, parents, PERSON, PERSON, "commit " + i);
            ObjectId id = store.put(commit);
            GenerationNumbers.computeAndStore(store, generations, id);
            chain.add(id);
            parent = id;
        }
        return chain;
    }

    @Test
    void negativeAncestryIsConstantTimeRegardlessOfHistoryDepth(@TempDir Path dir) {
        ObjectStore rawStore = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        List<ObjectId> chain = buildLinearChain(rawStore, generations, CHAIN_LENGTH);

        CountingObjectStore counting = new CountingObjectStore(rawStore);
        Ancestry ancestry = new Ancestry(counting, generations);

        // chain.get(900) is a descendant of chain.get(100), so it structurally cannot
        // be an ancestor of it: the generation comparison alone answers this.
        ObjectId descendant = chain.get(900);
        ObjectId earlier = chain.get(100);

        boolean isAncestor = ancestry.isAncestor(descendant, earlier);

        assertThat(isAncestor).isFalse();
        assertThat(counting.getCalls.get())
                .as("negative ancestry must not walk the graph at all")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void positiveAncestryWalksOnlyTheFrontierBetweenTheTwoCommits(@TempDir Path dir) {
        ObjectStore rawStore = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        List<ObjectId> chain = buildLinearChain(rawStore, generations, CHAIN_LENGTH);

        CountingObjectStore counting = new CountingObjectStore(rawStore);
        Ancestry ancestry = new Ancestry(counting, generations);

        // chain.get(990) is only 10 commits back from the tip (999): the walk should
        // touch on the order of that gap, not the full 1000-commit history behind it.
        ObjectId tip = chain.get(999);
        ObjectId nearAncestor = chain.get(990);

        boolean isAncestor = ancestry.isAncestor(nearAncestor, tip);

        assertThat(isAncestor).isTrue();
        assertThat(counting.getCalls.get())
                .as("walk from tip to a near ancestor should be proportional to the gap, not the full chain")
                .isLessThan(CHAIN_LENGTH / 4);
    }

    @Test
    void mergeBaseOfADirectAncestorPairSkipsFullReachabilityEnumeration(@TempDir Path dir) {
        ObjectStore rawStore = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore generations = new FileGenerationStore(dir.resolve("generations"));
        List<ObjectId> chain = buildLinearChain(rawStore, generations, CHAIN_LENGTH);

        CountingObjectStore counting = new CountingObjectStore(rawStore);
        Ancestry ancestry = new Ancestry(counting, generations);

        ObjectId tip = chain.get(999);
        ObjectId nearAncestor = chain.get(990);

        List<ObjectId> bases = ancestry.mergeBases(tip, nearAncestor);

        assertThat(bases).containsExactly(nearAncestor);
        assertThat(counting.getCalls.get())
                .as("merge-base of a direct ancestor pair should take the O(1)-ish fast path, not enumerate all reachable commits")
                .isLessThan(CHAIN_LENGTH / 4);
    }

    @Test
    void generationsSurviveAcrossStoreReopen(@TempDir Path dir) {
        Path genFile = dir.resolve("generations");
        ObjectStore store = new LooseObjectStore(dir.resolve("objects"));
        GenerationStore first = new FileGenerationStore(genFile);
        List<ObjectId> chain = buildLinearChain(store, first, 5);

        GenerationStore reopened = new FileGenerationStore(genFile);
        assertThat(reopened.get(chain.get(4))).hasValue(5);
        assertThat(reopened.get(chain.get(0))).hasValue(1);
    }
}
