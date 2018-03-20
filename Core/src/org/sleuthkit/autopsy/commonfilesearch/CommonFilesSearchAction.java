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
package org.sleuthkit.autopsy.commonfilesearch;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import java.util.logging.Level;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Encapsulates a menu action which triggers the common files search dialog.
 */
final class CommonFilesSearchAction extends CallableSystemAction {

    private static final Logger LOGGER = Logger.getLogger(CommonFilesPanel.class.getName());
    
    private static CommonFilesSearchAction instance = null;
    private static final long serialVersionUID = 1L;

    CommonFilesSearchAction() {
        super();
        this.setEnabled(true); //this.caseHasMultipleSources());
//        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
//            if (evt.getPropertyName().equals(Case.Events.CURRENT_CASE.toString())) {
//                this.setEnabled(evt.getNewValue() != null && this.caseHasMultipleSources());
//            }
//        });
    }
    
    private boolean caseHasMultipleSources(){
        try {
            Case currentCase = Case.getOpenCase();
            SleuthkitCase tskDb = currentCase.getSleuthkitCase();
            return tskDb.getDataSources().size() >= 2;
        } catch (NoCurrentCaseException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Unable to count datasources.", ex);
        }
        return false;
    }

    public static synchronized CommonFilesSearchAction getDefault() {
        if (instance == null) {
            instance = new CommonFilesSearchAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new CommonFilesDialog().setVisible(true);
    }

    @Override
    public void performAction() {
        new CommonFilesDialog().setVisible(true);
    }

    @NbBundle.Messages({
        "CommonFilesAction.getName.text=Common Files Search"})
    @Override
    public String getName() {
        return Bundle.CommonFilesAction_getName_text();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
