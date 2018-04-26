/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * A root node containing child nodes of the multi user cases
 */
final class MultiUserNode extends AbstractNode {

    @Messages({"CaseNode.column.name=Name",
        "CaseNode.column.createdTime=Created Time",
        "CaseNode.column.metadataFilePath=Path"})
    private static final Logger LOGGER = Logger.getLogger(MultiUserNode.class.getName());
    private static final String LOG_FILE_NAME = "auto_ingest_log.txt";

    /**
     * Provides a root node with children which each represent a case.
     *
     * @param caseList the list of CaseMetadata objects representing the cases
     */
    MultiUserNode(List<CaseMetadata> caseList) {
        super(Children.create(new MultiUserNodeChildren(caseList), true));
    }

    static class MultiUserNodeChildren extends ChildFactory<CaseMetadata> {

        private final List<CaseMetadata> caseList;

        MultiUserNodeChildren(List<CaseMetadata> caseList) {
            this.caseList = caseList;
        }

        @Override
        protected boolean createKeys(List<CaseMetadata> list) {
            if (caseList != null && caseList.size() > 0) {
                list.addAll(caseList);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(CaseMetadata key) {
            return new MultiUserCaseNode(key);
        }

    }

    /**
     * A node which represents a single multi user case.
     */
    static final class MultiUserCaseNode extends AbstractNode {

        private final String caseName;
        private final String caseCreatedDate;
        private final String caseMetadataFilePath;
        private final Path caseLogFilePath;

        MultiUserCaseNode(CaseMetadata multiUserCase) {
            super(Children.LEAF);
            caseName = multiUserCase.getCaseDisplayName();
            caseCreatedDate = multiUserCase.getCreatedDate();
            super.setName(caseName);
            setName(caseName);
            setDisplayName(caseName);
            caseMetadataFilePath = multiUserCase.getFilePath().toString();
            caseLogFilePath = Paths.get(multiUserCase.getCaseDirectory(), LOG_FILE_NAME);
        }
        
        /**
         * Returns action to open the Case represented by this node
         * @return an action which will open the current case
         */
        @Override 
        public Action getPreferredAction() {
            return new OpenMultiUserCaseAction(caseMetadataFilePath);
        }

        @Override
        protected Sheet createSheet() {
            Sheet sheet = super.createSheet();
            Sheet.Set sheetSet = sheet.get(Sheet.PROPERTIES);
            if (sheetSet == null) {
                sheetSet = Sheet.createPropertiesSet();
                sheet.put(sheetSet);
            }
            sheetSet.put(new NodeProperty<>(Bundle.CaseNode_column_name(), Bundle.CaseNode_column_name(), Bundle.CaseNode_column_name(),
                    caseName));
            sheetSet.put(new NodeProperty<>(Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(),
                    caseCreatedDate));
            sheetSet.put(new NodeProperty<>(Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath(),
                    caseMetadataFilePath));
            return sheet;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            actions.add(new OpenMultiUserCaseAction(caseMetadataFilePath));  //open case context menu option
            actions.add(new OpenCaseLogAction(caseLogFilePath));
            return actions.toArray(new Action[actions.size()]);
        }
    }

    @Messages({"MultiUserNode.OpenMultiUserCaseAction.text=Open Case"})
    /**
     * An action that opens the specified case and hides the multi user case
     * panel.
     */
    private static final class OpenMultiUserCaseAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final String caseMetadataFilePath;

        OpenMultiUserCaseAction(String path) {
            super(Bundle.MultiUserNode_OpenMultiUserCaseAction_text());
            caseMetadataFilePath = path;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            StartupWindowProvider.getInstance().close();
            MultiUserCasesDialog.getInstance().setVisible(false);
            new Thread(
                    () -> {
                        try {
                            Case.openAsCurrentCase(caseMetadataFilePath);
                        } catch (CaseActionException ex) {
                            if (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException)) {
                                LOGGER.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseMetadataFilePath), ex); //NON-NLS
                                MessageNotifyUtil.Message.error(ex.getCause().getLocalizedMessage());
                            }
                            SwingUtilities.invokeLater(() -> {
                                //GUI changes done back on the EDT
                                StartupWindowProvider.getInstance().open();
                                MultiUserCasesDialog.getInstance().setVisible(true);
                            });
                        }
                    }
            ).start();
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @Messages({"MultiUserNode.OpenCaseLogAction.text=Open Log File"})
    /**
     * An action that opens the specified case and hides the multi user case
     * panel.
     */
    private static final class OpenCaseLogAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final Path pathToLog;

        OpenCaseLogAction(Path caseLogFilePath) {
            super(Bundle.MultiUserNode_OpenCaseLogAction_text());
            pathToLog = caseLogFilePath;
            this.setEnabled(caseLogFilePath != null && caseLogFilePath.toFile().exists());
        }

        @Override
        public void actionPerformed(ActionEvent e) {

            if (pathToLog != null) {
                try {
                    if (pathToLog.toFile().exists()) {
                        Desktop.getDesktop().edit(pathToLog.toFile());

                    } else {
                        JOptionPane.showMessageDialog(MultiUserCasesDialog.getInstance(), org.openide.util.NbBundle.getMessage(MultiUserNode.class, "DisplayLogDialog.cannotFindLog"),
                                org.openide.util.NbBundle.getMessage(MultiUserNode.class, "DisplayLogDialog.unableToShowLogFile"), JOptionPane.ERROR_MESSAGE);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error attempting to open case auto ingest log file %s", pathToLog), ex);
                    JOptionPane.showMessageDialog(MultiUserCasesDialog.getInstance(),
                            org.openide.util.NbBundle.getMessage(MultiUserNode.class, "DisplayLogDialog.cannotOpenLog"),
                            org.openide.util.NbBundle.getMessage(MultiUserNode.class, "DisplayLogDialog.unableToShowLogFile"),
                            JOptionPane.PLAIN_MESSAGE);
                }
            }
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

}
