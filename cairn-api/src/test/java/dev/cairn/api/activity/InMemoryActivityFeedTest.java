package dev.cairn.api.activity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryActivityFeedTest {

    private static ActivityEvent event(long repoId, String summary, long time) {
        return new ActivityEvent(repoId, "acme/demo", "ada", "pr_merged", summary, time);
    }

    @Test
    void recentReturnsNewestFirst() {
        InMemoryActivityFeed feed = new InMemoryActivityFeed();
        feed.onActivity(event(1L, "first", 100));
        feed.onActivity(event(1L, "second", 200));

        assertThat(feed.recent(1L)).extracting(ActivityEvent::summary).containsExactly("second", "first");
    }

    @Test
    void eventsAreIsolatedPerRepo() {
        InMemoryActivityFeed feed = new InMemoryActivityFeed();
        feed.onActivity(event(1L, "repo one event", 100));
        feed.onActivity(event(2L, "repo two event", 100));

        assertThat(feed.recent(1L)).extracting(ActivityEvent::summary).containsExactly("repo one event");
        assertThat(feed.recent(2L)).extracting(ActivityEvent::summary).containsExactly("repo two event");
    }

    @Test
    void aRepoWithNoEventsYetReturnsAnEmptyListRatherThanNull() {
        InMemoryActivityFeed feed = new InMemoryActivityFeed();
        assertThat(feed.recent(99L)).isEmpty();
    }

    @Test
    void feedIsBoundedPerRepo() {
        InMemoryActivityFeed feed = new InMemoryActivityFeed();
        for (int i = 0; i < 250; i++) {
            feed.onActivity(event(1L, "event " + i, i));
        }

        assertThat(feed.recent(1L)).hasSize(200);
        assertThat(feed.recent(1L).get(0).summary()).isEqualTo("event 249");
    }
}
