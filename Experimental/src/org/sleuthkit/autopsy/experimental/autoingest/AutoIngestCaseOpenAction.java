/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.casemodule.CaseCloseAction;
import org.sleuthkit.autopsy.casemodule.CaseOpenAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.StartupWindowProvider;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;

final class AutoIngestCaseOpenAction extends CallableSystemAction implements ActionListener {

    private static final Logger logger = Logger.getLogger(AutoIngestCaseOpenAction.class.getName());
    private static final long serialVersionUID = 1L;

    public AutoIngestCaseOpenAction() {
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        AutoIngestUserPreferences.SelectedMode mode = AutoIngestUserPreferences.getMode();
        switch (mode) {
            case REVIEW:

                if (Case.isCaseOpen()) {
                    /*
                     * In review mode, close the currently open case, if any, and
                     * then display the review mode cases panel. This can be
                     * accomplished by invoking CaseCloseAction because it calls
                     * StartupWindowProvider.getInstance().open() after it closes
                     * the current case.
                     */
                    SystemAction.get(CaseCloseAction.class).actionPerformed(e);
                } else {
                    // no case is open, so show the startup window
                    StartupWindowProvider.getInstance().open();
                }
                break;
                
            case AUTOMATED:
                /*
                 * New case action is disabled in auto ingest mode.
                 */               
                break;
                
            case STANDALONE:
                /**
                 * In standalone mode, invoke default Autopsy version of CaseOpenAction.
                 */
                Lookup.getDefault().lookup(CaseOpenAction.class).actionPerformed(e);
                break;


            default:
                logger.log(Level.SEVERE, "Attempting to open case in unsupported mode {0}", mode.toString());
        }
    }

    @Override
    public void performAction() {
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(AutoIngestCaseOpenAction.class, "CTL_OpenAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

}
