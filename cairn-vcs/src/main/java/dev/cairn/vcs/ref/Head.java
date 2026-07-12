package dev.cairn.vcs.ref;

import dev.cairn.vcs.object.ObjectId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The symbolic ref {@code HEAD}: either a pointer at a branch ({@code "ref: refs/heads/main"})
 * or, when detached, a raw commit id. Branch creation and switching act on this file plus
 * the underlying {@link RefStore}.
 */
public final class Head {

    private static final String BRANCH_PREFIX = "ref: ";

    private final Path headFile;
    private final RefStore refStore;

    public Head(Path headFile, RefStore refStore) {
        this.headFile = headFile;
        this.refStore = refStore;
    }

    public void pointToBranch(String branchRef) {
        write(BRANCH_PREFIX + branchRef);
    }

    public void detachTo(ObjectId id) {
        write(id.hex());
    }

    public boolean isDetached() {
        return currentBranch().isEmpty();
    }

    public Optional<String> currentBranch() {
        String content = read();
        if (content.startsWith(BRANCH_PREFIX)) {
            return Optional.of(content.substring(BRANCH_PREFIX.length()).trim());
        }
        return Optional.empty();
    }

    /** The commit HEAD currently resolves to, following the branch pointer if not detached. */
    public Optional<ObjectId> resolve() {
        String content = read();
        if (content.isEmpty()) {
            return Optional.empty();
        }
        if (content.startsWith(BRANCH_PREFIX)) {
            return refStore.resolve(content.substring(BRANCH_PREFIX.length()).trim());
        }
        return Optional.of(ObjectId.fromHex(content.trim()));
    }

    /** Moves HEAD's target forward to {@code newId}: the branch if attached, or HEAD itself if detached. */
    public void advance(ObjectId newId) {
        Optional<String> branch = currentBranch();
        if (branch.isPresent()) {
            refStore.update(branch.get(), newId);
        } else {
            detachTo(newId);
        }
    }

    private String read() {
        if (!Files.exists(headFile)) {
            return "";
        }
        try {
            return Files.readString(headFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(String content) {
        try {
            Files.createDirectories(headFile.getParent());
            Files.writeString(headFile, content + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
