package dev.cairn.vcs.pack;

import dev.cairn.vcs.diff.Edit;
import dev.cairn.vcs.diff.EditType;
import dev.cairn.vcs.diff.MyersDiff;

import java.io.ByteArrayOutputStream;
import java.util.AbstractList;
import java.util.List;

/**
 * Encodes and decodes Git's delta instruction format: a target object is represented
 * as a source size, a target size, and a stream of COPY (take bytes from the base)
 * and INSERT (literal bytes) instructions. This is the same format real Git packs
 * use, which is what lets a real {@code git} client apply deltas Cairn produces
 * during a clone (architecture doc, section 4.2).
 *
 * <p><b>How the copy/insert script is found.</b> Rather than a rolling-hash greedy
 * matcher (Git's own approach), this reuses {@link MyersDiff} at the byte level: an
 * {@code EQUAL} edit becomes one or more COPY instructions (split at 64KiB, Git's
 * conventional per-instruction copy limit) and an {@code INSERT} edit becomes literal
 * bytes. This is a real tradeoff, not a simplification worth hiding: Myers is
 * O(N D), fine when base and target are similar (small D, the case delta encoding
 * targets in the first place), but a rolling-hash matcher would stay linear even when
 * they are not. Reusing Myers buys implementation and correctness confidence (the
 * algorithm is already tested exhaustively in {@code MyersDiffTest}) at the cost of
 * that worst case.
 */
public final class DeltaCodec {

    private static final int MAX_COPY_LENGTH = 0x10000;

    private DeltaCodec() {
    }

    public static byte[] encode(byte[] base, byte[] target) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeVarInt(out, base.length);
        writeVarInt(out, target.length);

        List<Edit> edits = new MyersDiff<Byte>().diff(asList(base), asList(target));
        for (Edit edit : edits) {
            switch (edit.type()) {
                case EQUAL -> writeCopyInstructions(out, edit.origStart(), edit.origLength());
                case INSERT -> writeInsertInstructions(out, target, edit.revStart(), edit.revLength());
                case DELETE -> {
                    // Consumes base bytes without producing target bytes: nothing to emit.
                }
            }
        }
        return out.toByteArray();
    }

    public static byte[] decode(byte[] base, byte[] delta) {
        int[] pos = {0};
        long sourceSize = readVarInt(delta, pos);
        long targetSize = readVarInt(delta, pos);
        if (sourceSize != base.length) {
            throw new IllegalArgumentException("delta source size " + sourceSize + " does not match base length " + base.length);
        }
        byte[] target = new byte[Math.toIntExact(targetSize)];
        int outPos = 0;

        while (pos[0] < delta.length) {
            int instruction = delta[pos[0]++] & 0xFF;
            if ((instruction & 0x80) != 0) {
                long offset = 0;
                long length = 0;
                if ((instruction & 0x01) != 0) offset |= (long) (delta[pos[0]++] & 0xFF);
                if ((instruction & 0x02) != 0) offset |= (long) (delta[pos[0]++] & 0xFF) << 8;
                if ((instruction & 0x04) != 0) offset |= (long) (delta[pos[0]++] & 0xFF) << 16;
                if ((instruction & 0x08) != 0) offset |= (long) (delta[pos[0]++] & 0xFF) << 24;
                if ((instruction & 0x10) != 0) length |= (long) (delta[pos[0]++] & 0xFF);
                if ((instruction & 0x20) != 0) length |= (long) (delta[pos[0]++] & 0xFF) << 8;
                if ((instruction & 0x40) != 0) length |= (long) (delta[pos[0]++] & 0xFF) << 16;
                if (length == 0) {
                    length = MAX_COPY_LENGTH;
                }
                System.arraycopy(base, Math.toIntExact(offset), target, outPos, Math.toIntExact(length));
                outPos += length;
            } else if (instruction != 0) {
                System.arraycopy(delta, pos[0], target, outPos, instruction);
                pos[0] += instruction;
                outPos += instruction;
            } else {
                throw new IllegalArgumentException("reserved delta instruction byte 0 encountered");
            }
        }
        if (outPos != targetSize) {
            throw new IllegalArgumentException("delta produced " + outPos + " bytes, expected " + targetSize);
        }
        return target;
    }

    private static void writeCopyInstructions(ByteArrayOutputStream out, int offset, int length) {
        while (length > 0) {
            int chunk = Math.min(length, MAX_COPY_LENGTH);
            writeCopyInstruction(out, offset, chunk == MAX_COPY_LENGTH ? 0 : chunk);
            offset += chunk;
            length -= chunk;
        }
    }

    private static void writeCopyInstruction(ByteArrayOutputStream out, int offset, int length) {
        int flags = 0x80;
        java.util.List<Integer> offsetBytes = new java.util.ArrayList<>();
        java.util.List<Integer> lengthBytes = new java.util.ArrayList<>();
        int o = offset;
        for (int i = 0; i < 4; i++) {
            int b = o & 0xFF;
            if (b != 0) {
                flags |= (1 << i);
            }
            offsetBytes.add(b);
            o >>>= 8;
        }
        int l = length;
        for (int i = 0; i < 3; i++) {
            int b = l & 0xFF;
            if (b != 0) {
                flags |= (1 << (4 + i));
            }
            lengthBytes.add(b);
            l >>>= 8;
        }
        out.write(flags);
        for (int i = 0; i < 4; i++) {
            if ((flags & (1 << i)) != 0) {
                out.write(offsetBytes.get(i));
            }
        }
        for (int i = 0; i < 3; i++) {
            if ((flags & (1 << (4 + i))) != 0) {
                out.write(lengthBytes.get(i));
            }
        }
    }

    private static void writeInsertInstructions(ByteArrayOutputStream out, byte[] target, int start, int length) {
        int pos = start;
        int remaining = length;
        while (remaining > 0) {
            int chunk = Math.min(remaining, 127);
            out.write(chunk);
            out.write(target, pos, chunk);
            pos += chunk;
            remaining -= chunk;
        }
    }

    private static void writeVarInt(ByteArrayOutputStream out, long value) {
        while (true) {
            int b = (int) (value & 0x7F);
            value >>>= 7;
            if (value != 0) {
                out.write(b | 0x80);
            } else {
                out.write(b);
                return;
            }
        }
    }

    private static long readVarInt(byte[] data, int[] pos) {
        long value = 0;
        int shift = 0;
        while (true) {
            int b = data[pos[0]++] & 0xFF;
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
    }

    private static List<Byte> asList(byte[] array) {
        return new AbstractList<>() {
            @Override
            public Byte get(int index) {
                return array[index];
            }

            @Override
            public int size() {
                return array.length;
            }
        };
    }
}
