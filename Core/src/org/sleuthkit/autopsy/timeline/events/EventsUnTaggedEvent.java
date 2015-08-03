/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.events;

import java.util.Set;

/**
 *
 */
public  class EventsUnTaggedEvent {

    private final Set<Long> eventIDs;

    public Set<Long> getEventIDs() {
        return eventIDs;
    }

    public EventsUnTaggedEvent(Set<Long> eventIDs) {
        this.eventIDs = eventIDs;
    }

}
