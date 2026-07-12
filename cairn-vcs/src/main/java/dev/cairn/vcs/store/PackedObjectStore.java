package dev.cairn.vcs.store;

import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.pack.PackReader;

import java.util.Map;
import java.util.Optional;

/**
 * A read-only {@link ObjectStore} backed by one loaded packfile. Immutable by design,
 * mirroring real Git: a pack is never edited in place, only superseded by a new one
 * written during a repack (architecture doc, section 4.2). {@code put} therefore
 * always throws; new objects go to a {@link LooseObjectStore} until a maintenance
 * operation packs them.
 *
 * <p><b>Complexity.</b> The whole pack is decoded into memory up front by
 * {@link PackReader}, so {@code get}/{@code has} are O(1) map lookups with no
 * per-call delta-chain work; the cost is paid once at construction (O(pack size)),
 * traded here for implementation simplicity over Git's real packed store, which
 * decodes an object lazily from a byte offset (via a separate `.idx` file) and
 * applies its delta chain only when that specific object is requested.
 */
public final class PackedObjectStore implements ObjectStore {

    private final Map<ObjectId, GitObject> objects;

    public PackedObjectStore(byte[] packBytes) {
        this.objects = PackReader.read(packBytes);
    }

    @Override
    public ObjectId put(GitObject obj) {
        throw new UnsupportedOperationException("a packed object store is read-only; write to a LooseObjectStore instead");
    }

    @Override
    public Optional<GitObject> get(ObjectId id) {
        return Optional.ofNullable(objects.get(id));
    }

    @Override
    public boolean has(ObjectId id) {
        return objects.containsKey(id);
    }

    public int objectCount() {
        return objects.size();
    }
}
