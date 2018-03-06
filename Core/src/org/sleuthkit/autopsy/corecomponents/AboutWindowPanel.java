/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.awt.Image;
import java.awt.Window;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Locale;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import org.netbeans.core.actions.HTMLViewAction;
import org.openide.awt.HtmlBrowser;
import org.openide.modules.Places;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.SleuthkitJNI;

/**
 * Custom "About" window panel.
 */
public final class AboutWindowPanel extends JPanel implements HyperlinkListener {

    private static final long serialVersionUID = 1L;
    private URL url = null;
    private final Icon about;
    private boolean verboseLogging;

    public AboutWindowPanel() {
        about = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/splash.png"));
        init();
    }
        
    public AboutWindowPanel(String pathToBrandingImage) {
        about = new ImageIcon(ImageUtilities.loadImage(pathToBrandingImage));
        init();
    }

    private void init() {
        initComponents();
        logoLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        description.setText(org.openide.util.NbBundle.getMessage(AboutWindowPanel.class,
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

        logoLabel = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        copyright = new javax.swing.JTextPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        description = new javax.swing.JTextPane();
        verboseLoggingButton = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();

        setBackground(new java.awt.Color(255, 255, 255));

        logoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        logoLabel.setIcon(about);
        logoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        logoLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                logoLabelMouseClicked(evt);
            }
        });

        jScrollPane3.setBorder(null);

        copyright.setEditable(false);
        copyright.setBorder(null);
        copyright.setContentType("text/html"); // NOI18N
        copyright.setText(org.openide.util.NbBundle.getBundle(AboutWindowPanel.class).getString("LBL_Copyright")); // NOI18N
        copyright.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                copyrightMouseClicked(evt);
            }
        });
        jScrollPane3.setViewportView(copyright);

        description.setEditable(false);
        description.setContentType("text/html"); // NOI18N
        jScrollPane2.setViewportView(description);

        verboseLoggingButton.setBackground(new java.awt.Color(255, 255, 255));
        verboseLoggingButton.setText("Activate verbose logging");
        verboseLoggingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                activateVerboseLogging(evt);
            }
        });

        jButton2.setBackground(new java.awt.Color(255, 255, 255));
        jButton2.setText(NbBundle.getMessage(AboutWindowPanel.class, "LBL_Close")); // NOI18N
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 486, Short.MAX_VALUE)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 486, Short.MAX_VALUE)
                    .addComponent(logoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 486, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(verboseLoggingButton, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jButton2, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(logoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 139, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, Short.MAX_VALUE)
                .addComponent(verboseLoggingButton)
                .addGap(18, 18, 18)
                .addComponent(jButton2)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

private void copyrightMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_copyrightMouseClicked
    showUrl();
}//GEN-LAST:event_copyrightMouseClicked

private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
    closeDialog();
}//GEN-LAST:event_jButton2ActionPerformed

private void logoLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_logoLabelMouseClicked
    try {
        url = new URL(NbBundle.getMessage(AboutWindowPanel.class, "URL_ON_IMG")); // NOI18N
        showUrl();
    } catch (MalformedURLException ex) {
        //ignore
    }
    url = null;
}//GEN-LAST:event_logoLabelMouseClicked

    private void activateVerboseLogging(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_activateVerboseLogging
        startVerboseLogging();
        disableVerboseLoggingButton();
    }//GEN-LAST:event_activateVerboseLogging
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextPane copyright;
    private javax.swing.JTextPane description;
    private javax.swing.JButton jButton2;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JLabel logoLabel;
    private javax.swing.JButton verboseLoggingButton;
    // End of variables declaration//GEN-END:variables

    private void disableVerboseLoggingButton() {
        this.verboseLoggingButton.setEnabled(false);
        this.verboseLoggingButton.setText(
                NbBundle.getMessage(this.getClass(), "ProductInformationPanel.verbLoggingEnabled.text"));
    }

    private void closeDialog() {
        Window w = SwingUtilities.getWindowAncestor(this);
        w.setVisible(false);
        w.dispose();
    }

    private void showUrl() {
        if (url != null) {
            org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                    NbBundle.getBundle(HTMLViewAction.class).getString("CTL_OpeningBrowser")); //NON-NLS
            HtmlBrowser.URLDisplayer.getDefault().showURL(url);
        }
    }

    private static String getSleuthKitVersionValue() {
        return SleuthkitJNI.getVersion();
    }

    private static String getProductVersionValue() {
        return MessageFormat.format(
                NbBundle.getBundle("org.netbeans.core.startup.Bundle").getString("currentVersion"), //NON-NLS
                new Object[]{System.getProperty("netbeans.buildnumber")});
    }

    private static String getOperatingSystemValue() {
        return NbBundle.getMessage(AboutWindowPanel.class, "Format_OperatingSystem_Value",
                System.getProperty("os.name", //NON-NLS
                        NbBundle.getMessage(AboutWindowPanel.class,
                                "ProductInformationPanel.propertyUnknown.text")),
                System.getProperty("os.version", //NON-NLS
                        NbBundle.getMessage(AboutWindowPanel.class,
                                "ProductInformationPanel.propertyUnknown.text")),
                System.getProperty("os.arch", //NON-NLS
                        NbBundle.getMessage(AboutWindowPanel.class,
                                "ProductInformationPanel.propertyUnknown.text")));
    }

    private static String getJavaValue() {
        return System.getProperty("java.version", //NON-NLS
                NbBundle.getMessage(AboutWindowPanel.class,
                        "ProductInformationPanel.propertyUnknown.text"));
    }

    private static String getVMValue() {
        return NbBundle.getMessage(AboutWindowPanel.class,
                "ProductInformationPanel.getVMValue.text",
                System.getProperty("java.vm.name", //NON-NLS
                        NbBundle.getMessage(AboutWindowPanel.class,
                                "ProductInformationPanel.propertyUnknown.text")),
                System.getProperty("java.vm.version", "")); //NON-NLS
    }

    private static String getSystemLocaleValue() {
        String branding;
        return Locale.getDefault().toString() + ((branding = NbBundle.getBranding()) == null ? "" : (" (" + branding + ")")); // NOI18N
    }

    private String getUserDirValue() {
        return Places.getUserDirectory().getAbsolutePath();
    }

    private static String getEncodingValue() {
        return System.getProperty("file.encoding", //NON-NLS
                NbBundle.getMessage(AboutWindowPanel.class, "ProductInformationPanel.propertyUnknown.text"));
    }

    public void setCopyright(String text) {
        copyright.setText(text);
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
        String logPath = PlatformUtil.getUserDirectory() + File.separator + "sleuthkit.txt"; //NON-NLS

        SleuthkitJNI.startVerboseLogging(logPath);
    }

    /**
     * Checks if verbose logging has been enabled.
     *
     * @return true if verbose logging has been enabled.
     */
    public boolean verboseLoggingIsSet() {
        return verboseLogging;
    }
}
