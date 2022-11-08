/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.osaccount;

import java.awt.BorderLayout;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.contentviewers.utils.ViewerPriority;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * DataContentViewer for OsAccounts.
 */
@ServiceProvider(service = DataContentViewer.class, position = 5)
public class OsAccountViewer extends javax.swing.JPanel implements DataContentViewer {

    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(OsAccountViewer.class.getName());

    private final OsAccountDataPanel dataPanel = new OsAccountDataPanel();

    /**
     * Creates new form OsAccountViewer
     */
    public OsAccountViewer() {
        initComponents();

        mainScrollPane.setViewportView(dataPanel);
    }

    @Override
    public void setNode(Node node) {
        Long osAccountId = null;
        try {
            OsAccount osAccount = node.getLookup().lookup(OsAccount.class);
            if (osAccount != null) {
                dataPanel.setOsAccount(osAccount);
                return;
            }
            
            Optional<Long> optional;
            AbstractFile file = node.getLookup().lookup(AbstractFile.class);
            if (file != null) {
                optional = file.getOsAccountObjectId();
                if (optional.isPresent()) {
                    osAccountId = optional.get();
                }
            }

            if (osAccountId == null) {
                DataArtifact dataArtifact = node.getLookup().lookup(DataArtifact.class);
                if (dataArtifact != null) {
                    optional = dataArtifact.getOsAccountObjectId();
                    if (optional.isPresent()) {
                        osAccountId = optional.get();
                    }
                }

            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to get OsAccount for node %s", node.getDisplayName()), ex);
        }

        if (osAccountId != null) {
            dataPanel.setOsAccountId(osAccountId);
        }
    }

    @Messages({
        "OsAccountViewer_title=OS Account"
    })
    @Override
    public String getTitle() {
        return Bundle.OsAccountViewer_title();
    }

    @Messages({
        "OsAccountViewer_tooltip=Viewer for Operating System accounts related to the selected node."
    })
    @Override
    public String getToolTip() {
        return Bundle.OsAccountViewer_tooltip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new OsAccountViewer();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        dataPanel.setOsAccount(null);
    }

    @Override
    public boolean isSupported(Node node) {
        AbstractFile file = node.getLookup().lookup(AbstractFile.class);
        OsAccount osAccount = node.getLookup().lookup(OsAccount.class);
        DataArtifact dataArtifact = node.getLookup().lookup(DataArtifact.class);

        try {
            return osAccount != null
                    || (file != null && file.getOsAccountObjectId().isPresent())
                    || (dataArtifact != null && dataArtifact.getOsAccountObjectId().isPresent());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to determine if node %s is Supported for OsAccountViewer", node.getDisplayName()), ex);
            return false;
        }
    }

    @Override
    public int isPreferred(Node node) {
        return ViewerPriority.viewerPriority.LevelOne.getFlag();
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

        mainScrollPane = new javax.swing.JScrollPane();

        setLayout(new java.awt.GridBagLayout());

        mainScrollPane.setPreferredSize(new java.awt.Dimension(200, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(mainScrollPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane mainScrollPane;
    // End of variables declaration//GEN-END:variables

}
