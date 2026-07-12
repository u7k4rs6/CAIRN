package dev.cairn.transfer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PktLineTest {

    @Test
    void encodesLengthPrefixCorrectly() {
        byte[] encoded = PktLine.encode("hello\n");
        // 4 (header) + 6 (payload) = 10 = 0x000a
        assertThat(new String(encoded, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("000a");
    }

    @Test
    void roundTripsThroughReadAndEncode() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PktLine.encode("first line\n"));
        out.writeBytes(PktLine.encode("second line\n"));
        out.writeBytes(PktLine.flush());

        var in = new ByteArrayInputStream(out.toByteArray());
        assertThat(PktLine.readLine(in)).isEqualTo("first line");
        assertThat(PktLine.readLine(in)).isEqualTo("second line");
        assertThat(PktLine.read(in)).isEqualTo(PktLine.FLUSH);
    }

    @Test
    void matchesGitsKnownEncodingOfAFlushPkt() {
        assertThat(new String(PktLine.flush(), java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("0000");
    }
}
