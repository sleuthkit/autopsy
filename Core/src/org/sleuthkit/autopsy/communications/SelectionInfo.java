/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
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

import java.util.Set;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.CommunicationsFilter;


/**
 * Class to wrap the details of the current selection from the AccountBrowser or
 * VisualizationPane
 * 
 */
public class SelectionInfo {
        
        static final private Logger logger = Logger.getLogger(SelectionInfo.class.getName());

        private final Set<AccountDeviceInstance> accountDeviceInstances;
        private final CommunicationsFilter communicationFilter;
        
        SelectionInfo(Set<AccountDeviceInstance> accountDeviceInstances, CommunicationsFilter communicationFilter) {
           this.accountDeviceInstances = accountDeviceInstances;
           this.communicationFilter = communicationFilter;
        }
        
        public Set<AccountDeviceInstance> getAccountDevicesInstances(){
            return accountDeviceInstances;
        }
        
        public CommunicationsFilter getCommunicationsFilter() {
            return communicationFilter;
        }
    
}
