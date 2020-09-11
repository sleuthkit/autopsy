/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import javax.swing.Action;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.ui.Bundle;

@ActionID(category = "Case", id = "org.sleuthkit.autopsy.casemodule.datasourcesummary.DataSourceSummaryAction")
@ActionRegistration(displayName = "#CTL_DataSourceSummaryAction", lazy = false)
@Messages({"CTL_DataSourceSummaryAction=Data Source Summary"})
/**
 * DataSourceSummaryAction action for the Case menu to activate a
 * ViewSummaryInformationAction selecting the first data source.
 */
public class DataSourceSummaryAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;

    /**
     * Create a datasource summary action which will be disabled when no case is
     * open.
     */
    DataSourceSummaryAction() {
        putValue(Action.NAME, Bundle.CTL_DataSourceSummaryAction());
        this.setEnabled(false);
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            setEnabled(null != evt.getNewValue());
        });
    }

    @Override
    public void performAction() {
        //perform the action of a ViewSummaryInformationAction with a ActionEvent which will not be used
        new ViewSummaryInformationAction(null).actionPerformed(new ActionEvent(Boolean.TRUE, 0, ""));
    }

    @Override
    public String getName() {
        return Bundle.CTL_DataSourceSummaryAction();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false;
    }
}
