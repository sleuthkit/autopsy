/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.datamodel.Image;

/**
 * Top component explorer for the Ingest module.
 */
public final class IngestTopComponent extends TopComponent implements DataExplorer {

    private static IngestTopComponent instance;
    private static final Logger logger = Logger.getLogger(IngestTopComponent.class.getName());
    private IngestManager manager = null;
    private Collection<IngestServiceAbstract> services;
    private Map<String, Boolean> serviceStates;
    private IngestMessagePanel messagePanel;
    private ActionListener serviceSelListener = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent ev) {
            JCheckBox box = (JCheckBox) ev.getSource();
            serviceStates.put(box.getName(), box.isSelected());
        }
    };

    private IngestTopComponent() {
        services = new ArrayList<IngestServiceAbstract>();
        serviceStates = new HashMap<String, Boolean>();

        initComponents();
        customizeComponents();
        setName(NbBundle.getMessage(IngestTopComponent.class, "CTL_IngestTopComponent"));
        setToolTipText(NbBundle.getMessage(IngestTopComponent.class, "HINT_IngestTopComponent"));
        //putClientProperty(TopComponent.PROP_UNDOCKING_DISABLED, Boolean.TRUE);
        putClientProperty(TopComponent.PROP_CLOSING_DISABLED, Boolean.TRUE);

    }

    public static synchronized IngestTopComponent getDefault() {
        if (instance == null) {
            instance = new IngestTopComponent();
        }
        return instance;
    }

    @Override
    public TopComponent getTopComponent() {
        return this;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        logger.log(Level.INFO, "Unhandled property change: " + evt.getPropertyName());
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    private void customizeComponents() {
        //custom GUI setup not done by builder
        //freqSlider.setToolTipText("Lower update frequency can optimize performance of certain ingest services, but also reduce real time status feedback");

        /* JScrollPane scrollPane = new JScrollPane(servicesPanel);
        scrollPane.setPreferredSize(this.getSize());
        this.add(scrollPane, BorderLayout.CENTER);
        servicesPanel.setLayout(new BoxLayout(servicesPanel, BoxLayout.Y_AXIS)); */


        //freqSlider.setEnabled(false);

        messagePanel = new IngestMessagePanel();

        messagePanel.setOpaque(false);
        messageFrame.setOpaque(false);
        messageFrame.addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent e) {

                super.componentResized(e);
                messageFrame.setPreferredSize(messageFrame.getSize());
            }
        });

        //make messageframe on top
        this.setComponentZOrder(controlPanel, 1);
        messageFrame.setContentPane(messagePanel);
        messageFrame.pack();
        messageFrame.setVisible(true);

        //handle case change
        Case.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(Case.CASE_CURRENT_CASE)) {
                    Case oldCase = (Case) evt.getOldValue();
                    if (oldCase == null) //nothing to do, new case had been opened
                    {
                        return;
                    }
                    //stop workers if running
                    if (manager != null) {
                        manager.stopAll();
                    }
                    //clear inbox
                    messagePanel.clearMessages();
                } else if (evt.getPropertyName().equals(Case.CASE_ADD_IMAGE)) {
                    final Image image = (Image) evt.getNewValue();
                    final IngestDialog ingestDialog = new IngestDialog();
                    ingestDialog.setImage(image);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            ingestDialog.display();
                        }
                    });

                }
            }
        });


        /* Collection<IngestServiceImage> imageServices = IngestManager.enumerateImageServices();
        for (IngestServiceImage service : imageServices) {
        final String serviceName = service.getName();
        services.add(service);
        JCheckBox checkbox = new JCheckBox(serviceName, true);
        checkbox.setName(serviceName);
        checkbox.addActionListener(serviceSelListener);
        servicesPanel.add(checkbox);
        serviceStates.put(serviceName, true);
        }
        
        Collection<IngestServiceFsContent> fsServices = IngestManager.enumerateFsContentServices();
        for (IngestServiceFsContent service : fsServices) {
        final String serviceName = service.getName();
        services.add(service);
        JCheckBox checkbox = new JCheckBox(serviceName, true);
        checkbox.setName(serviceName);
        checkbox.addActionListener(serviceSelListener);
        servicesPanel.add(checkbox);
        serviceStates.put(serviceName, true); 
        }*/


    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainScrollPane = new javax.swing.JScrollPane();
        mainPanel = new javax.swing.JPanel();
        messageFrame = new javax.swing.JInternalFrame();
        controlPanel = new javax.swing.JPanel();
        refreshFreqLabel = new javax.swing.JLabel();
        mainProgressBar = new javax.swing.JProgressBar();
        ingestProgressLabel = new javax.swing.JLabel();

        mainScrollPane.setPreferredSize(new java.awt.Dimension(322, 749));

        mainPanel.setPreferredSize(new java.awt.Dimension(322, 749));

        messageFrame.setBorder(new javax.swing.border.LineBorder(javax.swing.UIManager.getDefaults().getColor("InternalFrame.inactiveTitleBackground"), 3, true));
        messageFrame.setMaximizable(true);
        messageFrame.setResizable(true);
        messageFrame.setTitle(org.openide.util.NbBundle.getMessage(IngestTopComponent.class, "IngestTopComponent.messageFrame.title")); // NOI18N
        messageFrame.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        messageFrame.setFrameIcon(null);
        messageFrame.setOpaque(true);
        messageFrame.setPreferredSize(new java.awt.Dimension(280, 260));
        messageFrame.setVisible(true);

        javax.swing.GroupLayout messageFrameLayout = new javax.swing.GroupLayout(messageFrame.getContentPane());
        messageFrame.getContentPane().setLayout(messageFrameLayout);
        messageFrameLayout.setHorizontalGroup(
            messageFrameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 274, Short.MAX_VALUE)
        );
        messageFrameLayout.setVerticalGroup(
            messageFrameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 231, Short.MAX_VALUE)
        );

        org.openide.awt.Mnemonics.setLocalizedText(refreshFreqLabel, org.openide.util.NbBundle.getMessage(IngestTopComponent.class, "IngestTopComponent.refreshFreqLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(ingestProgressLabel, org.openide.util.NbBundle.getMessage(IngestTopComponent.class, "IngestTopComponent.ingestProgressLabel.text")); // NOI18N

        javax.swing.GroupLayout controlPanelLayout = new javax.swing.GroupLayout(controlPanel);
        controlPanel.setLayout(controlPanelLayout);
        controlPanelLayout.setHorizontalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(mainProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, 248, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(controlPanelLayout.createSequentialGroup()
                        .addGap(90, 90, 90)
                        .addComponent(ingestProgressLabel)))
                .addContainerGap(22, Short.MAX_VALUE))
            .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(controlPanelLayout.createSequentialGroup()
                    .addGap(74, 74, 74)
                    .addComponent(refreshFreqLabel)
                    .addContainerGap(116, Short.MAX_VALUE)))
        );
        controlPanelLayout.setVerticalGroup(
            controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(controlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(mainProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ingestProgressLabel)
                .addContainerGap(524, Short.MAX_VALUE))
            .addGroup(controlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(controlPanelLayout.createSequentialGroup()
                    .addGap(555, 555, 555)
                    .addComponent(refreshFreqLabel)
                    .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(controlPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap(48, Short.MAX_VALUE))
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addComponent(messageFrame, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(58, Short.MAX_VALUE))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(controlPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(27, 27, 27)
                .addComponent(messageFrame, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(415, 415, 415))
        );

        mainScrollPane.setViewportView(mainPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 340, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 771, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel controlPanel;
    private javax.swing.JLabel ingestProgressLabel;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JProgressBar mainProgressBar;
    private javax.swing.JScrollPane mainScrollPane;
    private javax.swing.JInternalFrame messageFrame;
    private javax.swing.JLabel refreshFreqLabel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        logger.log(Level.INFO, "IngestTopComponent opened()");
        if (manager == null) {
            manager = new IngestManager(this);
        }
    }

    @Override
    public void componentClosed() {
        logger.log(Level.INFO, "IngestTopComponent closed()");

    }

    void enableStartButton(boolean enable) {
        //startButton.setEnabled(enable);
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");

    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");

    }

    /**
     * Display ingest summary report in some dialog
     */
    void displayReport(String ingestReport) {
        JOptionPane.showMessageDialog(
                null,
                ingestReport,
                "File Ingest Summary",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Display IngestMessage from service (forwarded by IngestManager)
     */
    void displayMessage(IngestMessage ingestMessage) {
        messagePanel.addMessage(ingestMessage);
    }

    void initProgress(int maximum) {
        this.mainProgressBar.setMaximum(maximum);
    }

    void updateProgress(int progress) {
        this.mainProgressBar.setValue(progress);
    }

    IngestManager getManager() {
        return this.manager;
    }
}
