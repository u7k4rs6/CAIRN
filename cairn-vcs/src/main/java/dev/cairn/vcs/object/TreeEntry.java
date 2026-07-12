package dev.cairn.vcs.object;

/** One named entry in a {@link Tree}: a mode, a name, and the id of the blob or subtree it points to. */
public record TreeEntry(FileMode mode, String name, ObjectId id) implements Comparable<TreeEntry> {

    public boolean isDirectory() {
        return mode == FileMode.DIRECTORY;
    }

    /**
     * Git's own sort order: directory names sort as if suffixed with '/', so a file
     * named "foo" and a directory named "foo" order the same as "foo" vs "foo/" would.
     * Getting this wrong makes two trees with identical entries hash differently.
     */
    @Override
    public int compareTo(TreeEntry o) {
        String a = name + (isDirectory() ? "/" : "");
        String b = o.name + (o.isDirectory() ? "/" : "");
        return a.compareTo(b);
    }
}
