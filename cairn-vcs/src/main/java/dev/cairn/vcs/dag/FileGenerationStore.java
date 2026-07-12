package dev.cairn.vcs.dag;

import dev.cairn.vcs.object.ObjectId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * A flat {@code "<commit hex> <generation>"} file, loaded fully into memory on open.
 *
 * <p><b>Complexity and tradeoff.</b> Load is O(commits with a known generation);
 * every lookup and write after that is O(1) against the in-memory map, with a write
 * appending one line (persist rewrites the file, O(entries), which is the simple and
 * always-correct choice given how infrequently generations are (re)computed relative
 * to how often they're read). Git's own commit-graph file uses a binary, fan-out
 * indexed format for O(1) random access without a full load; a flat text file was
 * chosen here for simplicity given the scale this project targets, at the cost of an
 * O(n) startup load that a real commit-graph avoids.
 */
public final class FileGenerationStore implements GenerationStore {

    private final Path file;
    private final Map<ObjectId, Integer> generations = new HashMap<>();

    public FileGenerationStore(Path file) {
        this.file = file;
        load();
    }

    @Override
    public OptionalInt get(ObjectId commitId) {
        Integer gen = generations.get(commitId);
        return gen == null ? OptionalInt.empty() : OptionalInt.of(gen);
    }

    @Override
    public void put(ObjectId commitId, int generation) {
        generations.put(commitId, generation);
        persist();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(" ", 2);
                generations.put(ObjectId.fromHex(parts[0]), Integer.parseInt(parts[1]));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            generations.forEach((id, gen) -> sb.append(id.hex()).append(' ').append(gen).append('\n'));
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
