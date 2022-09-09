/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JPanel;
import static javax.swing.SwingUtilities.isDescendingFrom;
import javax.swing.table.TableColumn;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.NodeAdapter;
import org.openide.nodes.NodeMemberEvent;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.communications.ModifiableProxyLookup;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_START;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION;

/**
 *
 * CallLogViewer Panel
 */
final class CallLogViewer extends javax.swing.JPanel implements RelationshipsViewer {

    private static final long serialVersionUID = 1L;

    private final CallLogsChildNodeFactory nodeFactory;

    private final CallLogDataViewer callLogDataViewer;
    private final ModifiableProxyLookup proxyLookup;
    private PropertyChangeListener focusPropertyListener;

    @Messages({
        "CallLogViewer_title=Call Logs",
        "CallLogViewer_noCallLogs=<No call logs found for selected account>",
        "CallLogViewer_recipient_label=To/From",
        "CallLogViewer_duration_label=Duration(seconds)",
        "CallLogViewer_device_label=Device"
    })

    /**
     * Creates new form CallLogViewer
     */
    CallLogViewer() {
        initComponents();

        callLogDataViewer = new CallLogDataViewer();

        bottomScrollPane.setViewportView(callLogDataViewer);

        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);

        nodeFactory = new CallLogsChildNodeFactory(null);
        proxyLookup = new ModifiableProxyLookup(createLookup(outlineViewPanel.getExplorerManager(), getActionMap()));

        outlineViewPanel.hideOutlineView(Bundle.CallLogViewer_noCallLogs());

        // If changing the order of these columns effects the location of the
        // phone number column be sure to adjust the renderer code below.
        outlineViewPanel.getOutlineView().setPropertyColumns(
                TSK_DIRECTION.getLabel(), TSK_DIRECTION.getDisplayName(),
                TSK_PHONE_NUMBER.getLabel(), Bundle.CallLogViewer_recipient_label(),
                TSK_DATETIME_START.getLabel(), TSK_DATETIME_START.getDisplayName(),
                CallLogNode.DURATION_PROP, Bundle.CallLogViewer_duration_label()
        );

        Outline outline = outlineViewPanel.getOutlineView().getOutline();
        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.CallLogViewer_device_label());

        outlineViewPanel.getExplorerManager().addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                final Node[] nodes = outlineViewPanel.getExplorerManager().getSelectedNodes();
                callLogDataViewer.setNode(nodes != null && nodes.length > 0 ? nodes[0] : null);
            }
        });

        outlineViewPanel.getExplorerManager().setRootContext(
                new TableFilterNode(
                        new DataResultFilterNode(
                                new AbstractNode(Children.create(nodeFactory, true)), outlineViewPanel.getExplorerManager()), true));

        outlineViewPanel.getExplorerManager().getRootContext().addNodeListener(new NodeAdapter() {
            @Override
            public void childrenAdded(NodeMemberEvent nme) {
                updateOutlineViewPanel();
            }

            @Override
            public void childrenRemoved(NodeMemberEvent nme) {
                updateOutlineViewPanel();
            }
        });

        TableColumn column = outline.getColumnModel().getColumn(2);
        column.setCellRenderer(new NodeTableCellRenderer());

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        splitPane = new javax.swing.JSplitPane();
        outlineViewPanel = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();
        bottomScrollPane = new javax.swing.JScrollPane();

        setLayout(new java.awt.GridBagLayout());

        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPane.setLeftComponent(outlineViewPanel);
        splitPane.setRightComponent(bottomScrollPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(splitPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    @Override
    public String getDisplayName() {
        return Bundle.CallLogViewer_title();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setSelectionInfo(SelectionInfo info) {
        callLogDataViewer.setNode(null);
        nodeFactory.refresh(info);

    }

    @Override
    public Lookup getLookup() {
        return outlineViewPanel.getLookup();
    }

    @Override
    public void addNotify() {
        super.addNotify();

        if (focusPropertyListener == null) {
            // See org.sleuthkit.autopsy.timeline.TimeLineTopComponent for a detailed
            // explaination of focusPropertyListener
            focusPropertyListener = (final PropertyChangeEvent focusEvent) -> {
                if (focusEvent.getPropertyName().equalsIgnoreCase("focusOwner")) {
                    handleFocusChange((Component) focusEvent.getNewValue());

                }
            };
        }

        //add listener that maintains correct selection in the Global Actions Context
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("focusOwner", focusPropertyListener); //NON-NLS
    }

    /**
     * Handle the switching of the proxyLookup due to focus change.
     *
     * @param newFocusOwner
     */
    private void handleFocusChange(Component newFocusOwner) {
        if (newFocusOwner == null) {
            return;
        }
        if (isDescendingFrom(newFocusOwner, callLogDataViewer)) {
            //if the focus owner is within the MessageContentViewer (the attachments table)
            proxyLookup.setNewLookups(createLookup(callLogDataViewer.getExplorerManager(), getActionMap()));
        } else if (isDescendingFrom(newFocusOwner, this)) {
            //... or if it is within the Results table.
            proxyLookup.setNewLookups(createLookup(outlineViewPanel.getExplorerManager(), getActionMap()));

        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (focusPropertyListener != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removePropertyChangeListener("focusOwner", focusPropertyListener); //NON-NLS
        }
    }

    private void updateOutlineViewPanel() {
        int nodeCount = outlineViewPanel.getExplorerManager().getRootContext().getChildren().getNodesCount();
        if (nodeCount == 0) {
            outlineViewPanel.hideOutlineView(Bundle.CallLogViewer_noCallLogs());
        } else {
            outlineViewPanel.showOutlineView();
        }
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane bottomScrollPane;
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel outlineViewPanel;
    private javax.swing.JSplitPane splitPane;
    // End of variables declaration//GEN-END:variables
}
