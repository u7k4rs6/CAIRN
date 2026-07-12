package dev.cairn.api.activity;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One concrete {@link ActivityListener}: keeps the most recent events per repo in
 * memory, newest first, for the activity feed endpoint. A real deployment would
 * persist this (a table, or a time-series store); an in-memory bounded deque is
 * the honest match for this project's H2-in-memory, single-instance scope, and
 * keeps the feed feature real rather than a paper description like the other M9
 * stretch items.
 */
@Component
public class InMemoryActivityFeed implements ActivityListener {

    private static final int MAX_EVENTS_PER_REPO = 200;

    private final Map<Long, Deque<ActivityEvent>> byRepo = new HashMap<>();

    @Override
    public synchronized void onActivity(ActivityEvent event) {
        Deque<ActivityEvent> events = byRepo.computeIfAbsent(event.repoId(), id -> new ArrayDeque<>());
        events.addFirst(event);
        while (events.size() > MAX_EVENTS_PER_REPO) {
            events.removeLast();
        }
    }

    public synchronized List<ActivityEvent> recent(Long repoId) {
        return List.copyOf(byRepo.getOrDefault(repoId, new ArrayDeque<>()));
    }
}
