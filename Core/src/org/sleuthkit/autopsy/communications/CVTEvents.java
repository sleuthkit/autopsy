/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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
import com.google.common.eventbus.EventBus;
import java.util.Collection;
import org.sleuthkit.autopsy.communications.FiltersPanel.DateControlState;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.autopsy.communications.StateManager.CommunicationsState;
import org.sleuthkit.datamodel.AccountDeviceInstance;

/**
 * Provide the singleton EventBus.
 */
final class CVTEvents {

    private final static EventBus cvtEventBus = new EventBus();

    static EventBus getCVTEventBus() {
        return cvtEventBus;
    }

    private CVTEvents() {
    }

    /**
     * Invoked when a change from the FiltersPanel occures.
     */
    static final class FilterChangeEvent {

        private final CommunicationsFilter newFilter;
        private final DateControlState startControlState;
        private final DateControlState endControlState;

        CommunicationsFilter getNewFilter() {
            return newFilter;
        }
        
        DateControlState getStartControlState() {
            return startControlState;
        }
        
        DateControlState getEndControlState() {
            return endControlState;
        }

        FilterChangeEvent(CommunicationsFilter newFilter, DateControlState startControlState, DateControlState endControlState) {
            this.newFilter = newFilter;
            this.startControlState = startControlState;
            this.endControlState = endControlState;
        }

    }

    /**
     * Invoked when a change in the pinned accounts occures.
     */
    static final class PinAccountsEvent {

        private final ImmutableSet<AccountDeviceInstance> accounInstances;
        private final boolean replace;

        public boolean isReplace() {
            return replace;
        }

        ImmutableSet<AccountDeviceInstance> getAccountDeviceInstances() {
            return accounInstances;
        }

        PinAccountsEvent(Collection<? extends AccountDeviceInstance> accountDeviceInstances, boolean replace) {
            this.accounInstances = ImmutableSet.copyOf(accountDeviceInstances);
            this.replace = replace;
        }
    }

    /**
     * Invoked when a change in the unpinned accounts occures.
     */
    static final class UnpinAccountsEvent {

        private final ImmutableSet<AccountDeviceInstance> accountInstances;

        public ImmutableSet<AccountDeviceInstance> getAccountDeviceInstances() {
            return accountInstances;
        }

         UnpinAccountsEvent(Collection<? extends AccountDeviceInstance> accountDeviceInstances) {
            this.accountInstances = ImmutableSet.copyOf(accountDeviceInstances);
        }
    }
    
    /**
    * Invoked when there is a change in the state of the window.
    */
    static final class StateChangeEvent {
        private final CommunicationsState newState;
        
        StateChangeEvent(CommunicationsState newState) {
            this.newState = newState;
        }
        
        public CommunicationsState getCommunicationsState(){
            return newState;
        }
    }
    
    /**
    * Invoked when change in the link analysis graph scale occures.
    */
    static final class ScaleChangeEvent {
        private final double scaleValue;
        
        ScaleChangeEvent(double scaleValue) {
            this.scaleValue = scaleValue;
        }
        
        public double getZoomValue(){
            return scaleValue;
        }
    }
}
