/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.SubFilter;
import static org.sleuthkit.datamodel.Relationship.Type.CALL_LOG;
import static org.sleuthkit.datamodel.Relationship.Type.MESSAGE;

/**
 * Manages the state history for the Communications window. History is currently
 * maintained for the CommunicationsFilter, the List of pinned accounts and the 
 * scale value of the graph. 
 */
final class StateManager {
    
    private final History<CommunicationsState> historyManager = new History<>();
    private CommunicationsFilter comFilter;
    private final PinnedAccountModel pinModel;
    
    /**
     * Manages the state history for the Communications window.
     * 
     * @param pinModel PinnedACcountModel
     */
    public StateManager(PinnedAccountModel pinModel){
        this.pinModel = pinModel;
        CVTEvents.getCVTEventBus().register(this);
    }
    
    @Subscribe
    void pinAccount(CVTEvents.PinAccountsEvent pinEvent) {
        if(pinEvent.isReplace()){
            HashSet<AccountDeviceInstanceKey> pinnedList = new HashSet<>();
            pinnedList.addAll(pinEvent.getAccountDeviceInstances());
            historyManager.advance(new CommunicationsState(comFilter.getAndFilters(), pinnedList, -1));
        } else {
            HashSet<AccountDeviceInstanceKey> pinnedList = new HashSet<>();
            pinnedList.addAll(pinEvent.getAccountDeviceInstances());
            pinnedList.addAll(pinModel.getPinnedAccounts());
            
            historyManager.advance(new CommunicationsState( comFilter.getAndFilters(), pinnedList, -1));
        }
    }
    
    @Subscribe
    void filterChange(CVTEvents.FilterChangeEvent fileterEvent) {
        comFilter = fileterEvent.getNewFilter();
        historyManager.advance(new CommunicationsState(comFilter.getAndFilters(), pinModel.getPinnedAccounts(), -1));
    }
    
    @Subscribe
    void unpinAccounts(CVTEvents.UnpinAccountsEvent pinEvent) {

        HashSet<AccountDeviceInstanceKey> pinnedList = new HashSet<>();
        pinnedList.addAll(pinModel.getPinnedAccounts());
        pinnedList.removeAll(pinEvent.getAccountDeviceInstances());
        
        historyManager.advance(new CommunicationsState(comFilter.getAndFilters(), pinnedList, -1));
    }
    
    @Subscribe
    void zoomedGraph(CVTEvents.ZoomEvent zoomEvent) {
        historyManager.advance(new CommunicationsState(comFilter.getAndFilters(), pinModel.getPinnedAccounts(), zoomEvent.getZoomValue()));
    }
    
    /**
     * Returns the next state object in the history.
     * 
     * @return CommunicationsState or null if canRetreat is null
     */
    public CommunicationsState retreat(){
        if(canRetreat()) {
            return historyManager.retreat();
        } else {
            return null;
        }
    }
    
    /**
     * Returns the next state object in the forward history.
     * 
     * @return CommunicationsState or null if canAdvance is null
     */
    public CommunicationsState advance() {
        if(canAdvance()) {
            return historyManager.advance();
        } else {
            return null;
        }
    }
    
    /**
     * Returns true if there is a history of states.
     * 
     * @return boolean
     */
    public boolean canRetreat() {
        return historyManager.canRetreat();
    }
    
    /**
     * Returns true if there is history to advance too.
     * 
     * @return 
     */
    public boolean canAdvance(){
        return historyManager.canAdvance();
    }

    /**
     * Object to store one instance of the state of the Communications window.
     */
    final class CommunicationsState{
        private final List<SubFilter> communcationFilters;
        private final Set<AccountDeviceInstanceKey> pinnedList;
        private final double zoomValue;
        
        /**
         * Stores all the properties of the current state of the Communications 
         * window.
         * 
         * @param communcationFilters List of the SubFilters from the FiltersPanel
         * @param pinnedList Set of AccountDeviceInstanceKey
         * @param zoomValue Double value of the current graph scale
         */
        protected CommunicationsState(List<SubFilter> communcationFilters, Set<AccountDeviceInstanceKey> pinnedList, double zoomValue){
            this.pinnedList = pinnedList;
            this.communcationFilters = communcationFilters;
            this.zoomValue = zoomValue;
        }
   
        /**
         * Return whether or not this state contains a zoom change
         * 
         * @return boolean
         */
        public boolean isZoomChange() {
            return (zoomValue != -1);
        }
        
        /**
         * Returns a list of the currently pinned accounts.
         * 
         * @return Set of AccountDeviceInstanceKey
         */
        public Set<AccountDeviceInstanceKey> getPinnedList(){
            return pinnedList;
        }
        
        /**
         * Returns a list of communication SubFilters.
         * 
         * @return List of SubFilter
         */
        public List<SubFilter> getCommunicationsFilters(){
            return communcationFilters;
        }
        
        /**
         * Return a new CommunicationsFilter object based on the list of
         * SubFilters
         *
         * @return CommunicationsFilter
         */
        public CommunicationsFilter getCommunicationsFilter() {
            CommunicationsFilter newFilters = new CommunicationsFilter();
            newFilters.addAndFilter(new CommunicationsFilter.RelationshipTypeFilter(ImmutableSet.of(CALL_LOG, MESSAGE)));
            communcationFilters.forEach(filter -> {
                newFilters.addAndFilter(filter);
            });

            return newFilters;
        }
        
        /**
         * Return the value for the % zoom.
         * 
         * @return double value % zoom or -1 if zoom did not change
         */
        public double getZoomValue() {
            return zoomValue;
        }
    }
}
