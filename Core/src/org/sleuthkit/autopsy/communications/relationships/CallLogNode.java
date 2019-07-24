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
package org.sleuthkit.autopsy.communications.relationships;

import java.util.logging.Level;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.communications.Utils;
import static org.sleuthkit.autopsy.communications.relationships.RelationshipsNodeUtilities.getAttributeDisplayString;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_END;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A BlackboardArtifactNode for Calllogs.
 */
final class CallLogNode extends BlackboardArtifactNode {
    
    private static final Logger logger = Logger.getLogger(CallLogNode.class.getName());
    
    final static String DURATION_PROP = "duration";
    
    CallLogNode(BlackboardArtifact artifact, String deviceID) { 
        super(artifact, Utils.getIconFilePath(Account.Type.PHONE));
        setDisplayName(deviceID);
    }
    
    @Override
    protected Sheet createSheet() {
        Sheet sheet = super.createSheet();
        Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
        if (sheetSet == null) {
            sheetSet = Sheet.createPropertiesSet();
            sheet.put(sheetSet);
        }

        final BlackboardArtifact artifact = getArtifact();
 
        BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        if (null != fromID && fromID != TSK_CALLLOG) {
            return sheet;
        }
        
        String phoneNumber = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM);
        if(phoneNumber == null || phoneNumber.isEmpty()) {
            phoneNumber = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO);
        }
        
        long duration = -1;
        try{
            duration = getCallDuration(artifact);
        } catch(TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Unable to get calllog duration for artifact: %d", artifact.getArtifactID()), ex);
        }

        sheetSet.put(createNode(TSK_DATETIME_START, artifact));
        sheetSet.put(createNode(TSK_DIRECTION, artifact));
        sheetSet.put(new NodeProperty<>(TSK_PHONE_NUMBER.getLabel(), TSK_PHONE_NUMBER.getDisplayName(), "", phoneNumber));
        if(duration != -1) {
            sheetSet.put(new NodeProperty<>("duration", "Duration", "", Long.toString(duration)));
        }

        return sheet;
    }
    
    NodeProperty<?> createNode(BlackboardAttribute.ATTRIBUTE_TYPE type, BlackboardArtifact artifact) {
        return new NodeProperty<>(type.getLabel(), type.getDisplayName(), type.getDisplayName(), getAttributeDisplayString(artifact, type));
    }
    
    long getCallDuration(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute startAttribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(TSK_DATETIME_START.getTypeID())));
        BlackboardAttribute endAttribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(TSK_DATETIME_END.getTypeID())));
        
        if(startAttribute == null || endAttribute == null) {
            return -1;
        }
        
        return endAttribute.getValueLong() - startAttribute.getValueLong();
    }
    
     /**
     * Circumvent DataResultFilterNode's slightly odd delegation to
     * BlackboardArtifactNode.getSourceName().
     *
     * @return the displayName of this Node, which is the type.
     */
    @Override
    public String getSourceName() {
        return getDisplayName();
    }
}
