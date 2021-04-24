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
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
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
    private int dividerLocation = 300;
    private final PropertyChangeListener dividerListener;

    private ArtifactRetrievalStatus status = ArtifactRetrievalStatus.UNPOPULATED;
    private final ListSelectionListener listener = new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent event) {
            if (!event.getValueIsAdjusting()) {
                mainSplitPane.removePropertyChangeListener(dividerListener);
                rightPanel.setArtifact(listPanel.getSelectedArtifact());
                mainSplitPane.setDividerLocation(dividerLocation);
                mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
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
        dividerListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equalsIgnoreCase(JSplitPane.DIVIDER_LOCATION_PROPERTY)
                        && evt.getNewValue() instanceof Integer
                        && evt.getOldValue() instanceof Integer
                        && (JSplitPane.UNDEFINED_CONDITION != (int) evt.getNewValue())) {
                    dividerLocation = (int) evt.getNewValue();
                }
            }
        };
        this.artifactType = type;
        listPanel = new ArtifactsListPanel(artifactType);
        listPanel.setPreferredSize(new Dimension(100, 20));
        listPanel.addMouseListener(new ArtifactMenuMouseAdapter(listPanel));

        mainSplitPane.setLeftComponent(listPanel);
        add(mainSplitPane);
        setRightComponent();
        mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
        dividerLocation = mainSplitPane.getDividerLocation();
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
            mainSplitPane.setRightComponent(rightPanel.getComponent());
        }
    }

    /**
     * Assign the focus to this panel's list.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void focusList() {
        listPanel.focusList();
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
        mainSplitPane.removePropertyChangeListener(dividerListener);
        if (status == ArtifactRetrievalStatus.UNPOPULATED) {
            listPanel.clearList();
            removeAll();
            add(mainSplitPane);
            if (rightPanel != null) {
                rightPanel.setArtifact(null);
            }
        } else if (status == ArtifactRetrievalStatus.POPULATING) {
            removeAll();
            add(new LoadingPanel(artifactType.getDisplayName()));
        }
        mainSplitPane.setDividerLocation(dividerLocation);
        mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
    }

    /**
     * Handle the event which indicates the artifacts have been retrieved.
     *
     * @param artifactresultEvent The event which indicates the artifacts have
     *                            been retrieved.
     */
    @Subscribe
    void handleArtifactSearchResultEvent(DiscoveryEventUtils.ArtifactSearchResultEvent artifactresultEvent) {
        if (artifactType == artifactresultEvent.getArtifactType() && status == ArtifactRetrievalStatus.POPULATING) {
            SwingUtilities.invokeLater(() -> {
                mainSplitPane.removePropertyChangeListener(dividerListener);
                listPanel.removeSelectionListener(listener);
                listPanel.addArtifacts(artifactresultEvent.getListOfArtifacts());
                status = ArtifactRetrievalStatus.POPULATED;
                setEnabled(!listPanel.isEmpty());
                listPanel.addSelectionListener(listener);
                listPanel.selectFirst();
                removeAll();
                add(mainSplitPane);
                mainSplitPane.setDividerLocation(dividerLocation);
                mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
                if (artifactresultEvent.shouldGrabFocus()) {
                    focusList();
                }
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

        mainSplitPane = new javax.swing.JSplitPane();

        mainSplitPane.setDividerLocation(dividerLocation);
        mainSplitPane.setResizeWeight(0.2);
        mainSplitPane.setLastDividerLocation(250);

        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        setMinimumSize(new java.awt.Dimension(0, 0));
        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane mainSplitPane;
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
