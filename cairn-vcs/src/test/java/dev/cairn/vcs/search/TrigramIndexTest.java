package dev.cairn.vcs.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrigramIndexTest {

    @Test
    void findsAnExactSubstringMatch() {
        TrigramIndex index = new TrigramIndex();
        index.add("App.java", "class App {\n    void run() {}\n}\n");

        List<TrigramIndex.FileMatch> results = index.search("void run");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).path()).isEqualTo("App.java");
        assertThat(results.get(0).lines()).extracting(TrigramIndex.LineMatch::lineNumber).containsExactly(2);
    }

    @Test
    void queryShorterThanThreeCharsAlwaysReturnsEmpty() {
        TrigramIndex index = new TrigramIndex();
        index.add("a.txt", "ab");

        assertThat(index.search("ab")).isEmpty();
        assertThat(index.search("")).isEmpty();
    }

    @Test
    void onlyDocumentsContainingTheSubstringAreReturned() {
        TrigramIndex index = new TrigramIndex();
        index.add("has-it.txt", "the quick brown fox");
        index.add("does-not.txt", "a totally unrelated document");

        List<TrigramIndex.FileMatch> results = index.search("brown fox");
        assertThat(results).extracting(TrigramIndex.FileMatch::path).containsExactly("has-it.txt");
    }

    @Test
    void matchingIsCaseInsensitive() {
        TrigramIndex index = new TrigramIndex();
        index.add("a.txt", "Hello World");

        assertThat(index.search("hello world")).hasSize(1);
        assertThat(index.search("HELLO WORLD")).hasSize(1);
    }

    @Test
    void trigramFalsePositivesAreRejectedByCandidateVerification() {
        // Every trigram of "abcd" ("abc", "bcd") appears in this content, but the
        // contiguous substring "abcd" itself does not: proves the index does not
        // return a match on trigram membership alone.
        TrigramIndex index = new TrigramIndex();
        index.add("decoy.txt", "xxabcxx xxbcdxx");

        assertThat(index.search("abcd")).isEmpty();
    }

    @Test
    void reportsEveryMatchingLineInAFileWithCorrectOneBasedLineNumbers() {
        TrigramIndex index = new TrigramIndex();
        index.add("multi.txt", "needle here\nno match\nanother needle\n");

        List<TrigramIndex.FileMatch> results = index.search("needle");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).lines()).extracting(TrigramIndex.LineMatch::lineNumber).containsExactly(1, 3);
    }

    @Test
    void documentCountReflectsWhatWasIndexed() {
        TrigramIndex index = new TrigramIndex();
        assertThat(index.documentCount()).isZero();
        index.add("a.txt", "one");
        index.add("b.txt", "two");
        assertThat(index.documentCount()).isEqualTo(2);
    }
}
