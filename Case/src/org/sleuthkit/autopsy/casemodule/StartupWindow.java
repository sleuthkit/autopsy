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


package org.sleuthkit.autopsy.casemodule;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 * Displays 
 */
public final class StartupWindow extends JDialog {

    private static StartupWindow instance;
    private static final String TITLE = "Welcome";
    private static Dimension DIMENSIONS = new Dimension(750, 400);

    private StartupWindow(JFrame frame, String title, boolean isModal) {
        super(frame, title, isModal);
    }

    /**
     * Get the startup window
     * @return the startup window singleton
     */
    public static synchronized StartupWindow getInstance() {
        if (StartupWindow.instance == null) {
            JFrame frame = new JFrame(TITLE);
            boolean isModal = true;
            StartupWindow.instance = new StartupWindow(frame, TITLE, isModal);
        }


        return instance;
    }

    /**
     * Shows the startup window.
     */
    public void display() {

        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();

        // set the popUp window / JFrame
        setSize(DIMENSIONS);
        int w = this.getSize().width;
        int h = this.getSize().height;

        // set the location of the popUp Window on the center of the screen
        setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);

        CueBannerPanel welcomeWindow = new CueBannerPanel();

        // add the command to close the window to the button on the Volume Detail Panel
        welcomeWindow.setCloseButtonActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });

        add(welcomeWindow);
        pack();
        setResizable(false);
        setVisible(true);

    }

    /**
     * Closes the startup window.
     */
    public void close() {
        this.dispose();
    }
}
