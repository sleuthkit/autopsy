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
package org.sleuthkit.autopsy.hashdatabase;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.coreutils.AutopsyPropFile;
import org.sleuthkit.autopsy.coreutils.Log;

/**
 * The HashDbMgmtAction opens the HashDbMgmtPanel in a dialog, and saves the
 * settings of the panel if the Apply button is clicked.
 * @author pmartel
 */
class HashDbMgmtAction extends CallableSystemAction {

    private static final String ACTION_NAME = "Hash Database Management";

    @Override
    public void performAction() {
        Log.noteAction(this.getClass());

        try {
            // Load settings from the property file
            HashDbSettings hashDatabaseSettings = HashDbSettings.getHashDbSettings();

            // create the popUp window for it
            final JFrame frame = new JFrame(ACTION_NAME);
            final JDialog popUpWindow = new JDialog(frame, ACTION_NAME, true); // to make the popUp Window to be modal

            // initialize panel with loaded settings
            final HashDbMgmtPanel panel = new HashDbMgmtPanel(hashDatabaseSettings);
            panel.setPreferredSize(new Dimension(360, 300));

            // set action for the "Apply" button
            panel.setApplyButtonActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (panel.indexesExist()) {
                        try {
                            panel.saveSettings();
                        } catch (IOException ex) {
                            Log.get(HashDbMgmtAction.class).log(Level.WARNING, "Couldn't save hash database settings.", ex);
                        }
                        popUpWindow.dispose();
                    } else {
                        NotifyDescriptor d = new NotifyDescriptor.Message("All selected databases must have indexes.", NotifyDescriptor.INFORMATION_MESSAGE);
                        DialogDisplayer.getDefault().notify(d);
                    }
                }
            });

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
            Log.get(HashDbMgmtAction.class).log(Level.WARNING, "Error displaying " + ACTION_NAME + " window.", ex);
        }
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
    @Override
    protected boolean asynchronous() {
        return false;
    }
}
