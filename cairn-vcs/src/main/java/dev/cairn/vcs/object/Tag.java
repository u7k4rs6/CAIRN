package dev.cairn.vcs.object;

import java.nio.charset.StandardCharsets;

/** An annotated tag: a named, signed-in-spirit pointer to another object, usually a commit. */
public final class Tag extends AbstractGitObject {

    private final ObjectId target;
    private final ObjectKind targetKind;
    private final String name;
    private final PersonIdent tagger;
    private final String message;

    public Tag(ObjectId target, ObjectKind targetKind, String name, PersonIdent tagger, String message) {
        this.target = target;
        this.targetKind = targetKind;
        this.name = name;
        this.tagger = tagger;
        this.message = message;
    }

    public ObjectId target() {
        return target;
    }

    public ObjectKind targetKind() {
        return targetKind;
    }

    public String name() {
        return name;
    }

    public PersonIdent tagger() {
        return tagger;
    }

    public String message() {
        return message;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.TAG;
    }

    @Override
    protected byte[] serializeBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("object ").append(target.hex()).append('\n');
        sb.append("type ").append(targetKind.label()).append('\n');
        sb.append("tag ").append(name).append('\n');
        sb.append("tagger ").append(tagger.format()).append('\n');
        sb.append('\n').append(message);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Tag parse(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        String[] lines = text.split("\n", -1);
        ObjectId target = null;
        ObjectKind targetKind = null;
        String name = null;
        PersonIdent tagger = null;
        int i = 0;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                i++;
                break;
            }
            if (line.startsWith("object ")) {
                target = ObjectId.fromHex(line.substring(7).trim());
            } else if (line.startsWith("type ")) {
                targetKind = ObjectKind.fromLabel(line.substring(5).trim());
            } else if (line.startsWith("tag ")) {
                name = line.substring(4).trim();
            } else if (line.startsWith("tagger ")) {
                tagger = PersonIdent.parse(line.substring(7));
            }
        }
        String message = String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length));
        return new Tag(target, targetKind, name, tagger, message);
    }
}
