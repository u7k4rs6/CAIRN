package dev.cairn.vcs.diff;

/**
 * One contiguous edit: a half-open range in the original sequence and a half-open
 * range in the revised sequence. For {@code EQUAL} both ranges are non-empty and the
 * same length; for {@code DELETE} the revised range is empty; for {@code INSERT} the
 * original range is empty.
 */
public record Edit(EditType type, int origStart, int origEnd, int revStart, int revEnd) {

    public int origLength() {
        return origEnd - origStart;
    }

    public int revLength() {
        return revEnd - revStart;
    }
}
