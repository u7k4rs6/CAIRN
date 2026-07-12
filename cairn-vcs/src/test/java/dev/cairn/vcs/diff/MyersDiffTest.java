package dev.cairn.vcs.diff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The correctness bar for a diff algorithm is: applying the edit script to the
 * original sequence must reconstruct the revised sequence exactly, and the script
 * must be minimal (matching a brute-force LCS oracle's edit distance). This suite
 * checks both, first with hand-picked cases and then across many random pairs, since
 * the linear-space middle-snake search is exactly the kind of code where an off-by-one
 * error produces a plausible-looking but wrong result on most inputs.
 */
class MyersDiffTest {

    private final MyersDiff<Character> diff = new MyersDiff<>();

    @Test
    void identicalSequencesProduceNoEdits() {
        List<Character> a = chars("abc");
        assertThat(diff.diff(a, chars("abc"))).allMatch(e -> e.type() == EditType.EQUAL);
        assertApply(a, chars("abc"));
    }

    @Test
    void totallyDifferentSequences() {
        assertApplyAndMinimal("abc", "xyz");
    }

    @Test
    void pureInsertion() {
        assertApplyAndMinimal("ac", "abc");
    }

    @Test
    void pureDeletion() {
        assertApplyAndMinimal("abc", "ac");
    }

    @Test
    void classicMyersExample() {
        // The example from Myers' 1986 paper: A = ABCABBA, B = CBABAC.
        assertApplyAndMinimal("ABCABBA", "CBABAC");
    }

    @Test
    void emptyOriginal() {
        assertApplyAndMinimal("", "abc");
    }

    @Test
    void emptyRevised() {
        assertApplyAndMinimal("abc", "");
    }

    @Test
    void bothEmpty() {
        assertThat(diff.diff(List.of(), List.of())).isEmpty();
    }

    @Test
    void commonPrefixAndSuffixAroundAChange() {
        assertApplyAndMinimal("prefixOLDsuffix", "prefixNEWsuffix");
    }

    @Test
    void singleCharacterSequences() {
        assertApplyAndMinimal("a", "b");
        assertApplyAndMinimal("a", "a");
    }

    @Test
    void randomizedAgainstBruteForceOracle() {
        Random random = new Random(42);
        String alphabet = "ab";
        for (int trial = 0; trial < 500; trial++) {
            int lenA = random.nextInt(9);
            int lenB = random.nextInt(9);
            String a = randomString(random, alphabet, lenA);
            String b = randomString(random, alphabet, lenB);
            assertApplyAndMinimal(a, b);
        }
    }

    @Test
    void randomizedWithLargerAlphabet() {
        Random random = new Random(7);
        String alphabet = "abcdef";
        for (int trial = 0; trial < 300; trial++) {
            String a = randomString(random, alphabet, random.nextInt(15));
            String b = randomString(random, alphabet, random.nextInt(15));
            assertApplyAndMinimal(a, b);
        }
    }

    @Test
    void largerSequencesDoNotOverflowTheRecursion() {
        Random random = new Random(99);
        for (int trial = 0; trial < 20; trial++) {
            String a = randomString(random, "abcdefghij", 300);
            String b = randomString(random, "abcdefghij", 300);
            assertApplyAndMinimal(a, b);
        }
    }

    private String randomString(Random random, String alphabet, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private void assertApplyAndMinimal(String a, String b) {
        List<Character> orig = chars(a);
        List<Character> rev = chars(b);
        List<Edit> edits = diff.diff(orig, rev);
        assertApply(orig, rev, edits);

        int editDistance = edits.stream()
                .filter(e -> e.type() != EditType.EQUAL)
                .mapToInt(e -> e.origLength() + e.revLength())
                .sum();
        int expected = bruteForceEditDistance(a, b);
        assertThat(editDistance)
                .as("edit distance for a=%s b=%s should be minimal", a, b)
                .isEqualTo(expected);
    }

    private void assertApply(List<Character> orig, List<Character> rev) {
        assertApply(orig, rev, diff.diff(orig, rev));
    }

    private void assertApply(List<Character> orig, List<Character> rev, List<Edit> edits) {
        List<Character> reconstructed = new ArrayList<>();
        for (Edit edit : edits) {
            switch (edit.type()) {
                case EQUAL, INSERT -> reconstructed.addAll(rev.subList(edit.revStart(), edit.revEnd()));
                case DELETE -> { /* contributes nothing to the revised sequence */ }
            }
        }
        assertThat(reconstructed).isEqualTo(rev);
    }

    /** O(N*M) dynamic-programming edit distance (insertions + deletions only, no substitution), used purely as a test oracle. */
    private int bruteForceEditDistance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                lcs[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? lcs[i - 1][j - 1] + 1
                        : Math.max(lcs[i - 1][j], lcs[i][j - 1]);
            }
        }
        int lcsLen = lcs[n][m];
        return (n - lcsLen) + (m - lcsLen);
    }

    private List<Character> chars(String s) {
        List<Character> list = new ArrayList<>();
        for (char c : s.toCharArray()) {
            list.add(c);
        }
        return list;
    }
}
