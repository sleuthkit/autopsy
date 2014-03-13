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
import java.util.Map;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.JFrame;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
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
    }

    /**
     * Pop-up the Case Properties Form window where user can change the case
     * properties (example: update case name and remove the image from the case)
     */
    @Override
    public void performAction() {
        Logger.noteAction(this.getClass());
        
        try {
            // create the popUp window for it
            String title = NbBundle.getMessage(this.getClass(), "CasePropertiesAction.window.title");
            final JFrame frame = new JFrame(title);
            popUpWindow = new JDialog(frame, title, true); // to make the popUp Window to be modal


            // get the information that needed
            Case currentCase = Case.getCurrentCase();
            String caseName = currentCase.getName();
            String crDate = currentCase.getCreatedDate();
            String caseDir = currentCase.getCaseDirectory();
            int totalImage = currentCase.getRootObjectsCount();

            // put the image paths information into hashmap
            Map<Long, String> imgPaths = Case.getImagePaths(currentCase.getSleuthkitCase());

            // create the case properties form
            CasePropertiesForm cpf = new CasePropertiesForm(currentCase, crDate, caseDir, imgPaths);

            // add the command to close the window to the button on the Case Properties form / panel
            cpf.setOKButtonActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    popUpWindow.dispose();
                }
            });

            // add the case properties form / panel to the popup window
            popUpWindow.add(cpf);
            popUpWindow.pack();
            popUpWindow.setResizable(false);

            // set the location of the popUp Window on the center of the screen
            Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
            double w = popUpWindow.getSize().getWidth();
            double h = popUpWindow.getSize().getHeight();
            popUpWindow.setLocation((int) ((screenDimension.getWidth() - w) / 2), (int) ((screenDimension.getHeight() - h) / 2));

            popUpWindow.setVisible(true);
        } catch (Exception ex) {
            Logger.getLogger(CasePropertiesAction.class.getName()).log(Level.WARNING, "Error displaying Case Properties window.", ex);
        }
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     * @return actionName
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(CasePropertiesAction.class, "CTL_CasePropertiesAction");
    }

    /**
     * Gets the HelpCtx associated with implementing object
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
