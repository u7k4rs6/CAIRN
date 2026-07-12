package dev.cairn.vcs.repository;

import dev.cairn.vcs.object.FileMode;
import dev.cairn.vcs.object.ObjectId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The staging area: a flat map of repo-relative path to the blob id and mode {@code add}
 * last staged for it. Persisted as one line per entry so staging survives across process
 * runs, the way Git's index does.
 */
public final class Index {

    private final Path indexFile;
    private final Map<String, Entry> entries = new TreeMap<>();

    public record Entry(FileMode mode, ObjectId blobId) {
    }

    public Index(Path indexFile) {
        this.indexFile = indexFile;
        load();
    }

    public void stage(String path, FileMode mode, ObjectId blobId) {
        entries.put(path, new Entry(mode, blobId));
        persist();
    }

    public void remove(String path) {
        entries.remove(path);
        persist();
    }

    /** Replaces every staged entry wholesale, as a checkout to a different commit does. */
    public void replaceAll(Map<String, Entry> newEntries) {
        entries.clear();
        entries.putAll(newEntries);
        persist();
    }

    public Map<String, Entry> entries() {
        return new LinkedHashMap<>(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private void load() {
        if (!Files.exists(indexFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(indexFile, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(" ", 3);
                entries.put(parts[2], new Entry(FileMode.fromOctal(parts[0]), ObjectId.fromHex(parts[1])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void persist() {
        try {
            Files.createDirectories(indexFile.getParent());
            StringBuilder sb = new StringBuilder();
            entries.forEach((path, entry) ->
                    sb.append(entry.mode().octal()).append(' ').append(entry.blobId().hex()).append(' ').append(path).append('\n'));
            Files.writeString(indexFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
