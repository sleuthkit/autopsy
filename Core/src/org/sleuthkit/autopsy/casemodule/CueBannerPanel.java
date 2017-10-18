/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;

/*
 * The panel in the default Autopsy startup window.
 */
public class CueBannerPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private static final String REVIEW_MODE_TITLE = "Open Multi-User Case (" + LOCAL_HOST_NAME + ")";
    /*
     * This is field is static for the sake of the closeOpenRecentCasesWindow
     * method.
     */
    private static JDialog recentCasesWindow;
    private static JDialog multiUserCaseWindow;

    public static void closeOpenRecentCasesWindow() {
        if (null != recentCasesWindow) {
            recentCasesWindow.setVisible(false);
        }
    }

    public static void closeMultiUserCasesWindow() {
        if (null != multiUserCaseWindow) {
            multiUserCaseWindow.setVisible(false);
        }
    }

    public CueBannerPanel() {
        initComponents();
        customizeComponents();
        enableComponents();
    }

    public CueBannerPanel(String welcomeLogo) {
        initComponents();
        ClassLoader cl = Lookup.getDefault().lookup(ClassLoader.class);
        if (cl != null) {
            ImageIcon icon = new ImageIcon(cl.getResource(welcomeLogo));
            autopsyLogo.setIcon(icon);
        }
        customizeComponents();
        enableComponents();
    }

    public void setCloseButtonActionListener(ActionListener e) {
        closeButton.addActionListener(e);
    }

    public void setCloseButtonText(String text) {
        closeButton.setText(text);
    }

    public void refresh() {
        enableComponents();
    }
    
    private void customizeComponents() {
        initRecentCasesWindow();
        initMultiUserCasesWindow();
    }

    private void initRecentCasesWindow() {
        recentCasesWindow = new JDialog(
                WindowManager.getDefault().getMainWindow(),
                NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.title.text"),
                Dialog.ModalityType.APPLICATION_MODAL);
        recentCasesWindow.setSize(new Dimension(750, 400));
        recentCasesWindow.getRootPane().registerKeyboardAction(
                e -> {
                    recentCasesWindow.setVisible(false);
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        OpenRecentCasePanel recentCasesPanel = OpenRecentCasePanel.getInstance();
        recentCasesPanel.setCloseButtonActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                recentCasesWindow.setVisible(false);
            }
        });
        recentCasesWindow.add(recentCasesPanel);
        recentCasesWindow.pack();
        recentCasesWindow.setResizable(false);
    }
    
    private void initMultiUserCasesWindow() {
        multiUserCaseWindow = new JDialog(
                WindowManager.getDefault().getMainWindow(),
                REVIEW_MODE_TITLE,
                Dialog.ModalityType.APPLICATION_MODAL);
        multiUserCaseWindow.getRootPane().registerKeyboardAction(
                e -> {
                    multiUserCaseWindow.setVisible(false);
                },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        multiUserCaseWindow.add(new MultiUserCasePanel());
        multiUserCaseWindow.pack();
        multiUserCaseWindow.setResizable(false);
    }

    private void enableComponents() {
        boolean enableOpenRecentCaseButton = (RecentCases.getInstance().getTotalRecentCases() > 0);
        openRecentCaseButton.setEnabled(enableOpenRecentCaseButton);
        openRecentCaseLabel.setEnabled(enableOpenRecentCaseButton);
        
        boolean enableOpenMultiUserCaseButton = UserPreferences.getIsMultiUserModeEnabled();
        openMultiUserCaseButton.setEnabled(enableOpenMultiUserCaseButton);
        openMultiUserCaseLabel.setEnabled(enableOpenMultiUserCaseButton);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        autopsyLogo = new javax.swing.JLabel();
        this.autopsyLogo.setText("");
        createNewCaseButton = new javax.swing.JButton();
        openRecentCaseButton = new javax.swing.JButton();
        createNewCaseLabel = new javax.swing.JLabel();
        openRecentCaseLabel = new javax.swing.JLabel();
        openExistingCaseButton = new javax.swing.JButton();
        openExistingCaseLabel = new javax.swing.JLabel();
        closeButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        openMultiUserCaseButton = new javax.swing.JButton();
        openMultiUserCaseLabel = new javax.swing.JLabel();

        autopsyLogo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/casemodule/welcome_logo.png"))); // NOI18N
        autopsyLogo.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.autopsyLogo.text")); // NOI18N

        createNewCaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/casemodule/btn_icon_create_new_case.png"))); // NOI18N
        createNewCaseButton.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.createNewCaseButton.text")); // NOI18N
        createNewCaseButton.setBorder(null);
        createNewCaseButton.setBorderPainted(false);
        createNewCaseButton.setContentAreaFilled(false);
        createNewCaseButton.setPreferredSize(new java.awt.Dimension(64, 64));
        createNewCaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                createNewCaseButtonActionPerformed(evt);
            }
        });

        openRecentCaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/casemodule/btn_icon_open_recent.png"))); // NOI18N
        openRecentCaseButton.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.openRecentCaseButton.text")); // NOI18N
        openRecentCaseButton.setBorder(null);
        openRecentCaseButton.setBorderPainted(false);
        openRecentCaseButton.setContentAreaFilled(false);
        openRecentCaseButton.setPreferredSize(new java.awt.Dimension(64, 64));
        openRecentCaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openRecentCaseButtonActionPerformed(evt);
            }
        });

        createNewCaseLabel.setFont(createNewCaseLabel.getFont().deriveFont(createNewCaseLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 13));
        createNewCaseLabel.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.createNewCaseLabel.text")); // NOI18N

        openRecentCaseLabel.setFont(openRecentCaseLabel.getFont().deriveFont(openRecentCaseLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 13));
        openRecentCaseLabel.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.openRecentCaseLabel.text")); // NOI18N

        openExistingCaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/casemodule/btn_icon_open_existing.png"))); // NOI18N
        openExistingCaseButton.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.openExistingCaseButton.text")); // NOI18N
        openExistingCaseButton.setBorder(null);
        openExistingCaseButton.setBorderPainted(false);
        openExistingCaseButton.setContentAreaFilled(false);
        openExistingCaseButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
        openExistingCaseButton.setPreferredSize(new java.awt.Dimension(64, 64));
        openExistingCaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openExistingCaseButtonActionPerformed(evt);
            }
        });

        openExistingCaseLabel.setFont(openExistingCaseLabel.getFont().deriveFont(openExistingCaseLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 13));
        openExistingCaseLabel.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.openExistingCaseLabel.text")); // NOI18N

        closeButton.setFont(closeButton.getFont().deriveFont(closeButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        closeButton.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.closeButton.text")); // NOI18N

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        openMultiUserCaseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/casemodule/btn_icon_open_existing.png"))); // NOI18N
        openMultiUserCaseButton.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.openMultiUserCaseButton.text")); // NOI18N
        openMultiUserCaseButton.setBorder(null);
        openMultiUserCaseButton.setBorderPainted(false);
        openMultiUserCaseButton.setContentAreaFilled(false);
        openMultiUserCaseButton.setMargin(new java.awt.Insets(1, 1, 1, 1));
        openMultiUserCaseButton.setPreferredSize(new java.awt.Dimension(64, 64));
        openMultiUserCaseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMultiUserCaseButtonActionPerformed(evt);
            }
        });

        openMultiUserCaseLabel.setFont(openMultiUserCaseLabel.getFont().deriveFont(openMultiUserCaseLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 13));
        openMultiUserCaseLabel.setText(org.openide.util.NbBundle.getMessage(CueBannerPanel.class, "CueBannerPanel.openMultiUserCaseLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(autopsyLogo)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 5, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(createNewCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(openRecentCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(openExistingCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(openMultiUserCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(createNewCaseLabel)
                            .addComponent(openRecentCaseLabel)
                            .addComponent(openExistingCaseLabel)
                            .addComponent(openMultiUserCaseLabel)))
                    .addComponent(closeButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(createNewCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(createNewCaseLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(openRecentCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 70, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(openRecentCaseLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(openExistingCaseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(openExistingCaseLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(openMultiUserCaseButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(openMultiUserCaseLabel)
                                .addGap(20, 20, 20))))
                    .addComponent(jSeparator1)
                    .addComponent(autopsyLogo, javax.swing.GroupLayout.PREFERRED_SIZE, 257, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                .addComponent(closeButton)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void createNewCaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_createNewCaseButtonActionPerformed
        Lookup.getDefault().lookup(CaseNewActionInterface.class).actionPerformed(evt);
    }//GEN-LAST:event_createNewCaseButtonActionPerformed

    private void openExistingCaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openExistingCaseButtonActionPerformed
        Lookup.getDefault().lookup(CaseOpenAction.class).actionPerformed(evt);
    }//GEN-LAST:event_openExistingCaseButtonActionPerformed

    private void openRecentCaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openRecentCaseButtonActionPerformed
        recentCasesWindow.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        OpenRecentCasePanel.getInstance();  //refreshes the recent cases table
        recentCasesWindow.setVisible(true);
    }//GEN-LAST:event_openRecentCaseButtonActionPerformed

    private void openMultiUserCaseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openMultiUserCaseButtonActionPerformed
        multiUserCaseWindow.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        multiUserCaseWindow.setVisible(true);
    }//GEN-LAST:event_openMultiUserCaseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel autopsyLogo;
    private javax.swing.JButton closeButton;
    private javax.swing.JButton createNewCaseButton;
    private javax.swing.JLabel createNewCaseLabel;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton openMultiUserCaseButton;
    private javax.swing.JLabel openMultiUserCaseLabel;
    private javax.swing.JButton openExistingCaseButton;
    private javax.swing.JLabel openExistingCaseLabel;
    private javax.swing.JButton openRecentCaseButton;
    private javax.swing.JLabel openRecentCaseLabel;
    // End of variables declaration//GEN-END:variables

}
