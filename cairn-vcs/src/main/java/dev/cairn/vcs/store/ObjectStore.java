package dev.cairn.vcs.store;

import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;

import java.util.Optional;

/**
 * Strategy pattern: hides where and how objects live behind one narrow interface, so
 * the DAG, diff, and merge code above never learns whether it is talking to loose
 * storage or a packfile (architecture doc, section 4.2). Adding a backend means
 * implementing this interface, not touching a single caller (open/closed principle).
 */
public interface ObjectStore {

    /**
     * Writes {@code obj} if not already present and returns its id.
     * O(n) in the object's size: it must be hashed, and in most implementations
     * compressed and written, all proportional to the object's byte length.
     */
    ObjectId put(GitObject obj);

    /**
     * O(1) expected to locate, then O(n) to read and reconstruct where n is the
     * object's size (and, for a packed store, its delta chain depth).
     */
    Optional<GitObject> get(ObjectId id);

    /** O(1) expected: existence is a lookup, not a read. */
    boolean has(ObjectId id);
}
