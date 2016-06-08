/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.sleuthkit.autopsy.timeline;

import java.util.Set;
import org.joda.time.Interval;

/**
 *
 */
public class EventInTimeRange {

    private final Set<Long> eventIDs;
    private final Interval range;

    public EventInTimeRange(Set<Long> eventIDs, Interval range) {
        this.eventIDs = eventIDs;
        this.range = range;
    }

    public Set<Long> getEventIDs() {
        return eventIDs;
    }

    public Interval getRange() {
        return range;
    }

}
