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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *A ChildFactory for CallLog artifacts. 
 */
final class CallLogsChildNodeFactory extends ChildFactory<BlackboardArtifact>{
    
    private static final Logger logger = Logger.getLogger(CallLogsChildNodeFactory.class.getName());
    
    private SelectionInfo selectionInfo;
    
    private Map<String, String> deviceIDMap = new HashMap<String,String>();
    
    CallLogsChildNodeFactory(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;
    }
    
    void refresh(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;
        refresh(true);
    }
    
    @Override
    protected boolean createKeys(List<BlackboardArtifact> list) {
        
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
                list.add(bba);
            }
        }
        
        list.sort(new BlackboardArtifactDateComparator(BlackboardArtifactDateComparator.ACCENDING));

        return true;
    }
    
    @Override
    protected Node createNodeForKey(BlackboardArtifact key) {
        String deviceID = "";
        try {
            deviceID = getDeviceIDForDataSource(key.getDataSource().getName());
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to get account for artifact data source: artifactID = %d", key.getId()), ex);
        }
        
        return new CallLogNode(key, deviceID);
    }
    
    private String getDeviceIDForDataSource(String dataSourceName) throws NoCurrentCaseException, TskCoreException{
       
        String deviceID = deviceIDMap.get(dataSourceName);
        
        if(deviceID == null) {
            CommunicationsManager manager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
            CommunicationsFilter filter = new CommunicationsFilter();

            List<String> list = new ArrayList<>();
            list.add(dataSourceName);

            filter.addAndFilter(new CommunicationsFilter.DeviceFilter(list));

            // This list should just have 1 item in it
            List<AccountDeviceInstance> adiList = manager.getAccountDeviceInstancesWithRelationships(filter);
            
            if(adiList != null && adiList.size() > 0) {
                deviceID = adiList.get(0).getDeviceId();
            } else {
                deviceID = "";
            }     
            
            deviceIDMap.put(dataSourceName, deviceID);
        }
        
       return deviceID;
    }
}
