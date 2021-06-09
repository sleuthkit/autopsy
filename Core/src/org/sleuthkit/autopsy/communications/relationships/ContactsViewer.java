/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JPanel;
import static javax.swing.SwingUtilities.isDescendingFrom;
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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.communications.ModifiableProxyLookup;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Visualization for contact nodes.
 *
 */
final class ContactsViewer extends JPanel implements RelationshipsViewer {

    private static final long serialVersionUID = 1L;

    private final Outline outline;
    private final ModifiableProxyLookup proxyLookup;
    private PropertyChangeListener focusPropertyListener;
    private final ContactsChildNodeFactory nodeFactory;
    private final ContactDataViewer contactPane;

    @NbBundle.Messages({
        "ContactsViewer_tabTitle=Contacts",
        "ContactsViewer_columnHeader_Name=Name",
        "ContactsViewer_columnHeader_Phone=Phone",
        "ContactsViewer_columnHeader_Email=Email",
        "ContactsViewer_noContacts_message=<No contacts found for selected account>"
    })

    /**
     * Visualization for contact nodes.
     */
    ContactsViewer() {
        initComponents();

        contactPane = new ContactDataViewer();
        splitPane.setRightComponent(contactPane);

        outlineViewPanel.hideOutlineView(Bundle.ContactsViewer_noContacts_message());

        proxyLookup = new ModifiableProxyLookup(createLookup(outlineViewPanel.getExplorerManager(), getActionMap()));
        nodeFactory = new ContactsChildNodeFactory(null);

        outline = outlineViewPanel.getOutlineView().getOutline();
        outlineViewPanel.getOutlineView().setPropertyColumns(
                "TSK_EMAIL", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL.getDisplayName(),
                "TSK_PHONE_NUMBER", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getDisplayName()
        );
        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.ContactsViewer_columnHeader_Name());

        outlineViewPanel.hideOutlineView("<No contacts for selected account>");

        outlineViewPanel.getExplorerManager().addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                final Node[] nodes = outlineViewPanel.getExplorerManager().getSelectedNodes();
                contactPane.setNode(nodes != null && nodes.length > 0 ? nodes[0] : null);
            }
        });

        outlineViewPanel.getExplorerManager().setRootContext(new TableFilterNode(new DataResultFilterNode(new AbstractNode(Children.create(nodeFactory, true)), outlineViewPanel.getExplorerManager()), true));

        // When a new set of nodes are added to the OutlineView the childrenAdded
        // seems to be fired before the childrenRemoved. 
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

        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);
    }

    @Override
    public String getDisplayName() {
        return Bundle.ContactsViewer_tabTitle();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setSelectionInfo(SelectionInfo info) {
        contactPane.setNode(null);
        nodeFactory.refresh(info);
    }

    @Override
    public Lookup getLookup() {
        return proxyLookup;
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
        if (isDescendingFrom(newFocusOwner, contactPane)) {
            //if the focus owner is within the MessageContentViewer (the attachments table)
            proxyLookup.setNewLookups(createLookup(contactPane.getExplorerManager(), getActionMap()));
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
            outlineViewPanel.hideOutlineView(Bundle.ContactsViewer_noContacts_message());
        } else {
            outlineViewPanel.showOutlineView();
        }
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

        setLayout(new java.awt.GridBagLayout());

        splitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitPane.setLeftComponent(outlineViewPanel);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(splitPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel outlineViewPanel;
    private javax.swing.JSplitPane splitPane;
    // End of variables declaration//GEN-END:variables
}
