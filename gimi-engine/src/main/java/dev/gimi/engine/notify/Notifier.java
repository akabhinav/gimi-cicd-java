package dev.gimi.engine.notify;

import java.util.Map;

/** Sends notifications about pipeline events. */
public interface Notifier {

    /** Send a notification message with optional metadata. */
    void send(String message, Map<String, String> metadata);
}
