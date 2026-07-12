package dev.cairn.vcs.pack;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaCodecTest {

    @Test
    void encodesAndDecodesASmallSimilarPair() {
        byte[] base = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
        byte[] target = "The quick brown fox leaps over the lazy dog and runs".getBytes(StandardCharsets.UTF_8);

        byte[] delta = DeltaCodec.encode(base, target);
        byte[] reconstructed = DeltaCodec.decode(base, delta);

        assertThat(reconstructed).isEqualTo(target);
    }

    @Test
    void identicalContentDeltaIsTiny() {
        byte[] content = "identical content, repeated ".repeat(50).getBytes(StandardCharsets.UTF_8);
        byte[] delta = DeltaCodec.encode(content, content);
        assertThat(delta.length).isLessThan(content.length / 4);
        assertThat(DeltaCodec.decode(content, delta)).isEqualTo(content);
    }

    @Test
    void completelyDifferentContentStillRoundTrips() {
        byte[] base = "aaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8);
        byte[] target = "zzzzzzzzzzzzzzzzzzzzzzzz".getBytes(StandardCharsets.UTF_8);
        byte[] delta = DeltaCodec.encode(base, target);
        assertThat(DeltaCodec.decode(base, delta)).isEqualTo(target);
    }

    @Test
    void emptyBaseAndTarget() {
        byte[] delta = DeltaCodec.encode(new byte[0], new byte[0]);
        assertThat(DeltaCodec.decode(new byte[0], delta)).isEqualTo(new byte[0]);
    }

    @Test
    void emptyBaseWithNonEmptyTarget() {
        byte[] target = "brand new content".getBytes(StandardCharsets.UTF_8);
        byte[] delta = DeltaCodec.encode(new byte[0], target);
        assertThat(DeltaCodec.decode(new byte[0], delta)).isEqualTo(target);
    }

    @Test
    void nonEmptyBaseWithEmptyTarget() {
        byte[] base = "will be entirely deleted".getBytes(StandardCharsets.UTF_8);
        byte[] delta = DeltaCodec.encode(base, new byte[0]);
        assertThat(DeltaCodec.decode(base, delta)).isEqualTo(new byte[0]);
    }

    @Test
    void largeSimilarBuffersSpanningMultipleCopyInstructions() {
        // Bigger than the 64KiB per-instruction copy cap, to exercise the split path.
        byte[] base = new byte[200_000];
        new Random(1).nextBytes(base);
        byte[] target = base.clone();
        // Change a handful of scattered bytes so most of the buffer is still one long common run.
        target[500] = (byte) (target[500] + 1);
        target[150_000] = (byte) (target[150_000] + 1);

        byte[] delta = DeltaCodec.encode(base, target);
        byte[] reconstructed = DeltaCodec.decode(base, delta);

        assertThat(reconstructed).isEqualTo(target);
    }

    @Test
    void randomizedRoundTrip() {
        Random random = new Random(123);
        for (int trial = 0; trial < 100; trial++) {
            byte[] base = randomBytes(random, random.nextInt(200));
            byte[] target = randomBytes(random, random.nextInt(200));
            byte[] delta = DeltaCodec.encode(base, target);
            assertThat(DeltaCodec.decode(base, delta)).isEqualTo(target);
        }
    }

    private byte[] randomBytes(Random random, int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        // Bias toward a small alphabet so base/target actually share structure sometimes.
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (bytes[i] % 4);
        }
        return bytes;
    }
}
