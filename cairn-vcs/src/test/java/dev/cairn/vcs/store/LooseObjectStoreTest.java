package dev.cairn.vcs.store;

import dev.cairn.vcs.object.Blob;
import dev.cairn.vcs.object.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LooseObjectStoreTest {

    @Test
    void putThenGetRoundTripsContent(@TempDir Path dir) {
        LooseObjectStore store = new LooseObjectStore(dir);
        Blob blob = new Blob("round trip me".getBytes());
        ObjectId id = store.put(blob);

        assertThat(store.has(id)).isTrue();
        assertThat(store.get(id)).isPresent();
        assertThat(store.get(id).get()).isInstanceOf(Blob.class);
        assertThat(((Blob) store.get(id).get()).content()).isEqualTo(blob.content());
    }

    @Test
    void puttingIdenticalContentTwiceStoresOnce(@TempDir Path dir) {
        LooseObjectStore store = new LooseObjectStore(dir);
        ObjectId first = store.put(new Blob("same".getBytes()));
        ObjectId second = store.put(new Blob("same".getBytes()));
        assertThat(first).isEqualTo(second);
    }

    @Test
    void missingObjectIsAbsent(@TempDir Path dir) {
        LooseObjectStore store = new LooseObjectStore(dir);
        assertThat(store.has(ObjectId.hash("nope".getBytes()))).isFalse();
        assertThat(store.get(ObjectId.hash("nope".getBytes()))).isEmpty();
    }

    @Test
    void objectsAreShardedByFirstTwoHexChars(@TempDir Path dir) {
        LooseObjectStore store = new LooseObjectStore(dir);
        ObjectId id = store.put(new Blob("shard me".getBytes()));
        assertThat(dir.resolve(id.shard()).resolve(id.rest())).exists();
    }
}
