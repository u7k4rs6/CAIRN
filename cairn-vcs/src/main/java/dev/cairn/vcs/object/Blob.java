package dev.cairn.vcs.object;

/** Raw file content, addressed by the hash of its bytes. Cairn stores no metadata in the blob itself. */
public final class Blob extends AbstractGitObject {

    private final byte[] content;

    public Blob(byte[] content) {
        this.content = content.clone();
    }

    public byte[] content() {
        return content.clone();
    }

    public int size() {
        return content.length;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.BLOB;
    }

    @Override
    protected byte[] serializeBody() {
        return content.clone();
    }
}
