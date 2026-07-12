package dev.cairn.vcs.ref;

import dev.cairn.vcs.object.ObjectId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** One file per ref under a {@code refs/} root, each file holding the 40-hex-char id it points to. */
public final class FileRefStore implements RefStore {

    private final Path refsRoot;

    public FileRefStore(Path refsRoot) {
        this.refsRoot = refsRoot;
        try {
            Files.createDirectories(refsRoot);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path pathFor(String name) {
        validate(name);
        return refsRoot.resolve(name);
    }

    private void validate(String name) {
        if (name.contains("..") || name.startsWith("/") || name.isBlank()) {
            throw new IllegalArgumentException("invalid ref name: " + name);
        }
    }

    @Override
    public Optional<ObjectId> resolve(String name) {
        Path path = pathFor(name);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            String hex = Files.readString(path, StandardCharsets.UTF_8).trim();
            return Optional.of(ObjectId.fromHex(hex));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void update(String name, ObjectId newId) {
        Path path = pathFor(name);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, newId.hex() + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String name) {
        try {
            Files.deleteIfExists(pathFor(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Map<String, ObjectId> list() {
        Map<String, ObjectId> refs = new LinkedHashMap<>();
        if (!Files.exists(refsRoot)) {
            return refs;
        }
        try (Stream<Path> walk = Files.walk(refsRoot)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                String name = refsRoot.relativize(path).toString().replace('\\', '/');
                resolve(name).ifPresent(id -> refs.put(name, id));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return refs;
    }

    @Override
    public boolean exists(String name) {
        return Files.exists(pathFor(name));
    }
}
