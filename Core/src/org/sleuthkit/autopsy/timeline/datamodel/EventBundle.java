/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.datamodel;

import com.google.common.collect.Range;
import java.util.Optional;
import java.util.Set;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;

/**
 *
 */
public interface EventBundle {

    String getDescription();

    DescriptionLOD getDescriptionLOD();

    Set<Long> getEventIDs();

    Set<Long> getEventIDsWithHashHits();

    Set<Long> getEventIDsWithTags();

    EventType getEventType();

    long getEndMillis();

    long getStartMillis();

    Iterable<Range<Long>> getRanges();

    Optional<EventBundle> getParentBundle();
}
