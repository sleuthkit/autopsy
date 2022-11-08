/*
 * Autopsy
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.apputils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.apache.commons.io.FileUtils;
import org.openide.LifecycleManager;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Class to open the Discovery dialog. Allows the user to run searches and see
 * results in the DiscoveryTopComponent.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.apputils.ResetWindowsAction")
@ActionReferences(value = {
    @ActionReference(path = "Menu/Window", position = 205)})
@ActionRegistration(displayName = "#CTL_ResetWindowsAction", lazy = false)
@NbBundle.Messages({"CTL_ResetWindowsAction=Reset Windows"})
public final class ResetWindowsAction extends CallableSystemAction {

    private static final String DISPLAY_NAME = Bundle.CTL_ResetWindowsAction();
    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(ResetWindowsAction.class.getName());
    private final static String WINDOWS2LOCAL = "Windows2Local";
    private final static String CASE_TO_REOPEN_FILE = "caseToOpen.txt"; 

    @Override
    public boolean isEnabled() {
        return true;
    }

    @NbBundle.Messages({"ResetWindowAction.confirm.text=In order to perform the resetting of window locations the software will close and restart. "
        + "If a case is currently open, it will be closed. If ingest or a search is currently running, it will be terminated. "
        + "Are you sure you want to restart the software to reset all window locations?",
        "ResetWindowAction.caseCloseFailure.text=Unable to close the current case, "
        + "the software will restart and the windows locations will reset the next time the software is closed.",
        "ResetWindowAction.caseSaveMetadata.text=Unable to save current case path, "
        + "the software will restart and the windows locations will reset but the current case will not be opened upon restart."})

    @Override
    public void performAction() {
        SwingUtilities.invokeLater(() -> {
            boolean response = MessageNotifyUtil.Message.confirm(Bundle.ResetWindowAction_confirm_text());
            if (response) {
                //adding the shutdown hook, closing the current case, and marking for restart can be re-ordered if slightly different behavior is desired
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        try {
                            FileUtils.deleteDirectory(new File(PlatformUtil.getUserConfigDirectory() + File.separator + WINDOWS2LOCAL));
                        } catch (IOException ex) {
                            //While we would like the user to be aware of this in the unlikely event that the directory can not be deleted
                            //Because our deletion is being attempted in a shutdown hook I don't know that we can pop up UI elements during the shutdown proces
                            logger.log(Level.SEVERE, "Unable to delete config directory, window locations will not be reset. To manually reset the windows please delete the following directory while the software is closed. " + PlatformUtil.getUserConfigDirectory() + File.separator + "Windows2Local", ex);
                        }
                    }
                });
                try {
                    if (Case.isCaseOpen()) {
                        String caseMetadataFilePath = Case.getCurrentCase().getMetadata().getFilePath().toString();
                        File caseToOpenFile = new File(ResetWindowsAction.getCaseToReopenFilePath());
                        Charset encoding = null;  //prevents writeStringToFile from having ambiguous arguments
                        FileUtils.writeStringToFile(caseToOpenFile, caseMetadataFilePath, encoding);
                        Case.closeCurrentCase();
                    }
                    // The method markForRestart can not be undone once it is called.
                    LifecycleManager.getDefault().markForRestart();
                    //we need to call exit last 
                    LifecycleManager.getDefault().exit();
                } catch (CaseActionException ex) {
                    logger.log(Level.WARNING, Bundle.ResetWindowAction_caseCloseFailure_text(), ex);
                    MessageNotifyUtil.Message.show(Bundle.ResetWindowAction_caseCloseFailure_text(), MessageNotifyUtil.MessageType.ERROR);
                } catch (IOException ex) {
                    logger.log(Level.WARNING, Bundle.ResetWindowAction_caseSaveMetadata_text(), ex);
                    MessageNotifyUtil.Message.show(Bundle.ResetWindowAction_caseSaveMetadata_text(), MessageNotifyUtil.MessageType.ERROR);
                }
            }
        });
    }
    
    public static String getCaseToReopenFilePath(){
        return PlatformUtil.getUserConfigDirectory() + File.separator + CASE_TO_REOPEN_FILE;
    }

    /**
     * Set this action to be enabled/disabled
     *
     * @param value whether to enable this action or not
     */
    @Override

    public void setEnabled(boolean value) {
        super.setEnabled(value);
    }

    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}
