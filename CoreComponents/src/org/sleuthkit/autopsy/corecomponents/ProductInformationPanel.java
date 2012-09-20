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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Cursor;
import java.awt.Window;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Locale;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import org.netbeans.core.actions.HTMLViewAction;
import org.openide.awt.HtmlBrowser;
import org.openide.modules.Places;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.SleuthkitJNI;

/**
 * Custom "About" window panel.
 */
class ProductInformationPanel extends JPanel implements HyperlinkListener {

    private URL url = null;
    private Icon about;
    private boolean verboseLogging;

    ProductInformationPanel() {
        about = new ImageIcon(org.netbeans.core.startup.Splash.loadContent(true));
        initComponents();
        jLabel1.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        description.setText(org.openide.util.NbBundle.getMessage(ProductInformationPanel.class,
                "LBL_Description", new Object[]{getProductVersionValue(), getJavaValue(), getVMValue(),
                    getOperatingSystemValue(), getEncodingValue(), getSystemLocaleValue(), getUserDirValue(), getSleuthKitVersionValue(), Version.getNetbeansBuild(), Version.getBuildType().toString()}));
        description.addHyperlinkListener(this);
        copyright.addHyperlinkListener(this);
        copyright.setBackground(getBackground());
        if (verboseLoggingIsSet()) {
            disableVerboseLoggingButton();
        }

    }

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        jLabel1 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jButton2 = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        copyright = new javax.swing.JTextPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        description = new javax.swing.JTextPane();
        verboseLoggingButton = new javax.swing.JButton();

        setBackground(new java.awt.Color(255, 255, 255));

        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setIcon(about);
        jLabel1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jLabel1.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel1MouseClicked(evt);
            }
        });

        jPanel1.setBackground(new java.awt.Color(255, 255, 255));
        jPanel1.setLayout(new java.awt.GridBagLayout());

        jButton2.setBackground(new java.awt.Color(255, 255, 255));
        jButton2.setText(NbBundle.getMessage(ProductInformationPanel.class, "LBL_Close")); // NOI18N
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        jPanel1.add(jButton2, gridBagConstraints);

        jScrollPane3.setBorder(null);

        copyright.setBorder(null);
        copyright.setContentType("text/html");
        copyright.setEditable(false);
        copyright.setText(org.openide.util.NbBundle.getBundle(ProductInformationPanel.class).getString("LBL_Copyright")); // NOI18N
        copyright.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                copyrightMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(copyright);

        description.setContentType("text/html");
        description.setEditable(false);
        jScrollPane2.setViewportView(description);

        verboseLoggingButton.setBackground(new java.awt.Color(255, 255, 255));
        verboseLoggingButton.setText("Activate verbose logging");
        verboseLoggingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                activateVerboseLogging(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 394, Short.MAX_VALUE)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 394, Short.MAX_VALUE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 394, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 237, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(verboseLoggingButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 101, Short.MAX_VALUE)
                .addGap(32, 32, 32)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 103, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(27, 27, 27)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(verboseLoggingButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

private void copyrightMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_copyrightMouseClicked
    showUrl();
}//GEN-LAST:event_copyrightMouseClicked

private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
    closeDialog();
}//GEN-LAST:event_jButton2ActionPerformed

private void jLabel1MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel1MouseClicked
    try {
        url = new URL(NbBundle.getMessage(ProductInformationPanel.class, "URL_ON_IMG")); // NOI18N
        showUrl();
    } catch (MalformedURLException ex) {
        //ignore
    }
    url = null;
}//GEN-LAST:event_jLabel1MouseClicked

    private void activateVerboseLogging(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_activateVerboseLogging
        startVerboseLogging();
        disableVerboseLoggingButton();
    }//GEN-LAST:event_activateVerboseLogging
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane copyright;
    private javax.swing.JTextPane description;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton verboseLoggingButton;
    // End of variables declaration//GEN-END:variables

    private void disableVerboseLoggingButton() {
        this.verboseLoggingButton.setEnabled(false);
        this.verboseLoggingButton.setText("Verbose logging enabled");
    }

    private void closeDialog() {
        Window w = SwingUtilities.getWindowAncestor(this);
        w.setVisible(false);
        w.dispose();
    }

    private void showUrl() {
        if (url != null) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                    NbBundle.getBundle(HTMLViewAction.class).getString("CTL_OpeningBrowser"));
            HtmlBrowser.URLDisplayer.getDefault().showURL(url);
        }
    }

    private static String getSleuthKitVersionValue() {
        return SleuthkitJNI.getVersion();
    }

    private static String getProductVersionValue() {
        return MessageFormat.format(
                NbBundle.getBundle("org.netbeans.core.startup.Bundle").getString("currentVersion"),
                new Object[]{System.getProperty("netbeans.buildnumber")});
    }

    private static String getOperatingSystemValue() {
        return NbBundle.getMessage(ProductInformationPanel.class, "Format_OperatingSystem_Value",
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"));
    }

    private static String getJavaValue() {
        return System.getProperty("java.version", "unknown");
    }

    private static String getVMValue() {
        return System.getProperty("java.vm.name", "unknown") + " " + System.getProperty("java.vm.version", "");
    }

    private static String getSystemLocaleValue() {
        String branding;
        return Locale.getDefault().toString() + ((branding = NbBundle.getBranding()) == null ? "" : (" (" + branding + ")")); // NOI18N
    }

    private String getUserDirValue() {
        return Places.getUserDirectory().getAbsolutePath();
    }

    private static String getEncodingValue() {
        return System.getProperty("file.encoding", "unknown");
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent event) {
        if (HyperlinkEvent.EventType.ENTERED == event.getEventType()) {
            url = event.getURL();
        } else if (HyperlinkEvent.EventType.EXITED == event.getEventType()) {
            url = null;
        }
    }
    
    /**
     * Activate verbose logging for Sleuth Kit
     */
    public void startVerboseLogging() {
        verboseLogging = true;
        String logPath = ModuleSettings.getUserDirPath() + File.separator + "sleuthkit.txt";
        
        SleuthkitJNI.startVerboseLogging(logPath);
    }
    
    /**
     * Checks if verbose logging has been enabled.
     * @return true if verbose logging has been enabled.
     */
    public boolean verboseLoggingIsSet() {
        return verboseLogging;
    }
}
