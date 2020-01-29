/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Encapsulates a menu action which triggers the common files search dialog.
 */
final public class CommonAttributeSearchAction extends CallableSystemAction {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributeSearchAction.class.getName());

    private static CommonAttributeSearchAction instance = null;
    private static final long serialVersionUID = 1L;

    /**
     * Get the default CommonAttributeSearchAction.
     *
     * @return the default instance of this action
     */
    public static synchronized CommonAttributeSearchAction getDefault() {
        if (instance == null) {
            instance = new CommonAttributeSearchAction();
        }
        return instance;
    }

    /**
     * Create a CommonAttributeSearchAction for opening the common attribute
     * search dialog
     */
    private CommonAttributeSearchAction() {
        super();
        this.setEnabled(false);
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && Case.isCaseOpen();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        createAndShowPanel();
    }

    @Override
    public void performAction() {
        createAndShowPanel();
    }

    /**
     * Create the commonAttributePanel and display it.
     */
    @NbBundle.Messages({
        "CommonAttributeSearchAction.openPanel.intro=The find common properties feature is not available because:",
        "CommonAttributeSearchAction.openPanel.resolution=\n\nAddress one of these issues to enable this feature.",
        "CommonAttributeSearchAction.openPanel.noCaseOpen=\n  - No case is open.",
        "CommonAttributeSearchAction.openPanel.notEnoughDataSources=\n  - There are not multiple data sources in the current case.",
        "CommonAttributeSearchAction.openPanel.centralRepoDisabled=\n  - The Central Repository is disabled.",
        "CommonAttributeSearchAction.openPanel.caseNotInCentralRepo=\n  - The current case is not in the Central Repository.",
        "CommonAttributeSearchAction.openPanel.notEnoughCases=\n  - Fewer than 2 cases exist in the Central Repository.",
        "CommonAttributeSearchAction.openPanel.centralRepoInvalid=\n  - The Central Repository configuration is invalid."})
    private void createAndShowPanel() {
        new SwingWorker<Boolean, Void>() {

            String reason = Bundle.CommonAttributeSearchAction_openPanel_intro();

            @Override
            protected Boolean doInBackground() throws Exception {
                // Test whether we should open the common files panel
                if (!Case.isCaseOpen()) {
                    reason += Bundle.CommonAttributeSearchAction_openPanel_noCaseOpen();
                    return false;
                }
                if (Case.getCurrentCase().getDataSources().size() > 1) {
                    // There are enough data sources to run the intra case seach
                    return true;
                } else {
                    reason += Bundle.CommonAttributeSearchAction_openPanel_notEnoughDataSources();
                }
                if (!CentralRepository.isEnabled()) {
                    reason += Bundle.CommonAttributeSearchAction_openPanel_centralRepoDisabled();
                    return false;
                }
                if (CentralRepository.getInstance() == null) {
                    reason += Bundle.CommonAttributeSearchAction_openPanel_centralRepoInvalid();
                    return false;
                }
                if (CentralRepository.getInstance().getCases().size() < 2) {
                    reason += Bundle.CommonAttributeSearchAction_openPanel_notEnoughCases();
                    return false;
                }
                if (CentralRepository.getInstance().getCase(Case.getCurrentCase()) == null) {
                    reason += Bundle.CommonAttributeSearchAction_openPanel_caseNotInCentralRepo();
                    return false;
                }
                return true;
            }

            @Override

            protected void done() {
                super.done();
                try {
                    boolean openPanel = get();
                    if (openPanel) {
                        CommonAttributePanel commonAttributePanel = new CommonAttributePanel();
                        //In order to update errors the CommonAttributePanel needs to observe its sub panels
                        commonAttributePanel.observeSubPanels();
                        commonAttributePanel.setVisible(true);
                    } else {
                        reason += Bundle.CommonAttributeSearchAction_openPanel_resolution();
                        NotifyDescriptor descriptor = new NotifyDescriptor.Message(reason, NotifyDescriptor.INFORMATION_MESSAGE);
                        DialogDisplayer.getDefault().notify(descriptor);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "Unexpected exception while opening Find Common Properties", ex); //NON-NLS
                }
            }
        }
                .execute();
    }

    @NbBundle.Messages({
        "CommonAttributeSearchAction.getName.text=Find Common Properties"})
    @Override
    public String getName() {
        return Bundle.CommonAttributeSearchAction_getName_text();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
