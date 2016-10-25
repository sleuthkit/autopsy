/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The action to pop up the Case Properties Form window. By using this form,
 * user can update the case properties (for example: updates the case name and
 * removes the image from the current case)
 *
 * @author jantonius
 */
final class CasePropertiesAction extends CallableSystemAction {

    private static JDialog popUpWindow;

    /**
     * The CasePropertiesAction constructor
     */
    CasePropertiesAction() {
        putValue(Action.NAME, NbBundle.getMessage(CasePropertiesAction.class, "CTL_CasePropertiesAction")); // put the action Name
        this.setEnabled(false);
        Case.addEventSubscriber(Case.Events.CURRENT_CASE.toString(), new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                popUpWindow = null;
            }
        });
    }

    /**
     * Pop-up the Case Properties Form window where user can change the case
     * properties (example: update case name and remove the image from the case)
     */
    @Override
    public void performAction() {
        if (popUpWindow == null) {
            // create the popUp window for it
            String title = NbBundle.getMessage(this.getClass(), "CasePropertiesAction.window.title");
            popUpWindow = new JDialog((JFrame) WindowManager.getDefault().getMainWindow(), title, false);
            try {

                CaseInformationPanel caseInformationPanel = new CaseInformationPanel();
                caseInformationPanel.addCloseButtonAction((ActionEvent e) -> {
                    popUpWindow.dispose();
                });

                popUpWindow.add(caseInformationPanel);
                popUpWindow.setResizable(true);
                popUpWindow.pack();

                // set the location of the popUp Window on the center of the screen
                Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
                double w = popUpWindow.getSize().getWidth();
                double h = popUpWindow.getSize().getHeight();
                popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));

                popUpWindow.setVisible(true);
            } catch (Exception ex) {
                Logger.getLogger(CasePropertiesAction.class.getName()).log(Level.WARNING, "Error displaying Case Properties window.", ex); //NON-NLS
            }
        }
        popUpWindow.setVisible(true);
        popUpWindow.toFront();
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     *
     * @return actionName
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(CasePropertiesAction.class, "CTL_CasePropertiesAction");
    }

    /**
     * Gets the HelpCtx associated with implementing object
     *
     * @return HelpCtx or HelpCtx.DEFAULT_HELP
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    static void closeCasePropertiesWindow() {
        popUpWindow.dispose();
    }
}
