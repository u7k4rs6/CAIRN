package dev.cairn.vcs.object;

/**
 * A content-addressed object: a blob, a tree, a commit, or a tag.
 *
 * <p>{@code id()} is the hash of {@code serialize()}, so identical content always
 * produces the identical id and is therefore stored once (architecture doc, 4.1).
 */
public interface GitObject {

    ObjectKind kind();

    /**
     * The serialized wire form: {@code "<kind> <byteLength>\0<body>"}. Hashing this,
     * not just the body, is what makes a blob and a tree that happen to hold the same
     * bytes still hash differently.
     */
    byte[] serialize();

    /**
     * O(n) in the size of {@link #serialize()}: every byte must be hashed. Callers
     * that need the id repeatedly should cache it rather than recompute.
     */
    default ObjectId id() {
        return ObjectId.hash(serialize());
    }
}
