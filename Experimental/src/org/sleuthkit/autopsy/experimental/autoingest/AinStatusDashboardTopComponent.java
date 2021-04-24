/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Top component which displays the Auto Ingest Node Status Dashboard interface.
 */
@TopComponent.Description(
        preferredID = "AinStatusDashboardTopComponent",
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(mode = "nodeStatus", openAtStartup = false)
@Messages({
    "CTL_AinStatusDashboardAction=Auto Ingest Nodes",
    "CTL_AinStatusDashboardTopComponent=Auto Ingest Nodes"})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AinStatusDashboardTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;
    public final static String PREFERRED_ID = "AinStatusDashboardTopComponent"; // NON-NLS
    private static final Logger logger = Logger.getLogger(AinStatusDashboardTopComponent.class.getName());
    private static boolean topComponentInitialized = false;

    @Messages({
        "AinStatusDashboardTopComponent.exceptionMessage.failedToCreateDashboard=Failed to create Auto Ingest Node Status Dashboard.",})
    static void openTopComponent(AutoIngestMonitor monitor) {
        final AinStatusDashboardTopComponent tc = (AinStatusDashboardTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (tc != null) {
            topComponentInitialized = true;
            WindowManager.getDefault().isTopComponentFloating(tc);

            if (tc.isOpened() == false) {
                Mode mode = WindowManager.getDefault().findMode("nodeStatus"); // NON-NLS
                if (mode != null) {
                    mode.dockInto(tc);
                }
                /*
                 * Make sure we have a clean-slate before attaching a new
                 * dashboard instance so we don't accumulate them.
                 */
                tc.removeAll();
                
                /*
                 * Create a new dashboard instance to ensure we're using the
                 * most recent configuration.
                 */
                AinStatusDashboard nodeTab = new AinStatusDashboard(monitor);
                nodeTab.startUp();
                nodeTab.setSize(nodeTab.getPreferredSize());
                tc.setLayout(new BorderLayout());
                tc.add(nodeTab, BorderLayout.CENTER);
                tc.open();
            }
        }
    }

    static void closeTopComponent() {
        if (topComponentInitialized) {
            final TopComponent tc = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
            if (tc != null) {
                try {
                    tc.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Failed to close " + PREFERRED_ID, e); // NON-NLS
                }
            }
        }
    }

    AinStatusDashboardTopComponent() {
        initComponents();
        setName(Bundle.CTL_AinStatusDashboardTopComponent());
    }


    @Override
    public List<Mode> availableModes(List<Mode> modes) {
        /*
         * This looks like the right thing to do, but online discussions seems
         * to indicate this method is effectively deprecated. A break point
         * placed here was never hit.
         */
        return modes.stream().filter(mode -> mode.getName().equals("nodeStatus") || mode.getName().equals("ImageGallery"))
                .collect(Collectors.toList());
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
    }

    /**
     * Get the current AutoIngestDashboard if there is one.
     *
     * @return the current AutoIngestDashboard or null if there is not one
     */
    AinStatusDashboard getAinStatusDashboard() {
        for (Component comp : getComponents()) {
            if (comp instanceof AinStatusDashboard) {
                return (AinStatusDashboard) comp;
            }
        }
        return null;
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
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

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
