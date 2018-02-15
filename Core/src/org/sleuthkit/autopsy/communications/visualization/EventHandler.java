package org.sleuthkit.autopsy.communications.visualization;

/**
 *
 */
public interface EventHandler<T> {

    void handle(T event);
}
