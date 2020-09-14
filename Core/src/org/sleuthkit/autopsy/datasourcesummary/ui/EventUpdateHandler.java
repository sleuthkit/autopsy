/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;
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
    
    private final Set<Case.Events> caseEvents;
    
    protected EventUpdateHandler(Set<Case.Events> caseEvents) {
        this.caseEvents = caseEvents;
    }
        
    protected boolean isRefreshRequired(ModuleDataEvent evt) {
        //return artifactTypesForRefresh.contains(evt.getBlackboardArtifactType().getTypeID());
    }

    protected boolean isRefreshRequired(ModuleContentEvent evt) {
        //return refreshOnNewContent;
    }

    protected boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt) {
        //return true;
    }
    
    
    public void onRefresh() {
        // to go here
    }
    
    public void register() {
        Case.addEventTypeSubscriber(caseEvents, caseEventsListener);
        refreshThrottler.registerForIngestModuleEvents();
    }
    
    public void unregister() {
        Case.removeEventTypeSubscriber(caseEvents, caseEventsListener);
        refreshThrottler.unregisterEventListener();
    }

    
//    protected BaseDataSourceSummaryPanel(EventUpdateGovernor...dataModels) {
//        this(getUnionSet(EventUpdateGovernor::getCaseEventUpdates, dataModels),
//                getUnionSet(EventUpdateGovernor::getArtifactIdUpdates, dataModels),
//                Stream.of(dataModels).anyMatch(EventUpdateGovernor::shouldRefreshOnNewContent)
//        );
//        
//        
//        this.artifactTypesForRefresh = (artifactTypesForRefresh == null)
//                ? new HashSet<>()
//                : new HashSet<>(artifactTypesForRefresh);
//
//        this.refreshOnNewContent = refreshOnNewContent;
//
//        if (refreshOnNewContent || this.artifactTypesForRefresh.size() > 0) {
//            refreshThrottler.registerForIngestModuleEvents();
//        }
//
//        if (caseEvents != null) {
//            Case.addEventTypeSubscriber(caseEvents, caseEventsListener);
//        }
//    }
//    
//    
//    private final Set<Integer> artifactTypesForRefresh;
//    private final boolean refreshOnNewContent;
//    
//    private static <I, O> Set<O> getUnionSet(Function<I, Collection<? extends O>> mapper, I... items) {
//        return Stream.of(items)
//                .flatMap((item) -> mapper.apply(item).stream())
//                .collect(Collectors.toSet());
//    }  

}
