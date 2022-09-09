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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import static javax.swing.SwingUtilities.isDescendingFrom;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import static org.openide.explorer.ExplorerUtils.createLookup;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.Node.PropertySet;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.communications.ModifiableProxyLookup;
import org.sleuthkit.autopsy.communications.Utils;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The main panel for the messages tab of the RelationshipViewer
 *
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class MessageViewer extends JPanel implements RelationshipsViewer {

    private static final Logger logger = Logger.getLogger(MessageViewer.class.getName());
    private static final long serialVersionUID = 1L;

    private final ModifiableProxyLookup proxyLookup;
    private PropertyChangeListener focusPropertyListener;
    private final ThreadChildNodeFactory rootMessageFactory;
    private final MessagesChildNodeFactory threadMessageNodeFactory;

    private SelectionInfo currentSelectionInfo = null;

    private OutlineViewPanel currentPanel;

    @Messages({
        "MessageViewer_tabTitle=Messages",
        "MessageViewer_columnHeader_From=From",
        "MessageViewer_columnHeader_Date=Date",
        "MessageViewer_columnHeader_To=To",
        "MessageViewer_columnHeader_EarlyDate=Earliest Message",
        "MessageViewer_columnHeader_Subject=Subject",
        "MessageViewer_columnHeader_Attms=Attachments",
        "MessageViewer_no_messages=<No messages found for selected account>",
        "MessageViewer_viewMessage_all=All",
        "MessageViewer_viewMessage_selected=Selected",
        "MessageViewer_viewMessage_unthreaded=Unthreaded",
        "MessageViewer_viewMessage_calllogs=Call Logs"})

    /**
     * Creates new form MessageViewer
     */
    MessageViewer() {

        initComponents();
        currentPanel = rootTablePane;
        proxyLookup = new ModifiableProxyLookup(createLookup(rootTablePane.getExplorerManager(), getActionMap()));
        rootMessageFactory = new ThreadChildNodeFactory(new ShowThreadMessagesAction());
        threadMessageNodeFactory = new MessagesChildNodeFactory();

        rootTablePane.getExplorerManager().setRootContext(
                new AbstractNode(Children.create(rootMessageFactory, true)));

        rootTablePane.getOutlineView().setPopupAllowed(false);

        Outline outline = rootTablePane.getOutlineView().getOutline();
        rootTablePane.getOutlineView().setPropertyColumns(
                "Date", Bundle.MessageViewer_columnHeader_EarlyDate(),
                "Subject", Bundle.MessageViewer_columnHeader_Subject()
        );
        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel("Type");
        outline.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

        rootTablePane.getExplorerManager().addPropertyChangeListener((PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(ExplorerManager.PROP_SELECTED_NODES)) {
                showSelectedThread();
            }
        });
        
        rootTablePane.getOutlineView().getOutline().getModel().addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                Utils.setColumnWidths(rootTablePane.getOutlineView().getOutline());
            }
        });

        threadMessagesPanel.setChildFactory(threadMessageNodeFactory);

        rootTablePane.setTableColumnsWidth(10, 20, 70);

        Image image = getScaledImage((new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/timeline/images/arrow-180.png"))).getImage(), 16, 16);
        backButton.setIcon(new ImageIcon(image));
    }

    @Override
    public String getDisplayName() {
        return Bundle.MessageViewer_tabTitle();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setSelectionInfo(SelectionInfo info) {
        if(currentSelectionInfo != null && currentSelectionInfo.equals(info)) {
            try {
                // Clear the currently selected thread so that clicks can 
                // be registered.
                rootTablePane.getExplorerManager().setSelectedNodes(new Node[0]);
            } catch (PropertyVetoException ex) {
                logger.log(Level.WARNING, "Error clearing the selected node", ex);
            }
        } else {
            currentSelectionInfo = info;
            rootMessageFactory.refresh(info);
        }

        currentPanel = rootTablePane;

        CardLayout layout = (CardLayout) this.getLayout();
        layout.show(this, "threads"); 
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
                .addPropertyChangeListener("focusOwner", focusPropertyListener);
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
        if (isDescendingFrom(newFocusOwner, rootTablePane)) {
            proxyLookup.setNewLookups(createLookup(rootTablePane.getExplorerManager(), getActionMap()));
        } else if (isDescendingFrom(newFocusOwner, this)) {
            proxyLookup.setNewLookups(createLookup(threadMessagesPanel.getExplorerManager(), getActionMap()));
        } 
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("focusOwner", focusPropertyListener);
    }

    @SuppressWarnings("rawtypes")
    private void showSelectedThread() {
        final Node[] nodes = rootTablePane.getExplorerManager().getSelectedNodes();

        if (nodes == null) {
            return;
        }

        if (nodes.length == 0 || nodes.length > 1) {
            return;
        }

        ArrayList<String> threadIDList = new ArrayList<>();
        String subject = "";

        PropertySet[] propertySets = nodes[0].getPropertySets();
        for (PropertySet pset : propertySets) {
            Property[] properties = pset.getProperties();
            for (Property prop : properties) {
                if (prop.getName().equalsIgnoreCase("threadid")) {
                    try {
                        String threadID = prop.getValue().toString();
                        if (!threadIDList.contains(threadID)) {
                            threadIDList.add(threadID);
                        }
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        logger.log(Level.WARNING, String.format("Unable to get threadid for node: %s", nodes[0].getDisplayName()), ex);
                    }
                } else if (prop.getName().equalsIgnoreCase("subject")) {
                    try {
                        subject = prop.getValue().toString();
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        logger.log(Level.WARNING, String.format("Unable to get subject for node: %s", nodes[0].getDisplayName()), ex);
                        subject = "<unavailable>";
                    }
                }
            }

        }

        if (!threadIDList.isEmpty()) {
            threadMessageNodeFactory.refresh(currentSelectionInfo, threadIDList);

            if (!subject.isEmpty()) {
                threadNameLabel.setText(subject);
            } else {
                threadNameLabel.setText(Bundle.MessageViewer_viewMessage_unthreaded());
            }

            showMessagesPane();
        }
    }

    /**
     * Make the threads pane visible.
     */
    private void showThreadsPane() {
        switchCard("threads");
    }

    /**
     * Make the message pane visible.
     */
    private void showMessagesPane() {
        switchCard("messages");
    }

    /**
     * Changes the visible panel (card).
     *
     * @param cardName Name of card to show
     */
    private void switchCard(String cardName) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                CardLayout layout = (CardLayout) getLayout();
                layout.show(MessageViewer.this, cardName);
            }
        });
    }

    /**
     * Scales the given image to the given width and height.
     *
     * @param srcImg Image to scale
     * @param w      Image width
     * @param h      Image height
     *
     * @return Scaled version of srcImg
     */
    private Image getScaledImage(Image srcImg, int w, int h) {
        BufferedImage resizedImg = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImg.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(srcImg, 0, 0, w, h, null);
        g2.dispose();

        return resizedImg;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.'
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        rootMessagesPane = new javax.swing.JPanel();
        threadsLabel = new javax.swing.JLabel();
        showAllButton = new javax.swing.JButton();
        rootTablePane = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();
        messagePanel = new javax.swing.JPanel();
        threadMessagesPanel = new MessagesPanel();
        backButton = new javax.swing.JButton();
        showingMessagesLabel = new javax.swing.JLabel();
        threadNameLabel = new javax.swing.JLabel();

        setMinimumSize(new java.awt.Dimension(450, 292));
        setName(""); // NOI18N
        setLayout(new java.awt.CardLayout());

        rootMessagesPane.setOpaque(false);
        rootMessagesPane.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(threadsLabel, org.openide.util.NbBundle.getMessage(MessageViewer.class, "MessageViewer.threadsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(15, 15, 9, 0);
        rootMessagesPane.add(threadsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(showAllButton, org.openide.util.NbBundle.getMessage(MessageViewer.class, "MessageViewer.showAllButton.text")); // NOI18N
        showAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showAllButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 15, 0);
        rootMessagesPane.add(showAllButton, gridBagConstraints);

        rootTablePane.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 9, 15);
        rootMessagesPane.add(rootTablePane, gridBagConstraints);

        add(rootMessagesPane, "threads");

        messagePanel.setMinimumSize(new java.awt.Dimension(450, 292));
        messagePanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
        messagePanel.add(threadMessagesPanel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(backButton, org.openide.util.NbBundle.getMessage(MessageViewer.class, "MessageViewer.backButton.text")); // NOI18N
        backButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 9, 15);
        messagePanel.add(backButton, gridBagConstraints);
        backButton.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(MessageViewer.class, "MessageViewer.backButton.AccessibleContext.accessibleDescription")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(showingMessagesLabel, org.openide.util.NbBundle.getMessage(MessageViewer.class, "MessageViewer.showingMessagesLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(9, 15, 5, 0);
        messagePanel.add(showingMessagesLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(threadNameLabel, org.openide.util.NbBundle.getMessage(MessageViewer.class, "MessageViewer.threadNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 5, 5, 15);
        messagePanel.add(threadNameLabel, gridBagConstraints);

        add(messagePanel, "messages");
    }// </editor-fold>//GEN-END:initComponents

    private void backButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backButtonActionPerformed
        try {
            rootTablePane.getExplorerManager().setSelectedNodes(new Node[0]);
        } catch (PropertyVetoException ex) {
            logger.log(Level.WARNING, "Error setting selected nodes", ex);
        }
        showThreadsPane();
    }//GEN-LAST:event_backButtonActionPerformed

    private void showAllButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showAllButtonActionPerformed
        threadMessageNodeFactory.refresh(currentSelectionInfo, null);
        threadNameLabel.setText("All Messages");

        showMessagesPane();
    }//GEN-LAST:event_showAllButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton backButton;
    private javax.swing.JPanel messagePanel;
    private javax.swing.JPanel rootMessagesPane;
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel rootTablePane;
    private javax.swing.JButton showAllButton;
    private javax.swing.JLabel showingMessagesLabel;
    private org.sleuthkit.autopsy.communications.relationships.MessagesPanel threadMessagesPanel;
    private javax.swing.JLabel threadNameLabel;
    private javax.swing.JLabel threadsLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * The preferred action of the table nodes.
     */
    class ShowThreadMessagesAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showSelectedThread();
                }
            });
        }
    }
}
