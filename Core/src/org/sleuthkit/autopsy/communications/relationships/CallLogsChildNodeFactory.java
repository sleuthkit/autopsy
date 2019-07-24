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
package org.sleuthkit.autopsy.communications.relationships;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.communications.relationships.CallLogsChildNodeFactory.CallLogNodeKey;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *A ChildFactory for CallLog artifacts. 
 */
final class CallLogsChildNodeFactory extends ChildFactory<CallLogNodeKey>{
    
    private static final Logger logger = Logger.getLogger(CallLogsChildNodeFactory.class.getName());
    
    private SelectionInfo selectionInfo;
    
    private final Map<String, String> deviceIDMap = new HashMap<>();
    
    CallLogsChildNodeFactory(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;
    }
    
    void refresh(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;
        refresh(true);
    }
    
    @Override
    protected boolean createKeys(List<CallLogNodeKey> list) {
        
        if(selectionInfo == null) {
            return true;
        }
        
        final Set<Content> relationshipSources;
        try {
            relationshipSources = selectionInfo.getRelationshipSources();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to load relationship sources.", ex); //NON-NLS
            return false;
        }


        for(Content content: relationshipSources) {
            if( !(content instanceof BlackboardArtifact)){
                continue;
            }

            BlackboardArtifact bba = (BlackboardArtifact) content;
            BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(bba.getArtifactTypeID());

            if ( fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG) { 
                
                String deviceID = "";
                try {
                    deviceID = getDeviceIDForDataSource(bba.getDataSource().getName());
                } catch (NoCurrentCaseException | TskCoreException ex) {
                    logger.log(Level.WARNING, String.format("Unable to get account for artifact data source: artifactID = %d", bba.getId()), ex);
                }
                
                list.add(new CallLogNodeKey(bba, deviceID));
            }
        }
        
        list.sort(new CallLogComparator(BlackboardArtifactDateComparator.ACCENDING));

        return true;
    }

    @Override
    protected Node createNodeForKey(CallLogNodeKey key) {
        
        return new CallLogNode(key.getArtifact(), key.getDeviceID());
    }
    
    /**
     * Gets the device ID for the given data source.  
     * 
     * To reduce lookup calls to the DB unique dataSourceName\deviceID pairs
     * are stored in deviceIDMap.
     * 
     * @param dataSourceName String name of data source
     * 
     * @return device ID for given dataSourceName or empty string if non is found.
     * 
     * @throws NoCurrentCaseException
     * @throws TskCoreException 
     */
    private String getDeviceIDForDataSource(String dataSourceName) throws NoCurrentCaseException, TskCoreException{
       
        String deviceID = deviceIDMap.get(dataSourceName);
        
        if(deviceID == null) {
            CommunicationsManager manager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
            CommunicationsFilter filter = new CommunicationsFilter();

            List<String> list = new ArrayList<>();
            list.add(dataSourceName);
            
            List<Account.Type> typeList = new ArrayList<Account.Type>();
            typeList.add(Account.Type.DEVICE);

            filter.addAndFilter(new CommunicationsFilter.DeviceFilter(list));
            filter.addAndFilter(new CommunicationsFilter.AccountTypeFilter(typeList));
            

            // This list should just have 1 item in it
            List<AccountDeviceInstance> adiList = manager.getAccountDeviceInstancesWithRelationships(filter);
            
            if( adiList != null && !adiList.isEmpty() ) {
                deviceID = adiList.get(0).getDeviceId();
            } else {
                deviceID = "";
            }     
            
            deviceIDMap.put(dataSourceName, deviceID);
        }
        
       return (deviceID != null ? deviceID : "");
    }
    
    /**
     * ChildFactory key class which contains a BlackboardArtifact and its
     * data source deviceID
     */
    final class CallLogNodeKey{
        private final BlackboardArtifact artifact;
        private final String deviceID;
        
        private CallLogNodeKey(BlackboardArtifact artifact, String deviceID) {
            this.artifact = artifact;
            this.deviceID = deviceID;
        }
        
        /**
         * Get the BlackboardArtifact for this key
         * 
         * @return BlackboardArtifact instance
         */
        BlackboardArtifact getArtifact() {
            return artifact;
        }
        
        /**
         * Gets the BlackboardArtifact data source device ID.
         * 
         * @return String device id.
         */
        String getDeviceID() {
            return deviceID;
        }
    }
    
    /**
     * A comparator for CallLogNodeKey objects
     */
    final class CallLogComparator implements Comparator<CallLogNodeKey>{
        
        final BlackboardArtifactDateComparator comparator;
        
        CallLogComparator(int direction) {
            comparator = new BlackboardArtifactDateComparator(direction);
        }

        @Override
        public int compare(CallLogNodeKey key1, CallLogNodeKey key2) {
            return comparator.compare(key1.getArtifact(), key2.getArtifact());
        }
    }
}
