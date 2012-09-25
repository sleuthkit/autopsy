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
package org.sleuthkit.autopsy.report;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.util.HelpCtx;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The ReportFilterAction opens the reportFilterPanel in a dialog, and saves the
 * settings of the panel if the Apply button is clicked.
 *
 * @author pmartel
 */
class ReportFilterAction {

    private static final String ACTION_NAME = "Report Window";

    //@Override
    public void performAction() {
        Logger.noteAction(this.getClass());

        try {

            // create the popUp window for it
            Container cpane;
            final JFrame frame = new JFrame(ACTION_NAME);
            final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal
            cpane = frame.getContentPane();
            // initialize panel with loaded settings
            final ReportFilter panel = new ReportFilter();

            // add the panel to the popup window
            popUpWindow.add(panel);
            popUpWindow.pack();
            popUpWindow.setResizable(false);

            // set the location of the popUp Window on the center of the screen
            Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            double w = popUpWindow.getSize().getWidth();
            double h = popUpWindow.getSize().getHeight();
            popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));
            // display the window
            popUpWindow.setVisible(true);


        } catch (Exception ex) {
            Logger.getLogger(ReportFilterAction.class.getName()).log(Level.WARNING, "Error displaying " + ACTION_NAME + " window.", ex);
        }
    }

    //@Override
    public String getName() {
        return ACTION_NAME;
    }

    // @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
