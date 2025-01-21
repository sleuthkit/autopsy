/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package com.basistech.df.cybertriage.autopsy.ctoptions;

import com.basistech.df.cybertriage.autopsy.ctoptions.subpanel.CTOptionsSubPanel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JPanel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;

/**
 * Options panel for Cyber Triage.
 */
public class CTOptionsPanel extends IngestModuleGlobalSettingsPanel {

    private static final int MAX_SUBPANEL_WIDTH = 700;

    private static final Logger logger = Logger.getLogger(CTOptionsPanel.class.getName());

    private final List<CTOptionsSubPanel> subPanels;

    /**
     * Creates new form CTOptions loading any CTOptionsSubPanel instances to be
     * displayed.
     */
    public CTOptionsPanel() {
        initComponents();
        Collection<? extends CTOptionsSubPanel> coll = Lookup.getDefault().lookupAll(CTOptionsSubPanel.class);
        Stream<? extends CTOptionsSubPanel> panelStream = coll != null ? coll.stream() : Stream.empty();
        this.subPanels = panelStream
                .map(panel -> {
                    try {
                        // lookup is returning singleton instances which means this panel gets messed up when accessed
                        // from multiple places because the panel's children are being added to a different CTOptionsPanel
                        return (CTOptionsSubPanel) panel.getClass().getConstructor().newInstance();
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(p -> p.getClass().getSimpleName().toUpperCase()).reversed())
                .collect(Collectors.toList());
        addSubOptionsPanels(this.subPanels);
    }

    private void addSubOptionsPanels(List<CTOptionsSubPanel> subPanels) {
        GridBagConstraints disclaimerConstraints = new GridBagConstraints();
        disclaimerConstraints.gridx = 0;
        disclaimerConstraints.gridy = 0;
        disclaimerConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        disclaimerConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        disclaimerConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        disclaimerConstraints.weighty = 0;
        disclaimerConstraints.weightx = 0;

        for (int i = 0; i < subPanels.size(); i++) {
            CTOptionsSubPanel subPanel = subPanels.get(i);

            subPanel.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(OptionsPanelController.PROP_CHANGED)) {
                        CTOptionsPanel.this.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
                    }
                }
            });

            GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = i + 1;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
            gridBagConstraints.weighty = 0;
            gridBagConstraints.weightx = 0;

            contentPane.add(subPanel, gridBagConstraints);
        }

        GridBagConstraints verticalConstraints = new GridBagConstraints();
        verticalConstraints.gridx = 0;
        verticalConstraints.gridy = subPanels.size() + 1;
        verticalConstraints.weighty = 1;
        verticalConstraints.weightx = 0;

        JPanel verticalSpacer = new JPanel();

        verticalSpacer.setMinimumSize(new Dimension(MAX_SUBPANEL_WIDTH, 0));
        verticalSpacer.setPreferredSize(new Dimension(MAX_SUBPANEL_WIDTH, 0));
        verticalSpacer.setMaximumSize(new Dimension(MAX_SUBPANEL_WIDTH, Short.MAX_VALUE));
        contentPane.add(verticalSpacer, verticalConstraints);

        GridBagConstraints horizontalConstraints = new GridBagConstraints();
        horizontalConstraints.gridx = 1;
        horizontalConstraints.gridy = 0;
        horizontalConstraints.weighty = 0;
        horizontalConstraints.weightx = 1;

        JPanel horizontalSpacer = new JPanel();
        contentPane.add(horizontalSpacer, horizontalConstraints);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
        contentPane = new javax.swing.JPanel();

        setLayout(new java.awt.BorderLayout());

        contentPane.setLayout(new java.awt.GridBagLayout());
        scrollPane.setViewportView(contentPane);

        add(scrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    @Override
    public void saveSettings() {
        subPanels.forEach(panel -> panel.saveSettings());
    }

    public void loadSavedSettings() {
        subPanels.forEach(panel -> panel.loadSettings());
    }

    public boolean valid() {
        return subPanels.stream().allMatch(panel -> panel.valid());
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel contentPane;
    // End of variables declaration//GEN-END:variables
}
