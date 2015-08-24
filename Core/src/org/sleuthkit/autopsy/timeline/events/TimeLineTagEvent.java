/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.events;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 *
 */
public class TimeLineTagEvent {

    private final Set<Long> updatedEventIDs;
    private final boolean tagged;

    public ImmutableSet<Long> getUpdatedEventIDs() {
        return ImmutableSet.copyOf(updatedEventIDs);
    }

    public boolean isTagged() {
        return tagged;
    }

    TimeLineTagEvent(Set<Long> updatedEventIDs, boolean tagged) {
        this.updatedEventIDs = updatedEventIDs;
        this.tagged = tagged;
    }
}
