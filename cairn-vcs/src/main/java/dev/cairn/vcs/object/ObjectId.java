package dev.cairn.vcs.object;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A content address: the SHA-1 hash of a {@link GitObject}'s serialized bytes.
 *
 * <p><b>Complexity.</b> Computing an id is O(n) in the size of the serialized object,
 * since every byte must be hashed; comparing or using an id as a map key is O(1) in
 * its length (a fixed 20 bytes), so once computed, identity and lookup are cheap even
 * though producing the identity in the first place is not.
 *
 * <p><b>Why SHA-1, not something newer.</b> Cairn deliberately matches Git's canonical
 * object hashing (see {@code DECISIONS.md}) rather than picking a stronger modern hash,
 * because the M5 acceptance criterion is that a real {@code git} client can clone from
 * and push to Cairn. That requires the ids Cairn advertises for refs to be the same ids
 * the client independently recomputes from the objects it receives, which means matching
 * Git's on-the-wire object encoding and hash algorithm exactly, not just conceptually.
 */
public final class ObjectId implements Comparable<ObjectId> {

    private static final String ALGORITHM = "SHA-1";
    private static final HexFormat HEX = HexFormat.of();

    private final byte[] bytes;

    private ObjectId(byte[] bytes) {
        this.bytes = bytes;
    }

    public static ObjectId of(byte[] hash) {
        return new ObjectId(hash.clone());
    }

    public static ObjectId fromHex(String hex) {
        return new ObjectId(HEX.parseHex(hex));
    }

    /** Hashes {@code content} to produce the id it would be stored under. */
    public static ObjectId hash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return new ObjectId(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " must be available on any JVM", e);
        }
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String hex() {
        return HEX.formatHex(bytes);
    }

    /** First two hex characters, used as the loose-object directory shard. */
    public String shard() {
        return hex().substring(0, 2);
    }

    /** Remaining hex characters after the shard, used as the loose-object filename. */
    public String rest() {
        return hex().substring(2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectId other)) return false;
        return java.util.Arrays.equals(bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(bytes);
    }

    @Override
    public int compareTo(ObjectId o) {
        return hex().compareTo(o.hex());
    }

    @Override
    public String toString() {
        return hex();
    }
}
