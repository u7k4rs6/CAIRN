package dev.cairn.transfer;

import dev.cairn.vcs.object.ObjectId;
import dev.cairn.vcs.pack.PackWriter;
import dev.cairn.vcs.store.ObjectStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The server side of {@code git-upload-pack}: fetch and clone. Parses the client's
 * want/have negotiation, computes {@code reachable_from(wants) \ reachable_from(haves)}
 * (architecture doc, section 8, FR-XFER-1), and packs only that set.
 *
 * <p>No {@code multi_ack}/side-band capability is advertised (see
 * {@link TransferCapabilities}), so the response is the simplest valid form: a single
 * {@code NAK} line followed directly by the raw packfile bytes. This is a real
 * protocol simplification, not a shortcut around negotiation itself: the client still
 * sends its full have set up front and the server still computes and sends only the
 * missing objects; what is skipped is the optional multi-round early-stopping
 * handshake, which affects chattiness, not correctness or the size of what is sent.
 */
public final class UploadPackHandler {

    public record Request(List<ObjectId> wants, List<ObjectId> haves) {
    }

    private UploadPackHandler() {
    }

    public static Request parseRequest(InputStream in) {
        List<ObjectId> wants = new ArrayList<>();
        List<ObjectId> haves = new ArrayList<>();

        while (true) {
            var payload = PktLine.read(in);
            if (payload == null || payload.isEmpty()) {
                break;
            }
            String line = stripNewline(new String(payload.get(), StandardCharsets.UTF_8));
            if (line.startsWith("want ")) {
                wants.add(ObjectId.fromHex(firstToken(line.substring(5))));
            }
        }

        while (true) {
            var payload = PktLine.read(in);
            if (payload == null) {
                break;
            }
            if (payload.isEmpty()) {
                continue;
            }
            String line = stripNewline(new String(payload.get(), StandardCharsets.UTF_8));
            if (line.equals("done")) {
                break;
            }
            if (line.startsWith("have ")) {
                haves.add(ObjectId.fromHex(firstToken(line.substring(5))));
            }
        }

        return new Request(wants, haves);
    }

    public static byte[] buildResponse(ObjectStore store, Request request) {
        LinkedHashSet<ObjectId> wantClosure = ObjectClosure.from(store, request.wants());
        List<ObjectId> validHaves = request.haves().stream().filter(store::has).toList();
        LinkedHashSet<ObjectId> haveClosure = ObjectClosure.from(store, validHaves);

        List<ObjectId> missing = wantClosure.stream().filter(id -> !haveClosure.contains(id)).toList();
        byte[] pack = new PackWriter(store).writePack(missing);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PktLine.encode("NAK\n"));
        out.writeBytes(pack);
        return out.toByteArray();
    }

    private static String stripNewline(String line) {
        return line.endsWith("\n") ? line.substring(0, line.length() - 1) : line;
    }

    private static String firstToken(String s) {
        int space = s.indexOf(' ');
        return space < 0 ? s.trim() : s.substring(0, space);
    }
}
