/*
 * Enterprise Artifacts Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;

/**
 * Action to update case details in enterprise artifacts manager database
 */
@ActionID(
        category = "Case",
        id = "org.sleuthkit.enterpriseartifactsmanager.actions.EnterpriseArtifactsManagerCaseEditCaseInfoAction"
)
@ActionRegistration(
        displayName = "#CTL_EnterpriseArtifactsManagerCaseEditCaseInfo",
        lazy = false
)
@ActionReference(path = "Menu/Case", position = 650, separatorAfter = 824)
@Messages("CTL_EnterpriseArtifactsManagerCaseEditCaseInfo=Enterprise Artifacts Manager Case Details")
public final class EamEditCaseInfoAction extends CallableSystemAction implements ActionListener {

    EamEditCaseInfoAction() {
        putValue(Action.NAME, Bundle.CTL_EnterpriseArtifactsManagerCaseEditCaseInfo()); // put the action Name
        this.setEnabled(true);
    }

    @Override
    public boolean isEnabled() {
        return EamDb.isEnabled() && Case.isCaseOpen();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        performAction();
    }

    @Override
    public void performAction() {

        EamCaseEditDetailsDialog caseInformationDialog = new EamCaseEditDetailsDialog();
    }

    @Override
    public String getName() {
        return Bundle.CTL_EnterpriseArtifactsManagerCaseEditCaseInfo();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }
}
