/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingUtilities;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
/**
 * Provides a root node for the results views with a single child node that
 * displays a message as the sole item in its property sheet, useful for
 * displaying explanatory text in the result views when there is a node with no
 * children in the tree view.
 */
public final class CaseNode extends AbstractNode {

    @Messages({"CaseNode.column.name=Name",
        "CaseNode.column.createdTime=Created Time",
        "CaseNode.column.status=Status",
        "CaseNode.column.metadataFilePath=Path"})

    /**
     * Provides a root node for the results views with a single child node that
     * displays a message as the sole item in its property sheet, useful for
     * displaying explanatory text in the result views when there is a node with
     * no children in the tree view.
     *
     * @param displayedMessage The text for the property sheet of the child
     *                         node.
     */
    CaseNode(Map<CaseMetadata, Boolean> caseList) {
        super(Children.create(new CaseNodeChildren(caseList), true));
    }

    static class CaseNodeChildren extends ChildFactory<Entry<CaseMetadata, Boolean>> {

        Map<CaseMetadata, Boolean> caseList;

        public CaseNodeChildren(Map<CaseMetadata, Boolean> caseList) {
            this.caseList = caseList;
        }

        @Override
        protected boolean createKeys(List<Entry<CaseMetadata, Boolean>> list) {
            if (caseList != null && caseList.size() > 0) {
                list.addAll(caseList.entrySet());
            }
            System.out.println("NUM OF KEYS: " + list.size());
            return true;
        }

        @Override
        protected Node createNodeForKey(Entry<CaseMetadata, Boolean> key) {
            return new CaseNameNode(key);
        }

    }

    /**
     * The single child node of an EmptyNode, responsible for displaying a
     * message as the sole item in its property sheet.
     */
    public static final class CaseNameNode extends DisplayableItemNode {

        CaseMetadata multiUserCase;
        String caseMetadataFilePath;
        boolean caseHasAlert;

        CaseNameNode(Entry<CaseMetadata, Boolean> userCase) {
            super(Children.LEAF);
            multiUserCase = userCase.getKey();
            caseHasAlert = userCase.getValue();
            super.setName(multiUserCase.getCaseDisplayName());
            setName(multiUserCase.getCaseDisplayName());
            setDisplayName(multiUserCase.getCaseDisplayName());
            caseMetadataFilePath = multiUserCase.getFilePath().toString();
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
                    multiUserCase.getCaseDisplayName()));
            ss.put(new NodeProperty<>(Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(),
                    multiUserCase.getCreatedDate()));
            ss.put(new NodeProperty<>(Bundle.CaseNode_column_status(), Bundle.CaseNode_column_status(), Bundle.CaseNode_column_status(),
                    (caseHasAlert == true ? "Alert" : "")));
            ss.put(new NodeProperty<>(Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath(),
                    caseMetadataFilePath));
            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            actions.addAll(Arrays.asList(super.getActions(context)));
            actions.add(new OpenMultiUserCaseAction(multiUserCase.getFilePath()));
            return actions.toArray(new Action[actions.size()]);
        }
    }

    /**
     * An action that opens the case node which it was generated off of
     */
    private static final class OpenMultiUserCaseAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        private final Path caseMetadataFilePath;

        public OpenMultiUserCaseAction(Path path) {
            super("Open Case");
            caseMetadataFilePath = path;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            StartupWindowProvider.getInstance().close();
            openCaseThread(caseMetadataFilePath);
        }
    }

    private static void openCaseThread(Path caseMetadataFilePath) {

        new Thread(
                () -> {
                    try {
                        Case.openAsCurrentCase(caseMetadataFilePath.toString());
                    } catch (CaseActionException ex) {
                        if (null != ex.getCause() && !(ex.getCause() instanceof CaseActionCancelledException)) {
                            //                   LOGGER.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", caseMetadataFilePath), ex); //NON-NLS
                            MessageNotifyUtil.Message.error(ex.getCause().getLocalizedMessage());
                        }
                        SwingUtilities.invokeLater(() -> {
                            //GUI changes done back on the EDT
                            StartupWindowProvider.getInstance().open();
                        });
                    } finally {
                        SwingUtilities.invokeLater(() -> {
                            //GUI changes done back on the EDT
                        });
                    }
                }
        ).start();
    }

}
