/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.datamodel;

import java.util.Set;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

/**
 *
 */
public interface EventBundle {

    String getDescription();

    DescriptionLOD getDescriptionLOD();

    long getEndMillis();

    Set<Long> getEventIDs();

    Set<Long> getEventIDsWithHashHits();

    Set<Long> getEventIDsWithTags();

    long getStartMillis();

    EventType getType();
    
}
