package dev.cairn.vcs.diff;

import java.util.List;

/**
 * Strategy pattern over the choice of diff algorithm (architecture doc, section 4.5
 * and 6): swappable per request, so a caller that wants readability (histogram,
 * patience) instead of minimality (Myers) can ask for it without the merge or
 * rendering code caring which it got.
 */
public interface DiffStrategy<T> {

    List<Edit> diff(List<T> orig, List<T> rev);
}
