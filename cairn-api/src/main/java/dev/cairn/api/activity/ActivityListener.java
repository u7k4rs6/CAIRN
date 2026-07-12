package dev.cairn.api.activity;

/**
 * Observer pattern (architecture doc, section 4: "Observer - webhooks, notifications,
 * activity-feed fan-out, to decouple 'something happened' from the reactions").
 * Any number of listeners can react to the same {@link ActivityEvent} without the
 * publisher or each other knowing the others exist; a real webhook-delivery listener
 * is a documented paper-only extension of this same interface (see DECISIONS.md, M9).
 */
public interface ActivityListener {

    void onActivity(ActivityEvent event);
}
