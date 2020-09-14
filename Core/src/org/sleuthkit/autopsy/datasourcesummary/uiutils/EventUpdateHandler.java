/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;

/**
 *
 * @author gregd
 */
public class EventUpdateHandler {       
    private final RefreshThrottler refreshThrottler = new RefreshThrottler(new RefreshThrottler.Refresher() {
        @Override
        public void refresh() {
            EventUpdateHandler.this.onRefresh();
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (Case.isCaseOpen()) {
                if (IngestManager.IngestModuleEvent.DATA_ADDED.toString().equals(eventType) && evt.getOldValue() instanceof ModuleDataEvent) {
                    ModuleDataEvent dataEvent = (ModuleDataEvent) evt.getOldValue();
                    return EventUpdateHandler.this.isRefreshRequired(dataEvent);
                } else if (IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString().equals(eventType) && evt.getOldValue() instanceof ModuleContentEvent) {
                    ModuleContentEvent contentEvent = (ModuleContentEvent) evt.getOldValue();
                    return EventUpdateHandler.this.isRefreshRequired(contentEvent);
                }
            }
            return false;
        }
    });
    
    private final PropertyChangeListener caseEventsListener = (evt) -> {
        if (isRefreshRequiredForCaseEvent(evt)) {
            onRefresh();
        }
    };
    
    private final List<UpdateGovernor> governors;
    private final Set<Case.Events> caseEvents;
    private final Runnable onUpdate;
    
    
    public EventUpdateHandler(Runnable onUpdate, UpdateGovernor...governors) {
        if (onUpdate == null) {
            throw new IllegalArgumentException("onUpdate parameter must be non-null.");
        }
        
        this.onUpdate = onUpdate;
        
        this.governors = governors == null ? Collections.emptyList() : Arrays.asList(governors);
        
        this.caseEvents = Stream.of(governors)
                .filter(governor -> governor.getCaseEventUpdates() != null)
                .flatMap(governor -> governor.getCaseEventUpdates().stream())
                .collect(Collectors.toSet());
        
    }
        
    protected boolean isRefreshRequired(ModuleDataEvent evt) {
        for (UpdateGovernor governor: governors) {
            if (governor.isRefreshRequired(evt)) {
                return true;
            }
        }
        
        return false;
    }

    protected boolean isRefreshRequired(ModuleContentEvent evt) {
        for (UpdateGovernor governor: governors) {
            if (governor.isRefreshRequired(evt)) {
                return true;
            }
        }
        
        return false;
    }

    protected boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt) {
        for (UpdateGovernor governor: governors) {
            if (governor.isRefreshRequiredForCaseEvent(evt)) {
                return true;
            }
        }
        
        return false;
    }
    
    
    protected void onRefresh() {
        onUpdate.run();
    }
    
    public void register() {
        Case.addEventTypeSubscriber(caseEvents, caseEventsListener);
        refreshThrottler.registerForIngestModuleEvents();
    }
    
    public void unregister() {
        Case.removeEventTypeSubscriber(caseEvents, caseEventsListener);
        refreshThrottler.unregisterEventListener();
    }
}
