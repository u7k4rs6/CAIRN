package dev.cairn.vcs.merge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileMergeTest {

    private final FileMerge merge = new FileMerge();

    private static List<String> lines(String... lines) {
        return List.of(lines);
    }

    @Test
    void nonOverlappingChangesMergeCleanly() {
        List<String> base = lines("a", "b", "c", "d", "e");
        List<String> ours = lines("A", "b", "c", "d", "e");
        List<String> theirs = lines("a", "b", "c", "d", "E");

        var result = merge.merge("file.txt", base, ours, theirs);

        assertThat(result.isClean()).isTrue();
        assertThat(result.lines()).containsExactly("A", "b", "c", "d", "E");
    }

    @Test
    void overlappingChangesToTheSameLineConflict() {
        List<String> base = lines("a", "b", "c");
        List<String> ours = lines("a", "OURS", "c");
        List<String> theirs = lines("a", "THEIRS", "c");

        var result = merge.merge("file.txt", base, ours, theirs);

        assertThat(result.isClean()).isFalse();
        assertThat(result.conflicts()).hasSize(1);
        Conflict conflict = result.conflicts().get(0);
        assertThat(conflict.base()).containsExactly("b");
        assertThat(conflict.ours()).containsExactly("OURS");
        assertThat(conflict.theirs()).containsExactly("THEIRS");
    }

    @Test
    void identicalChangesOnBothSidesDoNotConflict() {
        List<String> base = lines("a", "b", "c");
        List<String> ours = lines("a", "SAME", "c");
        List<String> theirs = lines("a", "SAME", "c");

        var result = merge.merge("file.txt", base, ours, theirs);

        assertThat(result.isClean()).isTrue();
        assertThat(result.lines()).containsExactly("a", "SAME", "c");
    }

    @Test
    void onlyOneSideChangedTakesThatSide() {
        List<String> base = lines("a", "b", "c");
        List<String> ours = lines("a", "b", "c");
        List<String> theirs = lines("a", "CHANGED", "c");

        var result = merge.merge("file.txt", base, ours, theirs);

        assertThat(result.isClean()).isTrue();
        assertThat(result.lines()).containsExactly("a", "CHANGED", "c");
    }

    @Test
    void insertionsAtDifferentPointsBothApply() {
        List<String> base = lines("a", "b", "c");
        List<String> ours = lines("OURS_HEAD", "a", "b", "c");
        List<String> theirs = lines("a", "b", "c", "THEIRS_TAIL");

        var result = merge.merge("file.txt", base, ours, theirs);

        assertThat(result.isClean()).isTrue();
        assertThat(result.lines()).containsExactly("OURS_HEAD", "a", "b", "c", "THEIRS_TAIL");
    }

    @Test
    void deleteVersusModifyConflicts() {
        List<String> base = lines("a", "b", "c");
        List<String> ours = lines("a", "c");
        List<String> theirs = lines("a", "MODIFIED", "c");

        var result = merge.merge("file.txt", base, ours, theirs);

        assertThat(result.isClean()).isFalse();
        assertThat(result.conflicts()).hasSize(1);
    }
}
