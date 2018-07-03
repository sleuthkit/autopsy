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
import java.util.logging.Level;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Encapsulates a menu action which triggers the common files search dialog.
 */
final public class CommonFilesSearchAction extends CallableSystemAction {

    private static CommonFilesSearchAction instance = null;
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CommonFilesSearchAction.class.getName());
    
    CommonFilesSearchAction() {
        super();
        this.setEnabled(false);
    }

    @Override
    public boolean isEnabled(){
        boolean shouldBeEnabled = false;
        try {
            shouldBeEnabled = Case.isCaseOpen() && Case.getCurrentCase().getDataSources().size() > 1;
        } catch(TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting data sources for action enabled check", ex);
        }
        return super.isEnabled() && shouldBeEnabled;
    }

    public static synchronized CommonFilesSearchAction getDefault() {
        if (instance == null) {
            instance = new CommonFilesSearchAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
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
