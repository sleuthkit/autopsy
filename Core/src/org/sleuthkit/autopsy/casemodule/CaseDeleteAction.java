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

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.logging.Level;;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;

/**
 * The action to delete the current Case. This class should be disabled on
 * creation and it will be enabled on new case creation or case opened.
 */
 final class CaseDeleteAction extends CallableSystemAction {

    private JPanel caller; // for error handling
    
    private static final Logger logger = Logger.getLogger(CaseDeleteAction.class.getName());

    /**
     * The constructor for this class
     */
    public CaseDeleteAction() {
        putValue(Action.NAME, NbBundle.getMessage(CaseDeleteAction.class, "CTL_CaseDeleteAction")); // put the action Name
        this.setEnabled(false);
    }

    /**
     * Deletes the current opened case.
     * @param e
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Logger.noteAction(this.getClass());

        Case currentCase = Case.getCurrentCase();
        File configFile = new File(currentCase.getConfigFilePath());
        File caseFolder = new File(configFile.getParent());
        String caseName = currentCase.getName();
        if(!caseFolder.exists()){
            // throw an error
            
            logger.log(Level.WARNING, "Couldn't delete case.", new Exception("The case directory doesn't exist."));
        }
        else{
            // show the confirmation first to close the current case and open the "New Case" wizard panel
            String closeCurrentCase = NbBundle.getMessage(this.getClass(), "CaseDeleteAction.closeConfMsg.text",                                                          caseName, caseFolder.getPath());
            NotifyDescriptor d = new NotifyDescriptor.Confirmation(closeCurrentCase,
                                                                   NbBundle.getMessage(this.getClass(),
                                                                                       "CaseDeleteAction.closeConfMsg.title"),
                                                                   NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
            d.setValue(NotifyDescriptor.NO_OPTION);

            Object res = DialogDisplayer.getDefault().notify(d);
            if(res != null && res == DialogDescriptor.YES_OPTION){
                boolean success = false;
                
                try {
                    Case.getCurrentCase().deleteCase(caseFolder); // delete the current case
                    success = true;
                } catch (CaseActionException ex) {
                    logger.log(Level.WARNING, "Could not delete the case folder: " + caseFolder);
                }

                // show notification whether the case has been deleted or it failed to delete...
                if(!success){
                    JOptionPane.showMessageDialog(caller,
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "CaseDeleteAction.msgDlg.fileInUse.msg"),
                                                  NbBundle.getMessage(this.getClass(),
                                                                      "CaseDeleteAction.msgDlg.fileInUse.title"),
                                                  JOptionPane.ERROR_MESSAGE); // throw an error
                }
                else{
                    CasePropertiesAction.closeCasePropertiesWindow(); // because the "Delete Case" button is in the "CaseProperties" window, we have to close that window when we delete the case.
                    JOptionPane.showMessageDialog(caller, NbBundle.getMessage(this.getClass(),
                                                                              "CaseDeleteAction.msgDlg.caseDelete.msg",
                                                                              caseName));
                }
            }
        }
    }

    /**
     * This method does nothing. Use the "actionPerformed(ActionEvent e)" instead of this method.
     */
    @Override
    public void performAction() {
        // Note: I use the actionPerformed above instead of this method
    }

    /**
     * Gets the name of this action. This may be presented as an item in a menu.
     * @return actionName
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(CaseDeleteAction.class, "CTL_CaseDeleteAction");
    }

    /**
     * Gets the HelpCtx associated with implementing object
     * @return HelpCtx or HelpCtx.DEFAULT_HELP
     */
    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
