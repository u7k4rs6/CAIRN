package dev.cairn.transfer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Git's pkt-line framing: each line is a 4-hex-digit length prefix (counting the
 * prefix itself) followed by that many bytes of payload. A length of {@code 0000}
 * is the flush-pkt, the section terminator every part of the smart protocol uses
 * (architecture doc, section 7.2 and 8).
 */
public final class PktLine {

    /** The flush-pkt: an empty {@link Optional}, distinct from a zero-length data line (which cannot occur; the minimum non-flush length is 4). */
    public static final Optional<byte[]> FLUSH = Optional.empty();

    private PktLine() {
    }

    public static byte[] encode(byte[] payload) {
        int length = payload.length + 4;
        String header = String.format("%04x", length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        out.writeBytes(header.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(payload);
        return out.toByteArray();
    }

    public static byte[] encode(String line) {
        return encode(line.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] flush() {
        return "0000".getBytes(StandardCharsets.US_ASCII);
    }

    /** Reads one pkt-line's payload, or {@link #FLUSH} at a flush-pkt, or {@code null} at end of stream. */
    public static Optional<byte[]> read(InputStream in) {
        try {
            byte[] lengthHex = in.readNBytes(4);
            if (lengthHex.length == 0) {
                return null;
            }
            if (lengthHex.length < 4) {
                throw new IllegalArgumentException("truncated pkt-line length header");
            }
            int length = Integer.parseInt(new String(lengthHex, StandardCharsets.US_ASCII), 16);
            if (length == 0) {
                return FLUSH;
            }
            byte[] payload = in.readNBytes(length - 4);
            if (payload.length != length - 4) {
                throw new IllegalArgumentException("truncated pkt-line payload");
            }
            return Optional.of(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String readLine(InputStream in) {
        Optional<byte[]> payload = read(in);
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        String line = new String(payload.get(), StandardCharsets.UTF_8);
        return line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    }
}
