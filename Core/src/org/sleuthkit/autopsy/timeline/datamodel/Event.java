/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.datamodel;

import java.util.Set;
import java.util.SortedSet;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;

/**
 *
 */
public interface Event {

    public String getDescription();

    public DescriptionLoD getDescriptionLoD();

    Set<Long> getEventIDs();

    Set<Long> getEventIDsWithHashHits();

    Set<Long> getEventIDsWithTags();

    EventType getEventType();

    long getEndMillis();

    long getStartMillis();

    default int getCount() {
        return getEventIDs().size();
    }


    SortedSet<EventCluster> getClusters();
}
