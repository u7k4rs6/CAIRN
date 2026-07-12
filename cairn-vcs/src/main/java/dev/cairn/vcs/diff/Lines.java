package dev.cairn.vcs.diff;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits blob content into lines the way diffing tools conventionally do: each line
 * keeps its trailing newline as part of its content so a file missing a final newline
 * diffs distinctly from one that has it, and rejoining the lines reproduces the
 * original bytes exactly.
 */
public final class Lines {

    private Lines() {
    }

    public static List<String> of(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        return lines;
    }

    public static byte[] join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        lines.forEach(sb::append);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
