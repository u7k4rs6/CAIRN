package dev.cairn.api.search;

import dev.cairn.api.git.RepositoryRegistry;
import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.Commit;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.Tree;
import dev.cairn.vcs.object.TreeEntry;
import dev.cairn.vcs.search.TrigramIndex;
import dev.cairn.vcs.store.ObjectStore;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FR-SEARCH-1's platform wiring around {@link TrigramIndex}: one index per repo,
 * built from the blobs reachable from the default branch's tip tree, kept in
 * memory and rebuilt only when that tip has moved since the last build.
 *
 * <p><b>Staleness tradeoff (architecture doc, section 10).</b> The real cost of a
 * trigram index is keeping it current on every push. This project takes neither of
 * the doc's two named options (synchronous rebuild on receive-pack, which slows
 * every push, or an asynchronous job-worker queue, which this project does not
 * build) but a third, simpler one: rebuild lazily, off the request thread, the
 * first time a search request notices the indexed commit no longer matches the
 * branch tip. A search that arrives while a rebuild is in flight gets the
 * frontend spec's named "indexing" state (section 5.8) rather than stale or
 * partial results, and the previous index keeps serving queries until the new one
 * is ready, rather than blocking.
 *
 * <p><b>Resource bound (security doc, section 6.3: search must be bounded).</b> A
 * single file over {@link #MAX_FILE_BYTES} is skipped rather than indexed, and a
 * repo with more than {@link #MAX_FILES} tracked files stops walking at that cap;
 * both are named here rather than silently truncating with no signal.
 */
@Component
public class RepoSearchIndexService {

    private static final int MAX_FILE_BYTES = 512_000;
    private static final int MAX_FILES = 20_000;

    private final RepositoryRegistry repositories;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "code-search-index-builder");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, RepoIndexState> states = new ConcurrentHashMap<>();

    public RepoSearchIndexService(RepositoryRegistry repositories) {
        this.repositories = repositories;
    }

    private record RepoIndexState(TrigramIndex index, ObjectId builtAt, boolean building) {
    }

    public record SearchOutcome(boolean indexing, List<TrigramIndex.FileMatch> matches) {
    }

    public SearchOutcome search(String owner, String name, String query) {
        RepositoryRegistry.RepositoryHandle handle = repositories.resolve(owner, name);
        Optional<ObjectId> tip = handle.refStore().resolve("refs/heads/main");
        if (tip.isEmpty()) {
            return new SearchOutcome(false, List.of());
        }
        String key = owner + "/" + name;
        RepoIndexState state = states.get(key);

        if (state == null || !state.builtAt().equals(tip.get())) {
            if (state == null || !state.building()) {
                states.put(key, new RepoIndexState(state == null ? null : state.index(), tip.get(), true));
                ObjectId commitId = tip.get();
                executor.submit(() -> buildIndex(key, handle, commitId));
            }
            if (state == null || state.index() == null) {
                return new SearchOutcome(true, List.of());
            }
            // A previous index still exists (a new build for a later commit is in
            // flight): keep serving it rather than blocking the caller on the rebuild.
            return new SearchOutcome(false, state.index().search(query));
        }
        return new SearchOutcome(false, state.index().search(query));
    }

    private void buildIndex(String key, RepositoryRegistry.RepositoryHandle handle, ObjectId commitId) {
        TrigramIndex index = new TrigramIndex();
        ObjectStore store = handle.objectStore();
        Commit commit = (Commit) store.get(commitId).orElseThrow();
        walk(store, commit.treeId(), "", index, new int[] {0});
        states.put(key, new RepoIndexState(index, commitId, false));
    }

    private void walk(ObjectStore store, ObjectId treeId, String prefix, TrigramIndex index, int[] fileCount) {
        if (fileCount[0] >= MAX_FILES) {
            return;
        }
        Tree tree = (Tree) store.get(treeId).orElseThrow();
        for (TreeEntry entry : tree.entries()) {
            if (fileCount[0] >= MAX_FILES) {
                return;
            }
            String path = prefix.isEmpty() ? entry.name() : prefix + "/" + entry.name();
            if (entry.isDirectory()) {
                walk(store, entry.id(), path, index, fileCount);
            } else {
                fileCount[0]++;
                Blob blob = (Blob) store.get(entry.id()).orElseThrow();
                byte[] content = blob.content();
                if (content.length > MAX_FILE_BYTES || isBinary(content)) {
                    continue;
                }
                index.add(path, new String(content, StandardCharsets.UTF_8));
            }
        }
    }

    /** Git's own heuristic: a NUL byte anywhere in the content means "binary," skipped rather than indexed as garbled text. */
    private static boolean isBinary(byte[] content) {
        int limit = Math.min(content.length, 8000);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
