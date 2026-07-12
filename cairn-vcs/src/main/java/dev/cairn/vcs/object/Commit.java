package dev.cairn.vcs.object;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A snapshot: a root tree, zero or more parents (zero for a root commit, two or more
 * for a merge), an author, a committer, and a message.
 *
 * <p>Pure text encoding, one header field per line, a blank line, then the message.
 * This is Git's canonical commit format; matching it lets a real {@code git} client
 * parse commits Cairn produces.
 *
 * <p><b>Extension headers (e.g. {@code gpgsig}).</b> Real commits from a real Git
 * client very often carry headers beyond tree/parent/author/committer: a GPG or SSH
 * commit signature is a common one, sitting between {@code committer} and the blank
 * line, itself spanning multiple lines. Cairn does not understand or verify these,
 * but preserves them verbatim, in original order, so round-tripping a signed commit
 * through Cairn does not change its id: dropping an unrecognized header would silently
 * change the serialized bytes and therefore the hash, which is exactly the kind of bug
 * that only surfaces against a real client using a feature this project doesn't
 * implement, rather than against its own test data.
 */
public final class Commit extends AbstractGitObject {

    private final ObjectId treeId;
    private final List<ObjectId> parents;
    private final PersonIdent author;
    private final PersonIdent committer;
    private final String message;
    private final List<String> extraHeaderLines;

    public Commit(ObjectId treeId, List<ObjectId> parents, PersonIdent author, PersonIdent committer, String message) {
        this(treeId, parents, author, committer, message, List.of());
    }

    public Commit(ObjectId treeId, List<ObjectId> parents, PersonIdent author, PersonIdent committer, String message,
                   List<String> extraHeaderLines) {
        this.treeId = treeId;
        this.parents = List.copyOf(parents);
        this.author = author;
        this.committer = committer;
        this.message = message;
        this.extraHeaderLines = List.copyOf(extraHeaderLines);
    }

    public ObjectId treeId() {
        return treeId;
    }

    public List<ObjectId> parents() {
        return parents;
    }

    public boolean isRoot() {
        return parents.isEmpty();
    }

    public boolean isMerge() {
        return parents.size() > 1;
    }

    public PersonIdent author() {
        return author;
    }

    public PersonIdent committer() {
        return committer;
    }

    public String message() {
        return message;
    }

    /** Unrecognized header lines (e.g. a {@code gpgsig} signature block) preserved verbatim between committer and message. */
    public List<String> extraHeaderLines() {
        return extraHeaderLines;
    }

    @Override
    public ObjectKind kind() {
        return ObjectKind.COMMIT;
    }

    @Override
    protected byte[] serializeBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("tree ").append(treeId.hex()).append('\n');
        for (ObjectId parent : parents) {
            sb.append("parent ").append(parent.hex()).append('\n');
        }
        sb.append("author ").append(author.format()).append('\n');
        sb.append("committer ").append(committer.format()).append('\n');
        for (String line : extraHeaderLines) {
            sb.append(line).append('\n');
        }
        sb.append('\n').append(message);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Commit parse(byte[] body) {
        String text = new String(body, StandardCharsets.UTF_8);
        String[] lines = text.split("\n", -1);
        ObjectId tree = null;
        java.util.List<ObjectId> parents = new java.util.ArrayList<>();
        PersonIdent author = null;
        PersonIdent committer = null;
        java.util.List<String> extra = new java.util.ArrayList<>();
        int i = 0;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                i++;
                break;
            }
            if (line.startsWith("tree ")) {
                tree = ObjectId.fromHex(line.substring(5).trim());
            } else if (line.startsWith("parent ")) {
                parents.add(ObjectId.fromHex(line.substring(7).trim()));
            } else if (line.startsWith("author ")) {
                author = PersonIdent.parse(line.substring(7));
            } else if (line.startsWith("committer ")) {
                committer = PersonIdent.parse(line.substring(10));
            } else {
                extra.add(line);
            }
        }
        String message = String.join("\n", java.util.Arrays.copyOfRange(lines, i, lines.length));
        return new Commit(tree, parents, author, committer, message, extra);
    }
}
