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
import java.awt.Component;
import java.util.logging.Level;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.contentviewer.OtherOccurrencesPanel;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.autopsy.discovery.search.SearchData;

/**
 * Panel to display details area for domain discovery results.
 *
 */
final class DomainDetailsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DomainDetailsPanel.class.getName());
    private ArtifactsWorker singleArtifactDomainWorker;
    private String domain;
    private String selectedTabName = null;

    /**
     * Creates new form ArtifactDetailsPanel.
     *
     * @param selectedTabName The name of the tab to select initially.
     */
    @NbBundle.Messages({"DomainDetailsPanel.otherOccurrencesTab.title=Other Occurrences"})
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    DomainDetailsPanel() {
        initComponents();
        MiniTimelinePanel timelinePanel = new MiniTimelinePanel();
        DiscoveryEventUtils.getDiscoveryEventBus().register(timelinePanel);
        jTabbedPane1.add(Bundle.DomainDetailsPanel_miniTimelineTitle_text(), timelinePanel);
        for (BlackboardArtifact.ARTIFACT_TYPE type : SearchData.Type.DOMAIN.getArtifactTypes()) {
            jTabbedPane1.add(type.getDisplayName(), new DomainArtifactsTabPanel(type));
        }
        if (CentralRepository.isEnabled()) {
            jTabbedPane1.add(Bundle.DomainDetailsPanel_otherOccurrencesTab_title(), new OtherOccurrencesPanel());
        }
    }

    /**
     * Configure the tabs for each of the artifact types which we will be
     * displaying.
     *
     * @param tabName The name of the tab to select initially.
     */
    @NbBundle.Messages({"DomainDetailsPanel.miniTimelineTitle.text=Timeline"})
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void configureArtifactTabs(String tabName) {
        selectedTabName = tabName;
        if (StringUtils.isBlank(selectedTabName)) {
            selectedTabName = Bundle.DomainDetailsPanel_miniTimelineTitle_text();
        }
        selectTab();
        jTabbedPane1.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (jTabbedPane1.getSelectedIndex() >= 0) {
                    String newTabTitle = jTabbedPane1.getTitleAt(jTabbedPane1.getSelectedIndex());
                    if (selectedTabName == null || !selectedTabName.equals(newTabTitle)) {
                        selectedTabName = newTabTitle;
                        Component selectedComponent = jTabbedPane1.getSelectedComponent();
                        if (!StringUtils.isBlank(domain) && selectedComponent instanceof DomainArtifactsTabPanel) {
                            runDomainWorker((DomainArtifactsTabPanel) selectedComponent, true);
                        } else if (!StringUtils.isBlank(domain) && selectedComponent instanceof MiniTimelinePanel) {
                            runMiniTimelineWorker((MiniTimelinePanel) selectedComponent, true);
                        } else if (selectedComponent instanceof OtherOccurrencesPanel) {
                            if (CentralRepository.isEnabled()) {
                                try {
                                    ((OtherOccurrencesPanel) selectedComponent).populateTableForOneType(CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.DOMAIN_TYPE_ID), domain);
                                } catch (CentralRepoException ex) {
                                    logger.log(Level.INFO, "Central repository exception while trying to get instances by type and value for domain: " + domain, ex);
                                    ((OtherOccurrencesPanel) selectedComponent).reset();
                                }
                            } else {
                                ((OtherOccurrencesPanel) selectedComponent).reset();
                            }
                        }
                    }
                }
            }
        });
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    /**
     * Set the selected tab index to be the previously selected tab if a
     * previously selected tab exists.
     */
    private void selectTab() {
        for (int i = 0; i < jTabbedPane1.getTabCount(); i++) {
            if (!StringUtils.isBlank(selectedTabName) && selectedTabName.equals(jTabbedPane1.getTitleAt(i))) {
                jTabbedPane1.setSelectedIndex(i);
                return;
            }
        }
    }

    /**
     * Get the status of the currently selected tab.
     *
     * @return The loading status of the currently selected tab.
     */
    DomainArtifactsTabPanel.ArtifactRetrievalStatus getCurrentTabStatus() {
        if (jTabbedPane1.getSelectedComponent() instanceof MiniTimelinePanel) {
            return ((MiniTimelinePanel) jTabbedPane1.getSelectedComponent()).getStatus();
        } else if (jTabbedPane1.getSelectedComponent() instanceof DomainArtifactsTabPanel) {
            return ((DomainArtifactsTabPanel) jTabbedPane1.getSelectedComponent()).getStatus();
        }
        return null;
    }

    /**
     * Run the worker which retrieves the list of artifacts for the domain to
     * populate the details area.
     *
     * @param domainArtifactsTabPanel The DomainArtifactsTabPanel which has been
     *                                selected.
     * @param shouldGrabFocus         True if the list of artifacts should have
     *                                focus, false otherwise.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void runDomainWorker(DomainArtifactsTabPanel domainArtifactsTabPanel, boolean shouldGrabFocus) {
        if (singleArtifactDomainWorker != null && !singleArtifactDomainWorker.isDone()) {
            singleArtifactDomainWorker.cancel(true);
        }
        if (domainArtifactsTabPanel.getStatus() == DomainArtifactsTabPanel.ArtifactRetrievalStatus.UNPOPULATED) {
            DiscoveryEventUtils.getDiscoveryEventBus().register(domainArtifactsTabPanel);
            domainArtifactsTabPanel.setStatus(DomainArtifactsTabPanel.ArtifactRetrievalStatus.POPULATING);
            singleArtifactDomainWorker = new ArtifactsWorker(domainArtifactsTabPanel.getArtifactType(), domain, shouldGrabFocus);
            singleArtifactDomainWorker.execute();
        } else if (domainArtifactsTabPanel.getStatus() == DomainArtifactsTabPanel.ArtifactRetrievalStatus.POPULATED) {
            domainArtifactsTabPanel.focusList();
        }

    }

    /**
     * Run the worker which retrieves the list of MiniTimelineResults for the
     * mini timeline view to populate.
     *
     * @param miniTimelinePanel The MiniTimelinePanel which has been selected.
     * @param shouldGrabFocus   True if the list of dates should have focus,
     *                          false otherwise.
     */
    private void runMiniTimelineWorker(MiniTimelinePanel miniTimelinePanel, boolean shouldGrabFocus) {
        if (miniTimelinePanel.getStatus() == DomainArtifactsTabPanel.ArtifactRetrievalStatus.UNPOPULATED) {
            miniTimelinePanel.setStatus(DomainArtifactsTabPanel.ArtifactRetrievalStatus.POPULATING, domain);
            new MiniTimelineWorker(domain, shouldGrabFocus).execute();
        } else if (miniTimelinePanel.getStatus() == DomainArtifactsTabPanel.ArtifactRetrievalStatus.POPULATED) {
            miniTimelinePanel.focusList();
        }
    }

    /**
     * Populate the the details tabs.
     *
     * @param populateEvent The PopulateDomainTabsEvent which indicates which
     *                      domain the details tabs should be populated for.
     */
    @Subscribe
    void handlePopulateDomainTabsEvent(DiscoveryEventUtils.PopulateDomainTabsEvent populateEvent) {
        SwingUtilities.invokeLater(() -> {
            domain = populateEvent.getDomain();
            if (StringUtils.isBlank(domain)) {
                resetTabsStatus();
                //send fade out event
                DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.DetailsVisibleEvent(false));
            } else {
                resetTabsStatus();
                Component selectedComponent = jTabbedPane1.getSelectedComponent();
                if (selectedComponent instanceof DomainArtifactsTabPanel) {
                    runDomainWorker((DomainArtifactsTabPanel) selectedComponent, false);
                } else if (selectedComponent instanceof MiniTimelinePanel) {
                    runMiniTimelineWorker((MiniTimelinePanel) selectedComponent, false);
                } else if (selectedComponent instanceof OtherOccurrencesPanel) {
                    if (CentralRepository.isEnabled()) {
                        try {
                            ((OtherOccurrencesPanel) selectedComponent).populateTableForOneType(CentralRepository.getInstance().getCorrelationTypeById(CorrelationAttributeInstance.DOMAIN_TYPE_ID), domain);
                        } catch (CentralRepoException ex) {
                            logger.log(Level.INFO, "Central repository exception while trying to get instances by type and value for domain: " + domain, ex);
                            ((OtherOccurrencesPanel) selectedComponent).reset();
                        }
                    } else {
                        ((OtherOccurrencesPanel) selectedComponent).reset();
                    }
                }
                //send fade in event
                DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.DetailsVisibleEvent(true));
            }
        });
    }

    /**
     * Private helper method to ensure tabs will re-populate after a new domain
     * is selected.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void resetTabsStatus() {
        for (Component comp : jTabbedPane1.getComponents()) {
            if (comp instanceof DomainArtifactsTabPanel) {
                ((DomainArtifactsTabPanel) comp).setStatus(DomainArtifactsTabPanel.ArtifactRetrievalStatus.UNPOPULATED);
            } else if (comp instanceof MiniTimelinePanel) {
                ((MiniTimelinePanel) comp).setStatus(DomainArtifactsTabPanel.ArtifactRetrievalStatus.UNPOPULATED, domain);
            } else if (comp instanceof OtherOccurrencesPanel) {
                ((OtherOccurrencesPanel) comp).reset();
            }
        }
    }

    /**
     * Get the name of the tab that was most recently selected.
     *
     * @return The name of the tab that was most recently selected.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    String getSelectedTabName() {
        return selectedTabName;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jTabbedPane1 = new javax.swing.JTabbedPane();

        setEnabled(false);
        setMinimumSize(new java.awt.Dimension(0, 0));
        setPreferredSize(new java.awt.Dimension(0, 0));
        setLayout(new java.awt.BorderLayout());

        jTabbedPane1.setMinimumSize(new java.awt.Dimension(0, 0));
        jTabbedPane1.setPreferredSize(new java.awt.Dimension(0, 0));
        add(jTabbedPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTabbedPane jTabbedPane1;
    // End of variables declaration//GEN-END:variables

    /*
     * Unregister the MiniTimelinePanel from the event bus.
     */
    void unregister() {
        for (Component comp : jTabbedPane1.getComponents()) {
            if (comp instanceof MiniTimelinePanel) {
                DiscoveryEventUtils.getDiscoveryEventBus().unregister(comp);
            }
        }
    }
}
