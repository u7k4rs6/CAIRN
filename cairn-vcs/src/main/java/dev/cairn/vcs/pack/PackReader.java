package dev.cairn.vcs.pack;

import dev.cairn.vcs.object.GitObject;
import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.object.ObjectKind;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * Parses a packfile written by {@link PackWriter} back into concrete objects,
 * resolving REF_DELTA entries against their base (which this reader requires to
 * already appear earlier in the same pack, matching what {@link PackWriter} always
 * produces; a "thin pack" whose deltas reference objects outside the pack, as Git
 * uses for some transfer cases, is out of scope here).
 *
 * <p><b>Complexity.</b> A single forward pass over the pack, O(pack size): each
 * entry is inflated once and, if a delta, applied against its already-resolved base
 * exactly once, so total reconstruction work is proportional to the pack's contents,
 * not to delta chain depth times object count (that bound is enforced at write time
 * by {@link PackWriter#MAX_DELTA_DEPTH}, which is what keeps any single chain from
 * being expensive in the first place).
 */
public final class PackReader {

    private PackReader() {
    }

    private record RawEntry(PackObjectType type, byte[] payload, ObjectId baseId) {
    }

    public static Map<ObjectId, GitObject> read(byte[] pack) {
        if (pack.length < 12 || pack[0] != 'P' || pack[1] != 'A' || pack[2] != 'C' || pack[3] != 'K') {
            throw new IllegalArgumentException("not a pack file: missing PACK magic");
        }
        int version = readUint32(pack, 4);
        if (version != 2) {
            throw new IllegalArgumentException("unsupported pack version: " + version);
        }
        int count = readUint32(pack, 8);

        int[] pos = {12};
        List<RawEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(readEntry(pack, pos));
        }

        Map<ObjectId, GitObject> resolved = new LinkedHashMap<>();
        for (RawEntry entry : entries) {
            byte[] body;
            ObjectKind kind;
            if (entry.type() == PackObjectType.REF_DELTA) {
                GitObject base = resolved.get(entry.baseId());
                if (base == null) {
                    throw new IllegalArgumentException("delta base " + entry.baseId() + " not resolved before its delta; "
                            + "thin packs (bases outside this pack) are not supported");
                }
                kind = base.kind();
                body = DeltaCodec.decode(ObjectBodies.bodyOf(base), entry.payload());
            } else {
                kind = PackObjectType.toObjectKind(entry.type());
                body = entry.payload();
            }
            GitObject object = ObjectBodies.reconstruct(kind, body);
            resolved.put(object.id(), object);
        }
        return resolved;
    }

    private static RawEntry readEntry(byte[] pack, int[] pos) {
        int first = pack[pos[0]++] & 0xFF;
        PackObjectType type = PackObjectType.fromCode((first >> 4) & 0x7);
        long size = first & 0x0F;
        int shift = 4;
        while ((first & 0x80) != 0) {
            first = pack[pos[0]++] & 0xFF;
            size |= (long) (first & 0x7F) << shift;
            shift += 7;
        }

        ObjectId baseId = null;
        if (type == PackObjectType.REF_DELTA) {
            byte[] idBytes = new byte[20];
            System.arraycopy(pack, pos[0], idBytes, 0, 20);
            pos[0] += 20;
            baseId = ObjectId.of(idBytes);
        }

        byte[] payload = inflate(pack, pos);
        return new RawEntry(type, payload, baseId);
    }

    /** Inflates one self-terminating zlib stream starting at {@code pos[0]}, advancing it past the stream's consumed bytes. */
    private static byte[] inflate(byte[] pack, int[] pos) {
        Inflater inflater = new Inflater();
        inflater.setInput(pack, pos[0], pack.length - pos[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IllegalStateException("truncated or corrupt pack entry");
                    }
                }
                out.write(buffer, 0, n);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalArgumentException("corrupt pack entry", e);
        } finally {
            int consumed = inflater.getTotalIn();
            inflater.end();
            pos[0] += consumed;
        }
        return out.toByteArray();
    }

    private static int readUint32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
