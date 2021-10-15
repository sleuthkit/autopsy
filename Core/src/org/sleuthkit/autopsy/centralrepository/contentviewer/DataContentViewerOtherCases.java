/*
 * Central Repository
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import java.awt.Component;
import java.awt.Cursor;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JPanel;
import org.apache.commons.lang.StringUtils;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.contentviewers.utils.ViewerPriority;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactItem;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.OsAccount;

/**
 * View correlation results from other cases
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 10)
@Messages({"DataContentViewerOtherCases.title=Other Occurrences",
    "DataContentViewerOtherCases.toolTip=Displays instances of the selected file/artifact from other occurrences."})
public final class DataContentViewerOtherCases extends JPanel implements DataContentViewer {

    private static final long serialVersionUID = -1L;
    private static final Logger logger = Logger.getLogger(DataContentViewerOtherCases.class.getName());
    private final OtherOccurrencesPanel otherOccurrencesPanel = new OtherOccurrencesPanel();

    private OtherOccurrencesNodeWorker worker = null;

    /**
     * Creates new form DataContentViewerOtherCases
     */
    public DataContentViewerOtherCases() {
        initComponents();
        add(otherOccurrencesPanel);
    }

    @Override
    public String getTitle() {
        return Bundle.DataContentViewerOtherCases_title();
    }

    @Override
    public String getToolTip() {
        return Bundle.DataContentViewerOtherCases_toolTip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerOtherCases();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public void resetComponent() {
        otherOccurrencesPanel.reset();
    }

    @Override
    public int isPreferred(Node node) {
        return ViewerPriority.viewerPriority.LevelOne.getFlag();
    }

    @Override
    public boolean isSupported(Node node) {
        //Ideally we would want to attempt to create correlation attributes for the node contents
        //and if none could be created determine that it was not supported.
        //However that winds up being more work than we really want to be performing in this method so we perform a quicker check.
        //The result of this is that the Other Occurrences viewer could be enabled but without any correlation attributes in some situations.
        // Is supported if:
        // The central repo is enabled and the node is not null
        if (CentralRepository.isEnabled() && node != null) {
            // And the node has information which could be correlated on.
            if (node.getLookup().lookup(OsAccount.class) != null) {
                //the node has an associated OsAccount to correlate on
                return true;
            }
            if (node.getLookup().lookup(BlackboardArtifactItem.class) != null) {
                //it has a blackboard artifact which might have a correlation attribute
                return true;
            }
            if (node.getLookup().lookup(BlackboardArtifactTag.class) != null) {
                //Blackboard artifact tags may have their underlying artifact correlated on
                return true;
            }
            AbstractFile file = node.getLookup().lookup(AbstractFile.class);
            //the AbstractFile lookup will handle the usecase for file tags as well
            if (file != null && !StringUtils.isBlank(file.getMd5Hash())) {
                //there is an abstractFile lookup and it has an MD5 so could be correlated on
                return true;
            }
        }
        return false;

    }

    @Override
    public void setNode(Node node) {
        otherOccurrencesPanel.reset(); // reset the table to empty.
        otherOccurrencesPanel.showPanelLoadingMessage();

        if (node == null) {
            return;
        }

        if (worker != null) {
            worker.cancel(true);
        }
        worker = new OtherOccurrencesNodeWorker(node) {
            @Override
            public void done() {
                try {
                    if (!isCancelled()) {
                        OtherOccurrencesData data = get();
                        otherOccurrencesPanel.populateTable(data);
                        otherOccurrencesPanel.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    DataContentViewerOtherCases.logger.log(Level.SEVERE, "Failed to update OtherOccurrencesPanel", ex);
                }
            }
        };
        otherOccurrencesPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        worker.execute();
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

        setMinimumSize(new java.awt.Dimension(1000, 10));
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(1000, 63));
        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
