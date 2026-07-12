package dev.cairn.vcs.dag;

import dev.cairn.vcs.object.ObjectId;

import java.util.OptionalInt;

/**
 * Persists each commit's generation number out of band from the object it describes:
 * {@code gen(commit) = 1 + max(gen(parent))} over its parents, roots at 1 (architecture
 * doc, section 4.4). It cannot live inside the {@code Commit} object itself, since that
 * would change the object's hash; Git's own commit-graph file is the same idea, kept
 * separate from the loose/packed object data it describes.
 */
public interface GenerationStore {

    OptionalInt get(ObjectId commitId);

    void put(ObjectId commitId, int generation);
}
