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

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * A root node containing child nodes of the multi user cases
 */
public final class MultiUserNode extends AbstractNode {

    @Messages({"CaseNode.column.name=Name",
        "CaseNode.column.createdTime=Created Time",
        "CaseNode.column.status=Status",
        "CaseNode.column.metadataFilePath=Path"})
    private static final Logger LOGGER = Logger.getLogger(MultiUserNode.class.getName());

    /**
     * Provides a root node with children which each represent a case.
     *
     * @param caseMap the map of cases and a boolean indicating if they have an
     *                alert
     */
    MultiUserNode(Map<CaseMetadata, Boolean> caseMap) {
        super(Children.create(new MultiUserNodeChildren(caseMap), true));
    }

    static class MultiUserNodeChildren extends ChildFactory<Entry<CaseMetadata, Boolean>> {

        private final Map<CaseMetadata, Boolean> caseMap;

        MultiUserNodeChildren(Map<CaseMetadata, Boolean> caseMap) {
            this.caseMap = caseMap;
        }

        @Override
        protected boolean createKeys(List<Entry<CaseMetadata, Boolean>> list) {
            if (caseMap != null && caseMap.size() > 0) {
                list.addAll(caseMap.entrySet());
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(Entry<CaseMetadata, Boolean> key) {
            return new MultiUserCaseNode(key);
        }

    }

    /**
     * A node which represents a single multi user case.
     */
    public static final class MultiUserCaseNode extends DisplayableItemNode {

        private final String caseName;
        private final String caseCreatedDate;
        private final String caseMetadataFilePath;
        private final boolean caseHasAlert;

        MultiUserCaseNode(Entry<CaseMetadata, Boolean> multiUserCase) {
            super(Children.LEAF);
            caseName = multiUserCase.getKey().getCaseDisplayName();
            caseCreatedDate = multiUserCase.getKey().getCreatedDate();
            caseHasAlert = multiUserCase.getValue();
            super.setName(caseName);
            setName(caseName);
            setDisplayName(caseName);
            caseMetadataFilePath = multiUserCase.getKey().getFilePath().toString();
        }

        @Override
        public boolean isLeafTypeNode() {
            return true;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return v.visit(this);
        }

        @Override
        public String getItemType() {
            return getClass().getName();
        }

        public String getMetadataFilePath() {
            return caseMetadataFilePath;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(Bundle.CaseNode_column_name(), Bundle.CaseNode_column_name(), Bundle.CaseNode_column_name(),
                    caseName));
            ss.put(new NodeProperty<>(Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(),
                    caseCreatedDate));
            ss.put(new NodeProperty<>(Bundle.CaseNode_column_status(), Bundle.CaseNode_column_status(), Bundle.CaseNode_column_status(),
                    (caseHasAlert == true ? "Alert" : "")));
            ss.put(new NodeProperty<>(Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath(),
                    caseMetadataFilePath));
            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            actions.add(new OpenMultiUserCaseAction(caseMetadataFilePath));  //open case context menu option
            return actions.toArray(new Action[actions.size()]);
        }
    }

    /**
     * An action that opens the specified case and hides the multi user case
     * panel.
     */
    private static final class OpenMultiUserCaseAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final String caseMetadataFilePath;

        OpenMultiUserCaseAction(String path) {
            super("Open Case");
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

}
