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

/*
 * IngestDialogPanel.java
 *
 * Created on Feb 1, 2012, 3:02:15 PM
 */
package org.sleuthkit.autopsy.ingest;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.sleuthkit.datamodel.Image;

/**
 *
 * @author dfickling
 */
public class IngestDialogPanel extends javax.swing.JPanel {
    
    // The image that's just been added to the database
    private Image image;
    private IngestManager manager = null;
    private Collection<IngestServiceAbstract> services;
    private Map<String, Boolean> serviceStates;
    private ActionListener serviceSelListener = new ActionListener() {

        @Override
        public void actionPerformed(ActionEvent ev) {
            JCheckBox box = (JCheckBox) ev.getSource();
            serviceStates.put(box.getName(), box.isSelected());
        }
    };

    /** Creates new form IngestDialogPanel */
    IngestDialogPanel() {
        services = new ArrayList<IngestServiceAbstract>();
        serviceStates = new HashMap<String, Boolean>();
        initComponents();
        customizeComponents();
    }
    
    void setImage(Image image) {
        this.image = image;
    }
    
    
    
    private void customizeComponents(){
        this.manager = IngestTopComponent.getDefault().getManager();
        
        JScrollPane scrollPane = new JScrollPane(servicesPanel);
        scrollPane.setPreferredSize(this.getSize());
        this.add(scrollPane, BorderLayout.CENTER);

        servicesPanel.setLayout(new BoxLayout(servicesPanel, BoxLayout.Y_AXIS));
        
        Collection<IngestServiceImage> imageServices = IngestManager.enumerateImageServices();
        for (final IngestServiceImage service : imageServices) {
            final String serviceName = service.getName();
            services.add(service);
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            JCheckBox checkbox = new JCheckBox(serviceName, true);
            checkbox.setName(serviceName);
            checkbox.addActionListener(serviceSelListener);
            panel.add(checkbox);
            panel.add(Box.createHorizontalGlue());
            JButton button = new JButton("Configure");
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    service.userConfigure();
                }
            });
            if(!service.isConfigurable())
                button.setEnabled(false);
            panel.add(button);
            servicesPanel.add(panel);
            serviceStates.put(serviceName, true);
        }

        Collection<IngestServiceFsContent> fsServices = IngestManager.enumerateFsContentServices();
        for (final IngestServiceFsContent service : fsServices) {
            final String serviceName = service.getName();
            services.add(service);
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
            JCheckBox checkbox = new JCheckBox(serviceName, true);
            checkbox.setName(serviceName);
            checkbox.addActionListener(serviceSelListener);
            checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(checkbox);
            panel.add(Box.createHorizontalGlue());
            JButton button = new JButton("Configure");
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    service.userConfigure();
                }
            });
            if(!service.isConfigurable())
                button.setEnabled(false);
            button.setAlignmentX(Component.RIGHT_ALIGNMENT);
            panel.add(button);
            servicesPanel.add(panel);
            serviceStates.put(serviceName, true);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        servicesLabel = new javax.swing.JLabel();
        servicesPanel = new javax.swing.JPanel();
        startButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();

        servicesLabel.setText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.servicesLabel.text")); // NOI18N

        servicesPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        servicesPanel.setMinimumSize(new java.awt.Dimension(200, 150));
        servicesPanel.setPreferredSize(new java.awt.Dimension(200, 150));

        javax.swing.GroupLayout servicesPanelLayout = new javax.swing.GroupLayout(servicesPanel);
        servicesPanel.setLayout(servicesPanelLayout);
        servicesPanelLayout.setHorizontalGroup(
            servicesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 198, Short.MAX_VALUE)
        );
        servicesPanelLayout.setVerticalGroup(
            servicesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 148, Short.MAX_VALUE)
        );

        startButton.setText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.startButton.text")); // NOI18N
        startButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startButtonActionPerformed(evt);
            }
        });

        closeButton.setText(org.openide.util.NbBundle.getMessage(IngestDialogPanel.class, "IngestDialogPanel.closeButton.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(servicesLabel)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(startButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(closeButton))
                    .addComponent(servicesPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(servicesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(servicesPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(closeButton)
                    .addComponent(startButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startButtonActionPerformed
        this.manager = IngestTopComponent.getDefault().getManager();
        if (manager == null) {
            return;
        }

        //pick the services
        List<IngestServiceAbstract> servicesToStart = new ArrayList<IngestServiceAbstract>();
        for (IngestServiceAbstract service : services) {
            boolean serviceEnabled = serviceStates.get(service.getName());
            if (serviceEnabled) {
                servicesToStart.add(service);
            }
        }

        if (!services.isEmpty() ) {
            manager.execute(servicesToStart, image);
        }
    }//GEN-LAST:event_startButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JLabel servicesLabel;
    private javax.swing.JPanel servicesPanel;
    private javax.swing.JButton startButton;
    // End of variables declaration//GEN-END:variables

    public void setCloseButtonActionListener(ActionListener actionListener) {
        closeButton.addActionListener(actionListener);
    }
    
    public void setStartButtonActionListener(ActionListener actionListener) {
        startButton.addActionListener(actionListener);
    }
    
    public void setBothButtonActionListener(ActionListener actionListener) {
        startButton.addActionListener(actionListener);
        closeButton.addActionListener(actionListener);
    }
}
