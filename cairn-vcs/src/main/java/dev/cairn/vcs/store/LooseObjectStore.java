package dev.cairn.vcs.store;

import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.GitObjects;
import dev.cairn.vcs.object.ObjectId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * One zlib-compressed file per object, keyed by id, sharded into {@code xx/yyyy...}
 * directories the way Git shards loose objects (so no directory ever holds a huge
 * flat list of files).
 *
 * <p><b>Complexity.</b> {@code put} is O(n): hash + deflate + write, where n is the
 * serialized object size; locating the path is O(1). {@code get} is O(1) to locate
 * plus O(n) to read and inflate. Space is O(sum of every object's compressed size),
 * since each version is stored in full: three revisions of a file cost three
 * (compressed) copies. That inefficiency is exactly what {@link PackedObjectStore}
 * exists to fix; loose storage trades space for write simplicity and is the
 * always-correct fallback every fresh write lands in first.
 */
public final class LooseObjectStore implements ObjectStore {

    private final Path root;

    public LooseObjectStore(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path pathFor(ObjectId id) {
        return root.resolve(id.shard()).resolve(id.rest());
    }

    @Override
    public ObjectId put(GitObject obj) {
        byte[] raw = obj.serialize();
        ObjectId id = ObjectId.hash(raw);
        Path path = pathFor(id);
        if (Files.exists(path)) {
            return id;
        }
        try {
            Files.createDirectories(path.getParent());
            Path tmp = Files.createTempFile(path.getParent(), "obj", ".tmp");
            try (var out = new DeflaterOutputStream(Files.newOutputStream(tmp), new Deflater(Deflater.BEST_SPEED))) {
                out.write(raw);
            }
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return id;
    }

    @Override
    public Optional<GitObject> get(ObjectId id) {
        Path path = pathFor(id);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try (var in = new InflaterInputStream(Files.newInputStream(path))) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return Optional.of(GitObjects.deserialize(buffer.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean has(ObjectId id) {
        return Files.exists(pathFor(id));
    }
}
