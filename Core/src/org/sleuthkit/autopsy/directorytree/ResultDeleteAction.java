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
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Action that deletes blackboard artifacts requested and reloads the view
 * @deprecated do not use, it is here in case we ever pick up on this work
 */
@Deprecated
 class ResultDeleteAction extends AbstractAction {

    private enum ActionType {

        SINGLE_ARTIFACT ///< deletes individual artifacts and assoc. attributes
        ,
        MULT_ARTIFACTS ///< deletes multiple artifacts and assoc. attributes
        ,
        TYPE_ARTIFACTS ///< deletes all artifacts by type and assoc. attributes
    }
    private BlackboardArtifact art;
    private BlackboardArtifact.ARTIFACT_TYPE artType;
    private List<BlackboardArtifact> arts;
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

    ResultDeleteAction(String title, List<BlackboardArtifact> arts) {
        this(title);
        this.arts = arts;
        this.actionType = ActionType.MULT_ARTIFACTS;
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
        if (actionType == ActionType.MULT_ARTIFACTS) {
            for (BlackboardArtifact art : arts) {
                deleteArtifact(art);
            }
            DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();
            viewer.refreshTree(BlackboardArtifact.ARTIFACT_TYPE.fromID(art.getArtifactTypeID()));
        } else if (this.actionType == ActionType.TYPE_ARTIFACTS) {
            if (JOptionPane.showConfirmDialog(null,
                                              NbBundle.getMessage(this.getClass(),
                                                                  "ResultDeleteAction.actionPerf.confDlg.delAllResults.msg",
                                                                  artType.getDisplayName()),
                                              NbBundle.getMessage(this.getClass(),
                                                                  "ResultDeleteAction.actoinPerf.confDlg.delAllresults.details",
                                                                  artType.getDisplayName()), JOptionPane.YES_NO_OPTION,
                                              JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                deleteArtifacts(artType);
                DirectoryTreeTopComponent viewer = DirectoryTreeTopComponent.findInstance();
                viewer.refreshTree(artType);
            }
        } else {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "ResultDeleteAction.exception.invalidAction.msg",
                                        this.actionType));
        }
    }

    //TODO should be moved to SleuthkitCase and BlackboardArtifact API
    @SuppressWarnings("deprecation")
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


    @SuppressWarnings("deprecation")
    private static void deleteArtifactsByAttributeValue(BlackboardArtifact.ARTIFACT_TYPE artType,
            BlackboardAttribute.ATTRIBUTE_TYPE attrType, String value) {

        final SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
        ResultSet rs = null;
        try {
            //first to select to get artifact ids to delete
            //then join delete attrs
            //then delete arts by id
                    rs = skCase.runQuery("DELETE FROM blackboard_attributes WHERE artifact_id IN "
                    + "(SELECT blackboard_artifacts.artifact_id FROM blackboard_artifacts "
                    + "INNER JOIN blackboard_attributes ON (blackboard_attributes.artifact_id = blackboard_artifacts.artifact_id) "
                    + "WHERE blackboard_artifacts.artifact_type_id = "
                    + Integer.toString(artType.getTypeID())
                    + " AND blackboard_attributes.attribute_type_id = " + Integer.toString(attrType.getTypeID())
                    + " AND blackboard_attributes.value_type = " + BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING.getType()
                    + " AND blackboard_attributes.value_text = '" + value + "'"
                    + ")");
        

            //rs = skCase.runQuery("DELETE from blackboard_artifacts where artifact_type_id = "
              //      + Integer.toString(artType.getTypeID()));
            //skCase.closeRunQuery(rs);

        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not delete artifacts by type id: " + artType.getTypeID(), ex);
        }
        finally {
            if (rs != null) {
                try {
                    skCase.closeRunQuery(rs);
                } catch (SQLException ex) {
                    logger.log(Level.WARNING, "Error closing result set after deleting", ex);
                }
            }
        }

    }

    //TODO should be moved to SleuthkitCase
    @SuppressWarnings("deprecation")
    private static void deleteArtifacts(BlackboardArtifact.ARTIFACT_TYPE artType) {
        // SELECT * from blackboard_attributes INNER JOIN blackboard_artifacts ON blackboard_artifacts.artifact_id = blackboard_attributes.artifact_ID AND blackboard_artifacts.artifact_type_id = 9;
        final SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
        try {
            ResultSet rs = skCase.runQuery("DELETE FROM blackboard_attributes WHERE artifact_id IN "
                    + "(SELECT blackboard_artifacts.artifact_id FROM blackboard_artifacts "
                    + "INNER JOIN blackboard_attributes ON (blackboard_attributes.artifact_id = blackboard_artifacts.artifact_id) "
                    + "WHERE blackboard_artifacts.artifact_type_id = "
                    + Integer.toString(artType.getTypeID())
                    + ")");
            skCase.closeRunQuery(rs);

            rs = skCase.runQuery("DELETE from blackboard_artifacts where artifact_type_id = "
                    + Integer.toString(artType.getTypeID()));
            skCase.closeRunQuery(rs);

        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not delete artifacts by type id: " + artType.getTypeID(), ex);
        }
    }
}
