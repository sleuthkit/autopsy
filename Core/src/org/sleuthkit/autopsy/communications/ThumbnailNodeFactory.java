/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author kelly
 */
public class ThumbnailNodeFactory extends ChildFactory<Content> {
    private static final Logger logger = Logger.getLogger(ThumbnailNodeFactory.class.getName());
    
    private SelectionInfo selectionInfo;
    
    ThumbnailNodeFactory(SelectionInfo selectionInfo) {
        this.selectionInfo = selectionInfo;
    }
    
    @Override
    protected boolean createKeys(List<Content> list) {
        CommunicationsManager communicationManager;
        try {
            communicationManager = Case.getCurrentCaseThrows().getSleuthkitCase().getCommunicationsManager();
        } catch (NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get communications manager from case.", ex); //NON-NLS
            return false;
        }
        
        if(selectionInfo == null) {
            return true;
        }
        
        final Set<Content> relationshipSources;

        try {
            relationshipSources = communicationManager.getRelationshipSources(selectionInfo.getAccountDevicesInstances(), selectionInfo.getCommunicationsFilter());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get relationship sources.", ex); //NON-NLS
            return false;
        }
        
        relationshipSources.stream().filter((content) -> (content instanceof BlackboardArtifact)).forEachOrdered((content) -> {

            BlackboardArtifact bba = (BlackboardArtifact) content;
            BlackboardArtifact.ARTIFACT_TYPE fromID = BlackboardArtifact.ARTIFACT_TYPE.fromID(bba.getArtifactTypeID());

            if (fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG
                    || fromID == BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE) {

                try{
                    if(bba.hasChildren()) {
 
                        for(Content child: bba.getChildren()) {
                            list.add(child);
                        }
                    }
                } catch(TskCoreException ex) {
                    
                }
            }
         });
        
        return true;
    }
    
    @Override
    protected Node createNodeForKey(Content key) {
        return new ThumbnailNode((AbstractContent)key);
    }
}
