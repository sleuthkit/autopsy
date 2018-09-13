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
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Encapsulates a menu action which triggers the common files search dialog.
 */
final public class CommonAttributeSearchAction extends CallableSystemAction {

    private static final Logger LOGGER = Logger.getLogger(CommonAttributeSearchAction.class.getName());
    
    private static CommonAttributeSearchAction instance = null;
    private static final long serialVersionUID = 1L;
    
    CommonAttributeSearchAction() {
        super();
        this.setEnabled(false);
    }

    @Override
    public boolean isEnabled(){
        boolean shouldBeEnabled = false;
        try {
            //dont refactor any of this to pull out common expressions - order of evaluation of each expression is significant
            shouldBeEnabled = 
                    (Case.isCaseOpen() && 
                    Case.getCurrentCase().getDataSources().size() > 1) 
                    || 
                    (EamDb.isEnabled() && 
                    EamDb.getInstance() != null && 
                    EamDb.getInstance().getCases().size() > 1 && 
                    Case.isCaseOpen() &&
                    Case.getCurrentCase() != null && 
                    EamDb.getInstance().getCase(Case.getCurrentCase()) != null);
                    
        } catch(TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error getting data sources for action enabled check", ex);
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting CR cases for action enabled check", ex);
        }
        return super.isEnabled() && shouldBeEnabled;
    }

    public static synchronized CommonAttributeSearchAction getDefault() {
        if (instance == null) {
            instance = new CommonAttributeSearchAction();
        }
        return instance;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        new CommonAttributePanel().setVisible(true);
    }

    @Override
    public void performAction() {
        new CommonAttributePanel().setVisible(true);
    }

    @NbBundle.Messages({
        "CommonAttributeSearchAction.getName.text=Common Attribute Search"})
    @Override
    public String getName() {
        return Bundle.CommonAttributeSearchAction_getName_text();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
