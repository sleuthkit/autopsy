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
package org.sleuthkit.autopsy.contentviewers.textcontentviewer;

import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.contentviewers.utils.ViewerPriority;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContent;
import org.sleuthkit.autopsy.corecomponentinterfaces.TextViewer;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;

/**
 * A TextContentViewerPanel which displays the content of the TextContentViewer
 * and its TextViewers
 */
public class TextContentViewerPanel extends javax.swing.JPanel implements DataContent, ChangeListener {

    private static final Logger logger = Logger.getLogger(TextContentViewerPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final List<UpdateWrapper> textViewers = new ArrayList<>();
    private Node currentNode;
    private boolean listeningToTabbedPane = false;

    /**
     * Creates new form TextContentViewerPanel
     */
    public TextContentViewerPanel(boolean isMain) {
        initComponents();
        // add all implementors of DataContentViewer and put them in the tabbed pane
        Collection<? extends TextViewer> dcvs = Lookup.getDefault().lookupAll(TextViewer.class);
        for (TextViewer factory : dcvs) {
            TextViewer dcv;
            if (isMain) {
                //use the instance from Lookup for the main viewer
                dcv = factory;
            } else {
                dcv = factory.createInstance();
            }
            textViewers.add(new UpdateWrapper(dcv));
            javax.swing.JScrollPane scrollTab = new javax.swing.JScrollPane(dcv.getComponent());
            scrollTab.setVerticalScrollBarPolicy(javax.swing.JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            textViewerTabbedPane.addTab(dcv.getTitle(), null,
                    scrollTab, dcv.getToolTip());
        }

        // disable the tabs
        int numTabs = textViewerTabbedPane.getTabCount();
        for (int tab = 0; tab < numTabs; ++tab) {
            textViewerTabbedPane.setEnabledAt(tab, false);
        }
    }

    /**
     * Determine whether the content viewer which displays this panel
     * isSupported. This panel is supported if any of the TextViewer's displayed
     * in it are supported.
     *
     * @param node
     *
     * @return true if any of the TextViewers are supported, false otherwise
     */
    boolean isSupported(Node node) {
        for (UpdateWrapper textViewer : textViewers) {
            if (textViewer.isSupported(node)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine the isPreffered score for the content viewer which is
     * displaying this panel. Score is depenedent on the score of the supported
     * TextViewers which exist.
     *
     * @param node
     *
     * @return the greatest isPreffered value of the supported TextViewers
     */
    int isPreffered(Node node) {
        int max = ViewerPriority.viewerPriority.LevelOne.getFlag();
        for (UpdateWrapper textViewer : textViewers) {
            if (textViewer.isSupported(node)) {
                max = Integer.max(max, textViewer.isPreferred(node));
            }
        }
        return max;
    }

    @Messages({"TextContentViewerPanel.defaultName=Text"})
    @Override
    public void setNode(Node selectedNode) {
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {

            String defaultName = Bundle.TextContentViewerPanel_defaultName();
            // set the file path
            if (selectedNode == null) {
                setName(defaultName);
            } else {
                Content content = selectedNode.getLookup().lookup(Content.class);
                if (content != null) {
                    String path = defaultName;
                    try {
                        path = content.getUniquePath();
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "Exception while calling Content.getUniquePath() for " + content.toString(), ex); //NON-NLS
                    }
                    setName(path);
                } else {
                    setName(defaultName);
                }
            }

            currentNode = selectedNode;

            setupTabs(selectedNode);
        } finally {
            this.setCursor(null);
        }
    }

    /**
     * Resets the tabs based on the selected Node. If the selected node is null
     * or not supported, disable that tab as well.
     *
     * @param selectedNode the selected content Node
     */
    public void setupTabs(Node selectedNode) {
        // Deferring becoming a listener to the tabbed pane until this point
        // eliminates handling a superfluous stateChanged event during construction.
        if (listeningToTabbedPane == false) {
            textViewerTabbedPane.addChangeListener(this);
            listeningToTabbedPane = true;
        }

        int currTabIndex = textViewerTabbedPane.getSelectedIndex();
        int totalTabs = textViewerTabbedPane.getTabCount();
        int maxPreferred = 0;
        int preferredViewerIndex = 0;
        for (int i = 0; i < totalTabs; ++i) {
            UpdateWrapper dcv = textViewers.get(i);
            dcv.resetComponent();

            // disable an unsupported tab (ex: picture viewer)
            if ((selectedNode == null) || (dcv.isSupported(selectedNode) == false)) {
                textViewerTabbedPane.setEnabledAt(i, false);
            } else {
                textViewerTabbedPane.setEnabledAt(i, true);

                // remember the viewer with the highest preference value
                int currentPreferred = dcv.isPreferred(selectedNode);
                if (currentPreferred > maxPreferred) {
                    preferredViewerIndex = i;
                    maxPreferred = currentPreferred;
                }
            }
        }

        // let the user decide if we should stay with the current viewer
        int tabIndex = UserPreferences.keepPreferredContentViewer() ? currTabIndex : preferredViewerIndex;

        UpdateWrapper dcv = textViewers.get(tabIndex);
        // this is really only needed if no tabs were enabled 
        if (textViewerTabbedPane.isEnabledAt(tabIndex) == false) {
            dcv.resetComponent();
        } else {
            dcv.setNode(selectedNode);
        }

        // set the tab to the one the user wants, then set that viewer's node.
        textViewerTabbedPane.setSelectedIndex(tabIndex);
        textViewerTabbedPane.getSelectedComponent().repaint();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        //does nothing
    }

    @Override
    public void stateChanged(ChangeEvent evt) {
        JTabbedPane pane = (JTabbedPane) evt.getSource();

        // Get and set current selected tab
        int currentTab = pane.getSelectedIndex();
        if (currentTab != -1 && pane.isEnabledAt(currentTab)) {
            UpdateWrapper dcv = textViewers.get(currentTab);
            if (dcv.isOutdated()) {
                // change the cursor to "waiting cursor" for this operation
                this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                try {
                    dcv.setNode(currentNode);
                } finally {
                    this.setCursor(null);
                }
            }
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

        textViewerTabbedPane = new javax.swing.JTabbedPane();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 50, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(textViewerTabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 50, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 27, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(textViewerTabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 27, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTabbedPane textViewerTabbedPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Class to assist in keeping track of which TextViewers need to be updated
     */
    private static class UpdateWrapper {

        private final TextViewer wrapped;
        private boolean outdated;

        UpdateWrapper(TextViewer wrapped) {
            this.wrapped = wrapped;
            this.outdated = true;
        }

        void setNode(Node selectedNode) {
            this.wrapped.setNode(selectedNode);
            this.outdated = false;
        }

        void resetComponent() {
            this.wrapped.resetComponent();
            this.outdated = true;
        }

        boolean isOutdated() {
            return this.outdated;
        }

        boolean isSupported(Node node) {
            return this.wrapped.isSupported(node);
        }

        int isPreferred(Node node) {
            return this.wrapped.isPreferred(node);
        }
    }
}
