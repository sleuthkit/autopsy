/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.directorytree;

import java.awt.event.ActionEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Action that deletes blackboard artifacts requested and reloads the view
 */
public class ResultDeleteAction extends AbstractAction {
    
    enum ActionType {
        SINGLE_ARTIFACT  ///< deletes individual artifacts and assoc. attributes
        ,
        TYPE_ARTIFACTS ///< deletes all artifacts by type and assoc. attributes
    }
    
    private BlackboardArtifact art;
    private BlackboardArtifact.ARTIFACT_TYPE artType;
    private ActionType actionType;
    
    private static final Logger logger = Logger.getLogger(ResultDeleteAction.class.getName());

    ResultDeleteAction(String title) {
        super(title);
    }
    
    ResultDeleteAction(String title, BlackboardArtifact art) {
        this(title);
        this.art = art;
        this.actionType = ActionType.SINGLE_ARTIFACT;
    }
    
    ResultDeleteAction(String title, BlackboardArtifact.ARTIFACT_TYPE artType) {
        this(title);
        this.artType = artType;
        this.actionType = ActionType.TYPE_ARTIFACTS;
    }
    
    
    @Override
    public void actionPerformed(ActionEvent e) {
        if (actionType == ActionType.SINGLE_ARTIFACT) {
            deleteArtifact(art);
            DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();
            viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.fromID(art.getArtifactTypeID()));
        }
        else if (this.actionType == ActionType.TYPE_ARTIFACTS) {
            deleteArtifacts(artType);
            DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();
            viewer.refreshTree(artType);
        }
        else throw new IllegalArgumentException("Invalid action type: " + this.actionType);
    }
    
    //TODO should be moved to SleuthkitCase and BlackboardArtifact API
    private static void deleteArtifact(BlackboardArtifact art) {
        final SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
        final long artId = art.getArtifactID();
        try {
            ResultSet rs = skCase.runQuery("DELETE from blackboard_attributes where artifact_id = " + Long.toString(artId));
            skCase.closeRunQuery(rs);

            rs = skCase.runQuery("DELETE from blackboard_artifacts where artifact_id = " + Long.toString(artId));
            skCase.closeRunQuery(rs);
            
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not delete artifact by id: " + artId, ex);
        }
        
    }
    
    //TODO should be moved to SleuthkitCase
    private static void deleteArtifacts(BlackboardArtifact.ARTIFACT_TYPE artType) {
        // SELECT * from blackboard_attributes INNER JOIN blackboard_artifacts ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_ID AND blackboard_artifacts.artifact_type_id = 9;
        final SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
        try {
            ResultSet rs = skCase.runQuery("DELETE FROM blackboard_attributes WHERE artifact_id in "
                    + "(SELECT blackboard_artifacts.artifact_id FROM blackboard_artifacts "
                    + "INNER JOIN blackboard_attributes ON (blackboard_attributes.artifact_id = blackboard_artifacts.artifact_id) "
                    + "WHERE blackboard_artifacts.artifact_type_id = "
                    + Integer.toString(artType.getTypeID()));
            skCase.closeRunQuery(rs);

            rs = skCase.runQuery("DELETE from blackboard_artifacts where artifact_type_id = " 
                    + Integer.toString(artType.getTypeID()));
            skCase.closeRunQuery(rs);
            
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not delete artifacts by type id: " + artType.getTypeID(), ex);
        }
    }
    
}
