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
package org.sleuthkit.autopsy.allcasessearch;

import java.awt.event.ActionEvent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Action for accessing the Search Other Cases dialog.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.allcasessearch.AllCasesSearchAction")
@ActionRegistration(displayName = "#CTL_OtherCasesSearchAction=Search Central Repository", lazy = false)
@ActionReference(path = "Menu/Tools", position = 201)
@NbBundle.Messages({"CTL_AllCasesSearchAction=Search Central Repository"})
public class AllCasesSearchAction extends CallableSystemAction {

    @Override
    public boolean isEnabled() {
        return CentralRepository.isEnabled() && Case.isCaseOpen();
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        performAction();
    }

    @Override
    public void performAction() {
        AllCasesSearchDialog dialog = new AllCasesSearchDialog();
        dialog.display();
    }

    @NbBundle.Messages({
        "AllCasesSearchAction.getName.text=Search Central Repository"})
    @Override
    public String getName() {
        return Bundle.AllCasesSearchAction_getName_text();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
}
