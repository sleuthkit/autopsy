/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import org.sleuthkit.autopsy.contentviewers.artifactviewers.GeneralPurposeArtifactViewer;
import com.google.common.eventbus.Subscribe;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.sleuthkit.autopsy.contentviewers.artifactviewers.DefaultTableArtifactContentViewer;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * JPanel which should be used as a tab in the domain artifacts details area.
 */
final class DomainArtifactsTabPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(DomainArtifactsTabPanel.class.getName());
    private final ArtifactsListPanel listPanel;
    private final BlackboardArtifact.ARTIFACT_TYPE artifactType;
    private AbstractArtifactDetailsPanel rightPanel = null;

    private ArtifactRetrievalStatus status = ArtifactRetrievalStatus.UNPOPULATED;
    private final ListSelectionListener listener = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent event) {
            if (!event.getValueIsAdjusting()) {
                rightPanel.setArtifact(listPanel.getSelectedArtifact());
            }
        }
    };

    /**
     * Creates new form CookiesPanel
     *
     * @param type The type of Artifact this tab is displaying information for.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    DomainArtifactsTabPanel(BlackboardArtifact.ARTIFACT_TYPE type) {
        initComponents();
        this.artifactType = type;
        listPanel = new ArtifactsListPanel(artifactType);
        listPanel.addMouseListener(new ArtifactMenuMouseAdapter(listPanel));
        jSplitPane1.setLeftComponent(listPanel);
        setRightComponent();
        listPanel.addSelectionListener(listener);
    }

    /**
     * Set the right component of the tab panel, which will display the details
     * for the artifact.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setRightComponent() {
        switch (artifactType) {
            case TSK_WEB_HISTORY:
            case TSK_WEB_COOKIE:
            case TSK_WEB_SEARCH_QUERY:
            case TSK_WEB_BOOKMARK:
                rightPanel = new GeneralPurposeArtifactViewer();
                break;
            case TSK_WEB_DOWNLOAD:
            case TSK_WEB_CACHE:
                rightPanel = new ContentViewerDetailsPanel();
                break;
            default:
                rightPanel = new DefaultTableArtifactContentViewer();
                break;
        }
        if (rightPanel != null) {
            jSplitPane1.setRightComponent(rightPanel.getComponent());
        }
    }

    /**
     * Get the status of the panel which indicates if it is populated.
     *
     * @return The ArtifactRetrievalStatus of the panel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    ArtifactRetrievalStatus getStatus() {
        return status;
    }

    /**
     * Manually set the status of the panel.
     *
     * @param status The ArtifactRetrievalStatus of the panel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void setStatus(ArtifactRetrievalStatus status) {
        this.status = status;
        if (status == ArtifactRetrievalStatus.UNPOPULATED) {
            listPanel.clearList();
            if (rightPanel != null){
                rightPanel.setArtifact(null);
            }
                
        }
    }

    /**
     * Handle the event which indicates the artifacts have been retrieved.
     *
     * @param artifactresultEvent The event which indicates the artifacts have
     *                            been retrieved.
     */
    @Subscribe
    void handleArtifactSearchResultEvent(DiscoveryEventUtils.ArtifactSearchResultEvent artifactresultEvent) {
        if (artifactType == artifactresultEvent.getArtifactType()) {
            SwingUtilities.invokeLater(() -> {
                listPanel.removeSelectionListener(listener);
                listPanel.addArtifacts(artifactresultEvent.getListOfArtifacts());
                status = ArtifactRetrievalStatus.POPULATED;
                setEnabled(!listPanel.isEmpty());
                listPanel.addSelectionListener(listener);
                listPanel.selectFirst();
                revalidate();
                repaint();
                try {
                    DiscoveryEventUtils.getDiscoveryEventBus().unregister(this);
                } catch (IllegalArgumentException notRegistered) {
                    logger.log(Level.INFO, "Attempting to unregister tab which was not registered");
                    // attempting to remove a tab that was never registered
                }
            });
        }
    }

    /**
     * Get the type of Artifact the panel exists for.
     *
     * @return The ARTIFACT_TYPE of the BlackboardArtifact being displayed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    BlackboardArtifact.ARTIFACT_TYPE getArtifactType() {
        return artifactType;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();

        setMinimumSize(new java.awt.Dimension(0, 0));
        setPreferredSize(new java.awt.Dimension(0, 0));
        setLayout(new java.awt.BorderLayout());

        jSplitPane1.setMinimumSize(new java.awt.Dimension(0, 0));
        jSplitPane1.setPreferredSize(new java.awt.Dimension(0, 0));
        add(jSplitPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane jSplitPane1;
    // End of variables declaration//GEN-END:variables

    /**
     * Enum to keep track of the populated state of this panel.
     */
    enum ArtifactRetrievalStatus {
        UNPOPULATED(),
        POPULATING(),
        POPULATED();
    }

}
