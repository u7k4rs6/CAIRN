package dev.cairn.vcs.pack;

import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.ObjectKind;
import dev.cairn.vcs.store.ObjectStore;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes a Git-format packfile (magic {@code "PACK"}, version 2, entry count, then
 * each object) for a given list of object ids, choosing delta-against-a-prior-object-
 * of-the-same-kind encoding when it is smaller than storing the object in full.
 *
 * <p><b>Base selection.</b> The architecture doc names the simple heuristic: group by
 * similarity and pick the nearest prior version. Here "nearest prior" is the most
 * recently written object of the same {@link ObjectKind} in this pack, which is cheap
 * (O(1) lookup per object) and effective for the common case of adjacent revisions of
 * the same file appearing close together in a commit walk; it will not find a good
 * base across unrelated files that merely share a kind, unlike Git's actual
 * similarity-window sort. A delta is only used when it is smaller than the full
 * object; otherwise the object is stored whole, so this heuristic can never make a
 * pack bigger than storing everything loose-compressed would.
 *
 * <p><b>Bounded delta chains (section 6.3's decompression-bomb mitigation).</b> A
 * fixed cap ({@link #MAX_DELTA_DEPTH}) on how many deltas may chain together before a
 * full object must be written again, so reconstructing any single object is bounded
 * work, not proportional to the whole pack's history.
 */
public final class PackWriter {

    public static final int MAX_DELTA_DEPTH = 50;

    private final ObjectStore store;

    public PackWriter(ObjectStore store) {
        this.store = store;
    }

    public byte[] writePack(List<ObjectId> objectIds) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeHeader(body, objectIds.size());

        Map<ObjectKind, ObjectId> lastOfKind = new EnumMap<>(ObjectKind.class);
        Map<ObjectId, Integer> depthOf = new HashMap<>();

        for (ObjectId id : objectIds) {
            GitObject object = store.get(id).orElseThrow(() -> new IllegalArgumentException("missing object: " + id));
            byte[] rawBody = ObjectBodies.bodyOf(object);
            ObjectId baseCandidate = lastOfKind.get(object.kind());

            Optional<byte[]> delta = Optional.empty();
            if (baseCandidate != null && depthOf.getOrDefault(baseCandidate, 0) < MAX_DELTA_DEPTH) {
                byte[] baseBody = ObjectBodies.bodyOf(store.get(baseCandidate).orElseThrow());
                byte[] candidateDelta = DeltaCodec.encode(baseBody, rawBody);
                if (candidateDelta.length < rawBody.length) {
                    delta = Optional.of(candidateDelta);
                }
            }

            if (delta.isPresent()) {
                writeObjectHeader(body, PackObjectType.REF_DELTA, delta.get().length);
                body.writeBytes(baseCandidate.bytes());
                deflateInto(body, delta.get());
                depthOf.put(id, depthOf.getOrDefault(baseCandidate, 0) + 1);
            } else {
                writeObjectHeader(body, PackObjectType.of(object.kind()), rawBody.length);
                deflateInto(body, rawBody);
                depthOf.put(id, 0);
            }
            lastOfKind.put(object.kind(), id);
        }

        byte[] packWithoutChecksum = body.toByteArray();
        byte[] checksum = sha1(packWithoutChecksum);
        ByteArrayOutputStream full = new ByteArrayOutputStream(packWithoutChecksum.length + checksum.length);
        full.writeBytes(packWithoutChecksum);
        full.writeBytes(checksum);
        return full.toByteArray();
    }

    private void writeHeader(ByteArrayOutputStream out, int objectCount) {
        out.writeBytes(new byte[]{'P', 'A', 'C', 'K'});
        writeUint32(out, 2);
        writeUint32(out, objectCount);
    }

    private void writeUint32(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private void writeObjectHeader(ByteArrayOutputStream out, PackObjectType type, long size) {
        int firstByte = (type.code << 4) | (int) (size & 0x0F);
        size >>>= 4;
        if (size != 0) {
            firstByte |= 0x80;
        }
        out.write(firstByte);
        while (size != 0) {
            int b = (int) (size & 0x7F);
            size >>>= 7;
            if (size != 0) {
                b |= 0x80;
            }
            out.write(b);
        }
    }

    private void deflateInto(ByteArrayOutputStream out, byte[] data) {
        try (var deflate = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_COMPRESSION))) {
            deflate.write(data);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
