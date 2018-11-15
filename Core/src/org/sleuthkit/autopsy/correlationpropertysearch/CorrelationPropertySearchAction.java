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
package org.sleuthkit.autopsy.correlationpropertysearch;

import java.awt.event.ActionEvent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;

/**
 * Action for accessing the Correlation Property Search dialog.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.correlationpropertysearch.CorrelationPropertySearchAction")
@ActionRegistration(displayName = "#CTL_CorrelationPropertySearchAction=Correlation Property Search", lazy = false)
@ActionReference(path = "Menu/Tools", position = 104)
@NbBundle.Messages({"CTL_CorrelationPropertySearchAction=Correlation Property Search"})
public class CorrelationPropertySearchAction extends CallableSystemAction {

    @Override
    public boolean isEnabled() {
        return EamDb.isEnabled() && Case.isCaseOpen();
    }
    
    @Override
    public void actionPerformed(ActionEvent event) {
        performAction();
    }

    @Override
    public void performAction() {
        CorrelationPropertySearchDialog dialog = new CorrelationPropertySearchDialog();
        dialog.display();
    }

    @NbBundle.Messages({
        "CorrelationPropertySearchAction.getName.text=Correlation Property Search"})
    @Override
    public String getName() {
        return Bundle.CorrelationPropertySearchAction_getName_text();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
    
}
