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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JScrollPane;
import org.openide.util.Exceptions;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.contentviewers.artifactviewers.ArtifactContentViewer;
import org.sleuthkit.autopsy.contentviewers.artifactviewers.CommunicationArtifactViewerHelper;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

@ServiceProvider(service = ArtifactContentViewer.class)
public class WebHistoryDetailsPanel extends AbstractArtifactDetailsPanel implements ArtifactContentViewer {

    private static final long serialVersionUID = 1L;
    private BlackboardArtifact webHistoryArtifact;
    private final GridBagLayout gridBagLayout = new GridBagLayout();
    private final List<BlackboardAttribute> urlList = new ArrayList<>();
    private final List<BlackboardAttribute> dateAccessedList = new ArrayList<>();
    private final List<BlackboardAttribute> referrerUrlList = new ArrayList<>();
    private final List<BlackboardAttribute> titleList = new ArrayList<>();
    private final List<BlackboardAttribute> programNameList = new ArrayList<>();
    private final List<BlackboardAttribute> domainList = new ArrayList<>();
    private final List<BlackboardAttribute> otherList = new ArrayList<>();
    private final List<BlackboardAttribute> artifactAttributesList = new ArrayList<>();
    private final GridBagConstraints gridBagConstraints = new GridBagConstraints();
    private String dataSourceName;

    /**
     * Creates new form WebHistoryDetailsPanel.
     */
    public WebHistoryDetailsPanel() {
        initComponents();
    }

    @Override
    public void setArtifact(BlackboardArtifact artifact) {

        resetComponent();
        if (artifact != null) {

            try {
                extractArtifactData(artifact);
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }
            updateView();
        }

        this.setLayout(this.gridBagLayout);
        this.revalidate();
        this.repaint();
    }

    /**
     * Extracts data from the artifact to be displayed in the panel.
     *
     * @param artifact Artifact to show.
     *
     * @throws TskCoreException
     */
    private void extractArtifactData(BlackboardArtifact artifact) throws TskCoreException {

        webHistoryArtifact = artifact;

        urlList.clear();
        dateAccessedList.clear();
        referrerUrlList.clear();
        titleList.clear();
        programNameList.clear();
        domainList.clear();
        otherList.clear();
        artifactAttributesList.clear();
        artifactAttributesList.addAll(webHistoryArtifact.getAttributes());
        // Get all the attributes and group them by the section panels they go in
        for (BlackboardAttribute bba : artifactAttributesList) {
            if (bba.getAttributeType().getTypeName().startsWith("TSK_URL")) {
                urlList.add(bba);
            } else if (bba.getAttributeType().getTypeName().startsWith("TSK_PROG_NAME")) {
                programNameList.add(bba);
            } else if (bba.getAttributeType().getTypeName().startsWith("TSK_DOMAIN")) {
                domainList.add(bba);
            } else if (bba.getAttributeType().getTypeName().startsWith("TSK_REFERRER")) {
                referrerUrlList.add(bba);
            } else if (bba.getAttributeType().getTypeName().startsWith("TSK_DATETIME_ACCESSED")) {
                dateAccessedList.add(bba);
            } else if (bba.getAttributeType().getTypeName().startsWith("TSK_TITLE")) {
                titleList.add(bba);
            } else {
                otherList.add(bba);
            }
            
        }

        dataSourceName = webHistoryArtifact.getDataSource().getName();
    }

    private void resetComponent() {
        gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridx = 0;
        gridBagConstraints.weighty = 0.0;
        gridBagConstraints.weightx = 0.0;    // keep components fixed horizontally.
        gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
        gridBagConstraints.fill = GridBagConstraints.NONE;
        webHistoryArtifact = null;
        dataSourceName = null;
        urlList.clear();
        dateAccessedList.clear();
        referrerUrlList.clear();
        titleList.clear();
        programNameList.clear();
        domainList.clear();
        artifactAttributesList.clear();
        // clear the panel 
        this.removeAll();
        this.setLayout(null);
    }

    @Override
    public Component getComponent() {
        // Slap a vertical scrollbar on the panel.
        return new JScrollPane(this, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    @Override
    public boolean isSupported(BlackboardArtifact artifact) {
        return (artifact != null)
                && (artifact.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void updateView() {
        CommunicationArtifactViewerHelper.addHeader(this, gridBagLayout, gridBagConstraints, "Attributes");

        for (BlackboardAttribute bba : this.titleList) {
            CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, bba.getAttributeType().getDisplayName(), bba.getDisplayString());
        }
//        CommunicationArtifactViewerHelper.addHeader(this, gridBagLayout, gridBagConstraints, sectionHeader);
        for (BlackboardAttribute bba : dateAccessedList) {
            CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, bba.getAttributeType().getDisplayName(), bba.getDisplayString());
        }
        for (BlackboardAttribute bba : domainList) {
            CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, bba.getAttributeType().getDisplayName(), bba.getDisplayString());
        }
        for (BlackboardAttribute bba : urlList) {
            CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, bba.getAttributeType().getDisplayName(), bba.getDisplayString());
        }
        for (BlackboardAttribute bba : referrerUrlList) {
            CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, bba.getAttributeType().getDisplayName(), bba.getDisplayString());
        }
        for (BlackboardAttribute bba : programNameList) {
            CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, bba.getAttributeType().getDisplayName(), bba.getDisplayString());
        }
        for (BlackboardAttribute bba : otherList) {
            CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, bba.getAttributeType().getDisplayName(), bba.getDisplayString());
        }
        CommunicationArtifactViewerHelper.addHeader(this, gridBagLayout, gridBagConstraints, "Source");
        CommunicationArtifactViewerHelper.addRow(this, gridBagLayout, gridBagConstraints, "Data Source", dataSourceName);
        // add veritcal glue at the end
        CommunicationArtifactViewerHelper.addPageEndGlue(this, gridBagLayout, this.gridBagConstraints);
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
