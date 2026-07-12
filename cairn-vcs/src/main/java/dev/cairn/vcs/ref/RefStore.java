package dev.cairn.vcs.ref;

import dev.cairn.vcs.object.ObjectId;

import java.util.Map;
import java.util.Optional;

/**
 * Maps ref names ({@code refs/heads/main}, {@code refs/tags/v1}) to the commit or tag
 * they point at. Read and write are O(1) per ref; listing is O(number of refs)
 * (architecture doc, section 4.3).
 */
public interface RefStore {

    Optional<ObjectId> resolve(String name);

    void update(String name, ObjectId newId);

    void delete(String name);

    /** All refs, name to id. O(refs). */
    Map<String, ObjectId> list();

    boolean exists(String name);
}
