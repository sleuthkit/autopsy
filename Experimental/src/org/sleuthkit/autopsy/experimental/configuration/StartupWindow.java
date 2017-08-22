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
package org.sleuthkit.autopsy.experimental.configuration;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.WindowConstants;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.CueBannerPanel;
import org.sleuthkit.autopsy.casemodule.StartupWindowInterface;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestControlPanel;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestCasePanel;

/**
 * The default implementation of the Autopsy startup window
 */
@ServiceProvider(service = StartupWindowInterface.class)
public final class StartupWindow extends JDialog implements StartupWindowInterface {

    private static final String TITLE = NbBundle.getMessage(StartupWindow.class, "StartupWindow.title.text");
    private static Dimension DIMENSIONS = new Dimension(750, 400);
    private static CueBannerPanel welcomeWindow;
    private static final long serialVersionUID = 1L;
    private AutoIngestCasePanel caseManagementPanel = null;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();

    public StartupWindow() {
        super(WindowManager.getDefault().getMainWindow(), TITLE, false);
        init();
    }

    /**
     * Shows the startup window.
     */
    private void init() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();

        // set the popUp window / JFrame
        setSize(DIMENSIONS);
        int w = getSize().width;
        int h = getSize().height;

        // set the location of the popUp Window on the center of the screen
        setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        
        addPanelForMode();
        pack();
        setResizable(false);
    }

    @Override
    public void open() {
        
        if (caseManagementPanel != null) {
            caseManagementPanel.updateView();
            caseManagementPanel.setCursor(Cursor.getDefaultCursor());
        }
        
        if (welcomeWindow != null) {
            welcomeWindow.refresh();
        }
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    /**
     * Closes the startup window.
     */
    @Override
    public void close() {
        this.setVisible(false);
    }

    /**
     * Adds a panel to the dialog based on operational mode selected by the
     * user.
     */
    private void addPanelForMode() {
        UserPreferences.SelectedMode mode = UserPreferences.getMode();

        switch (mode) {
            case AUTOINGEST:
                this.setTitle(NbBundle.getMessage(StartupWindow.class, "StartupWindow.AutoIngestMode") + " (" + LOCAL_HOST_NAME + ")");
                setIconImage(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/frame.gif", false)); //NON-NLS
                this.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        AutoIngestControlPanel.getInstance().shutdown();
                    }
                });
                setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                add(AutoIngestControlPanel.getInstance());
                break;
            case REVIEW:
                this.setTitle(NbBundle.getMessage(StartupWindow.class, "StartupWindow.ReviewMode") + " (" + LOCAL_HOST_NAME + ")");
                caseManagementPanel = new AutoIngestCasePanel(this);
                setIconImage(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/frame.gif", false)); //NON-NLS
                add(caseManagementPanel);
                break;
            default:                
                welcomeWindow = new CueBannerPanel();
                // add the command to close the window to the button on the Volume Detail Panel
                welcomeWindow.setCloseButtonActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        close();
                    }
                });
                add(welcomeWindow);
                break;
        }
    }
}