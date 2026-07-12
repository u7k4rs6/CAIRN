package dev.cairn.vcs.object;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared framing for every object kind: a header of {@code "<kind> <bodyLength>\0"}
 * followed by the kind-specific body. Subclasses only produce the body; this class
 * owns the header so every object is framed identically.
 */
abstract class AbstractGitObject implements GitObject {

    protected abstract byte[] serializeBody();

    @Override
    public final byte[] serialize() {
        byte[] body = serializeBody();
        byte[] header = (kind().label() + " " + body.length + "\0").getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(header.length + body.length);
        out.writeBytes(header);
        out.writeBytes(body);
        return out.toByteArray();
    }
}
