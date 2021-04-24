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

import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.contentviewers.artifactviewers.GeneralPurposeArtifactViewer;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Panel to display the entire mini timeline feature.
 */
final class MiniTimelinePanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final MiniTimelineDateListPanel dateListPanel = new MiniTimelineDateListPanel();
    private final MiniTimelineArtifactListPanel artifactListPanel = new MiniTimelineArtifactListPanel();
    private DomainArtifactsTabPanel.ArtifactRetrievalStatus status = DomainArtifactsTabPanel.ArtifactRetrievalStatus.UNPOPULATED;
    private AbstractArtifactDetailsPanel rightPanel = new GeneralPurposeArtifactViewer();
    private String selectedDomain = null;
    private final ListSelectionListener artifactListener;
    private final ListSelectionListener dateListener;
    private int leftSplitPaneDividerLocation = 125;
    private int mainSplitPaneDividerLocation = 300;
    private final PropertyChangeListener dividerListener;

    @NbBundle.Messages({"MiniTimelinePanel.loadingPanel.details=the Timeline view"})
    /**
     * Creates new form MiniTimelinePanel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    MiniTimelinePanel() {
        initComponents();
        artifactListPanel.addMouseListener(new ArtifactMenuMouseAdapter(artifactListPanel));
        artifactListener = new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    BlackboardArtifact artifact = artifactListPanel.getSelectedArtifact();
                    if (artifact != null && (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_CACHE.getTypeID()
                            || artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID())) {
                        rightPanel = new ContentViewerDetailsPanel();
                    } else {
                        rightPanel = new GeneralPurposeArtifactViewer();
                    }
                    leftSplitPane.removePropertyChangeListener(dividerListener);
                    mainSplitPane.removePropertyChangeListener(dividerListener);
                    mainSplitPane.setRightComponent(rightPanel.getComponent());
                    rightPanel.setArtifact(artifact);
                    mainSplitPane.setDividerLocation(mainSplitPaneDividerLocation);
                    leftSplitPane.setDividerLocation(leftSplitPaneDividerLocation);
                    mainSplitPane.setDividerLocation(mainSplitPaneDividerLocation);
                    leftSplitPane.setDividerLocation(leftSplitPaneDividerLocation);
                    leftSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
                    mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
                    validate();
                    repaint();
                }
            }
        };
        dividerListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equalsIgnoreCase(JSplitPane.DIVIDER_LOCATION_PROPERTY)
                        && evt.getNewValue() instanceof Integer
                        && evt.getOldValue() instanceof Integer
                        && (JSplitPane.UNDEFINED_CONDITION != (int) evt.getNewValue())) {
                    if (evt.getSource().equals(leftSplitPane)) {
                        leftSplitPaneDividerLocation = (int) evt.getNewValue();
                    } else if (evt.getSource().equals(mainSplitPane)) {
                        mainSplitPaneDividerLocation = (int) evt.getNewValue();
                    }
                }
            }
        };
        dateListener = new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (!event.getValueIsAdjusting()) {
                    artifactListPanel.removeSelectionListener(artifactListener);
                    leftSplitPane.removePropertyChangeListener(dividerListener);
                    mainSplitPane.removePropertyChangeListener(dividerListener);
                    artifactListPanel.clearList();
                    artifactListPanel.addArtifacts(dateListPanel.getArtifactsForSelectedDate());
                    artifactListPanel.addSelectionListener(artifactListener);
                    artifactListPanel.selectFirst();
                    mainSplitPane.setDividerLocation(mainSplitPaneDividerLocation);
                    leftSplitPane.setDividerLocation(leftSplitPaneDividerLocation);
                    leftSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
                    mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
                    validate();
                    repaint();
                }
            }
        };
        dateListPanel.addSelectionListener(dateListener);
        artifactListPanel.addSelectionListener(artifactListener);
        dateListPanel.setPreferredSize(new Dimension(100, 20));
        leftSplitPane.setLeftComponent(dateListPanel);
        artifactListPanel.setPreferredSize(new Dimension(100, 20));
        leftSplitPane.setRightComponent(artifactListPanel);
        mainSplitPane.setRightComponent(rightPanel.getComponent());
        add(mainSplitPane);
        leftSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
        mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
        leftSplitPaneDividerLocation = leftSplitPane.getDividerLocation();
        mainSplitPaneDividerLocation = mainSplitPane.getDividerLocation();
    }

    /**
     * Get the status of the panel which indicates if it is populated.
     *
     * @return The ArtifactRetrievalStatus of the panel.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    DomainArtifactsTabPanel.ArtifactRetrievalStatus getStatus() {
        return status;
    }

    /**
     * Assign the focus to this panel's list.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void focusList() {
        dateListPanel.focusList();
    }

    /**
     * Manually set the status of the panel.
     *
     * @param status The ArtifactRetrievalStatus of the panel
     * @param domain The domain the panel is currently reflecting.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void setStatus(DomainArtifactsTabPanel.ArtifactRetrievalStatus status, String domain) {
        this.status = status;
        this.selectedDomain = domain;
        if (status == DomainArtifactsTabPanel.ArtifactRetrievalStatus.UNPOPULATED) {
            artifactListPanel.clearList();
            dateListPanel.clearList();
            removeAll();
            add(mainSplitPane);
            if (rightPanel != null) {
                rightPanel.setArtifact(null);
            }
        } else if (status == DomainArtifactsTabPanel.ArtifactRetrievalStatus.POPULATING) {
            removeAll();
            add(new LoadingPanel(Bundle.MiniTimelinePanel_loadingPanel_details()));
        }
    }

    /**
     * Handle the event which indicates the artifacts have been retrieved.
     *
     * @param miniTimelineResultEvent The event which indicates the artifacts
     *                                have been retrieved.
     */
    @Subscribe
    void handleMiniTimelineResultEvent(DiscoveryEventUtils.MiniTimelineResultEvent miniTimelineResultEvent) {
        SwingUtilities.invokeLater(() -> {
            if (miniTimelineResultEvent.getDomain().equals(selectedDomain)) {
                dateListPanel.removeListSelectionListener(dateListener);
                artifactListPanel.removeSelectionListener(artifactListener);
                leftSplitPane.removePropertyChangeListener(dividerListener);
                mainSplitPane.removePropertyChangeListener(dividerListener);
                dateListPanel.addArtifacts(miniTimelineResultEvent.getResultList());
                status = DomainArtifactsTabPanel.ArtifactRetrievalStatus.POPULATED;
                setEnabled(!dateListPanel.isEmpty());
                dateListPanel.addSelectionListener(dateListener);
                artifactListPanel.addSelectionListener(artifactListener);
                dateListPanel.selectFirst();
                if (miniTimelineResultEvent.shouldGrabFocus()) {
                    focusList();
                }
                removeAll();
                add(mainSplitPane);
                mainSplitPane.setDividerLocation(mainSplitPaneDividerLocation);
                leftSplitPane.setDividerLocation(leftSplitPaneDividerLocation);
                leftSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
                mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerListener);
                revalidate();
                repaint();
            }
        });
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
        leftSplitPane = new javax.swing.JSplitPane();

        mainSplitPane.setDividerLocation(mainSplitPaneDividerLocation);
        mainSplitPane.setResizeWeight(0.2);
        mainSplitPane.setToolTipText("");
        mainSplitPane.setMinimumSize(new java.awt.Dimension(0, 0));

        leftSplitPane.setDividerLocation(leftSplitPaneDividerLocation);
        leftSplitPane.setResizeWeight(0.5);
        leftSplitPane.setMinimumSize(new java.awt.Dimension(0, 0));
        mainSplitPane.setLeftComponent(leftSplitPane);

        setMinimumSize(new java.awt.Dimension(0, 0));
        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane leftSplitPane;
    private javax.swing.JSplitPane mainSplitPane;
    // End of variables declaration//GEN-END:variables
}
