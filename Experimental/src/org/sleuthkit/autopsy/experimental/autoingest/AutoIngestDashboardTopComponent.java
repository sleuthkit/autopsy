/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Top component which displays the Auto Ingest Dashboard interface.
 */
@TopComponent.Description(
        preferredID = "AutoIngestDashboardTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@TopComponent.Registration(mode = "dashboard", openAtStartup = false)
@Messages({
    "CTL_AutoIngestDashboardAction=Auto Ingest Dashboard",
    "CTL_AutoIngestDashboardTopComponent=Auto Ingest Dashboard"})
public final class AutoIngestDashboardTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;
    public final static String PREFERRED_ID = "AutoIngestDashboardTopComponent"; // NON-NLS
    private static final Logger logger = Logger.getLogger(AutoIngestDashboardTopComponent.class.getName());
    private static boolean topComponentInitialized = false;

    @Messages({
        "AutoIngestDashboardTopComponent.exceptionMessage.failedToCreateDashboard=Failed to create Auto Ingest Dashboard.",})
    public static void openTopComponent() {
        final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (tc != null) {
            topComponentInitialized = true;
            WindowManager.getDefault().isTopComponentFloating(tc);
            Mode mode = WindowManager.getDefault().findMode("dashboard"); // NON-NLS

            try {
                if (tc.isOpened() == false) {
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
                    AutoIngestDashboard dashboard = AutoIngestDashboard.createDashboard();
                    tc.add(dashboard);
                    dashboard.setSize(dashboard.getPreferredSize());
                    
                    tc.open();
                }
                tc.toFront();
                tc.requestActive();
            } catch (AutoIngestDashboard.AutoIngestDashboardException ex) {
                logger.log(Level.SEVERE, "Unable to create auto ingest dashboard", ex);
                MessageNotifyUtil.Message.error(Bundle.AutoIngestDashboardTopComponent_exceptionMessage_failedToCreateDashboard());
            }
        }
    }

    public static void closeTopComponent() {
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

    public AutoIngestDashboardTopComponent() {
        initComponents();
        setName(Bundle.CTL_AutoIngestDashboardTopComponent());
    }

    @Override
    public List<Mode> availableModes(List<Mode> modes) {
        /*
         * This looks like the right thing to do, but online discussions seems
         * to indicate this method is effectively deprecated. A break point
         * placed here was never hit.
         */
        return modes.stream().filter(mode -> mode.getName().equals("dashboard") || mode.getName().equals("ImageGallery"))
                .collect(Collectors.toList());
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
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
