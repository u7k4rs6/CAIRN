package dev.cairn.api.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A second, independent {@link ActivityListener} alongside {@link InMemoryActivityFeed},
 * proving the fan-out is real: both react to the same events without knowing about
 * each other. Stands in for what a real webhook-delivery listener would do on
 * receiving the same event (see DECISIONS.md, M9, for why actual outbound HTTP
 * delivery is left paper-only).
 */
@Component
public class LoggingActivityListener implements ActivityListener {

    private static final Logger log = LoggerFactory.getLogger("dev.cairn.api.activity.feed");

    @Override
    public void onActivity(ActivityEvent event) {
        log.info("[{}] {}: {}", event.repoFullName(), event.type(), event.summary());
    }
}
