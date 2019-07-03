/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications.relationships;

import org.openide.nodes.Sheet;
import static org.sleuthkit.autopsy.communications.relationships.RelationshipsNodeUtilities.getAttributeDisplayString;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO;

/**
 *
 * 
 */
public class CallLogNode extends BlackboardArtifactNode {
    
    CallLogNode(BlackboardArtifact artifact) {
        super(artifact);
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
            
        sheetSet.put(createNode(TSK_PHONE_NUMBER_FROM, artifact));
        sheetSet.put(createNode(TSK_PHONE_NUMBER_TO, artifact));
        sheetSet.put(createNode(TSK_DATETIME_START, artifact));
        
        return sheet;
    }
    
    NodeProperty<?> createNode(BlackboardAttribute.ATTRIBUTE_TYPE type, BlackboardArtifact artifact) {
        return new NodeProperty<>(type.getLabel(), type.getDisplayName(), "", getAttributeDisplayString(artifact, type));
    }
}
