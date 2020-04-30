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

import com.google.common.eventbus.Subscribe;
import java.util.HashSet;
import java.util.Set;
import org.sleuthkit.autopsy.communications.FiltersPanel.DateControlState;
import org.sleuthkit.autopsy.coreutils.History;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;

/**
 * Manages the state history for the Communications window. History is currently
 * maintained for the CommunicationsFilter, the List of pinned accounts and the 
 * scale value of the graph. 
 */
final class StateManager {
    
    private final History<CommunicationsState> historyManager = new History<>();
    private CommunicationsFilter comFilter;
    private final PinnedAccountModel pinModel;
    private DateControlState currentStartState;
    private DateControlState currentEndState;
    
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
            HashSet<AccountDeviceInstance> pinnedList = new HashSet<>();
            pinnedList.addAll(pinEvent.getAccountDeviceInstances());
            historyManager.advance(new CommunicationsState(comFilter, pinnedList, -1, currentStartState, currentEndState));
        } else {
            HashSet<AccountDeviceInstance> pinnedList = new HashSet<>();
            pinnedList.addAll(pinEvent.getAccountDeviceInstances());
            pinnedList.addAll(pinModel.getPinnedAccounts());
            
            historyManager.advance(new CommunicationsState( comFilter, pinnedList, -1, currentStartState, currentEndState));
        }
    }
    
    @Subscribe
    void filterChange(CVTEvents.FilterChangeEvent filterEvent) {
        comFilter = filterEvent.getNewFilter();
        currentStartState = filterEvent.getStartControlState();
        currentEndState = filterEvent.getEndControlState();
        historyManager.advance(new CommunicationsState(comFilter, pinModel.getPinnedAccounts(), -1, currentStartState, currentEndState));
    }
    
    @Subscribe
    void unpinAccounts(CVTEvents.UnpinAccountsEvent pinEvent) {

        HashSet<AccountDeviceInstance> pinnedList = new HashSet<>();
        pinnedList.addAll(pinModel.getPinnedAccounts());
        pinnedList.removeAll(pinEvent.getAccountDeviceInstances());
        
        historyManager.advance(new CommunicationsState(comFilter, pinnedList, -1, currentStartState, currentEndState));
    }
    
    @Subscribe
    void zoomedGraph(CVTEvents.ScaleChangeEvent zoomEvent) {
        historyManager.advance(new CommunicationsState(comFilter, pinModel.getPinnedAccounts(), zoomEvent.getZoomValue(), currentStartState, currentEndState));
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
        private final CommunicationsFilter communcationFilter;
        private final Set<AccountDeviceInstance> pinnedList;
        private final double zoomValue;
        private final DateControlState startDateState;
        private final DateControlState endDateState;
        
        /**
         * Stores all the properties of the current state of the Communications 
         * window.
         * 
         * @param communcationFilter Instance of CommunicationsFilter
         * @param pinnedList Set of AccountDeviceInstanceKey
         * @param zoomValue Double value of the current graph scale
         */
        protected CommunicationsState(CommunicationsFilter communcationFilter, 
                Set<AccountDeviceInstance> pinnedList, double zoomValue, 
                DateControlState startDateState, DateControlState endDateState){
            this.pinnedList = pinnedList;
            this.communcationFilter = communcationFilter;
            this.zoomValue = zoomValue;
            this.startDateState = startDateState;
            this.endDateState = endDateState;
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
        public Set<AccountDeviceInstance> getPinnedList(){
            return pinnedList;
        }

        /**
         * Return a new CommunicationsFilter object based on the list of
         * SubFilters
         *
         * @return CommunicationsFilter
         */
        public CommunicationsFilter getCommunicationsFilter() {
            return communcationFilter;
        }
        
        /**
         * Return the value for the % zoom.
         * 
         * @return double value % zoom or -1 if zoom did not change
         */
        public double getZoomValue() {
            return zoomValue;
        }
        
        /**
         * Returns the state for the start date picker.
         * 
         * @return Start DateControlState
         */
        public DateControlState getStartControlState() {
            return startDateState;
        }
        
         /** 
         * Returns the state for the end date picker.
         * 
         * @return Etart DateControlState
         */
        public DateControlState getEndControlState() {
            return endDateState;
        }
    }
}
