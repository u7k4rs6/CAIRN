package dev.cairn.api.activity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Observer pattern's own contract, independent of any Spring wiring: every
 * registered listener is notified of the same event, in registration order, and a
 * listener that throws does not stop the others from being notified.
 */
class ActivityPublisherTest {

    private static ActivityEvent event() {
        return new ActivityEvent(1L, "acme/demo", "ada", "pr_merged", "merged a thing", 1_700_000_000L);
    }

    @Test
    void everyRegisteredListenerReceivesTheSameEvent() {
        List<ActivityEvent> seenByFirst = new ArrayList<>();
        List<ActivityEvent> seenBySecond = new ArrayList<>();
        ActivityPublisher publisher = new ActivityPublisher(List.of(seenByFirst::add, seenBySecond::add));

        ActivityEvent event = event();
        publisher.publish(event);

        assertThat(seenByFirst).containsExactly(event);
        assertThat(seenBySecond).containsExactly(event);
    }

    @Test
    void aThrowingListenerDoesNotPreventOthersFromBeingNotified() {
        List<ActivityEvent> seen = new ArrayList<>();
        ActivityListener broken = e -> {
            throw new RuntimeException("simulated webhook delivery failure");
        };
        ActivityPublisher publisher = new ActivityPublisher(List.of(broken, seen::add));

        publisher.publish(event());

        assertThat(seen).hasSize(1);
    }

    @Test
    void publishingWithNoListenersIsANoOp() {
        ActivityPublisher publisher = new ActivityPublisher(List.of());
        publisher.publish(event());
    }
}
