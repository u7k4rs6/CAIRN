package dev.cairn.api.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The Observer subject: fans one {@link ActivityEvent} out to every registered
 * {@link ActivityListener}. Spring collects every {@code ActivityListener} bean into
 * the constructor's {@code List}, so adding a new observer (a real webhook-delivery
 * listener, say) never touches this class or any publisher call site.
 *
 * <p>A listener that throws is logged and skipped, not propagated: the thing that
 * happened (a push, a merge) already succeeded, and a broken notification is a
 * degraded feed, not a reason to fail the action that triggered it.
 */
@Component
public class ActivityPublisher {

    private static final Logger log = LoggerFactory.getLogger(ActivityPublisher.class);

    private final List<ActivityListener> listeners;

    public ActivityPublisher(List<ActivityListener> listeners) {
        this.listeners = listeners;
    }

    public void publish(ActivityEvent event) {
        for (ActivityListener listener : listeners) {
            try {
                listener.onActivity(event);
            } catch (RuntimeException e) {
                log.warn("activity listener {} failed for event {}", listener.getClass().getSimpleName(), event, e);
            }
        }
    }
}
