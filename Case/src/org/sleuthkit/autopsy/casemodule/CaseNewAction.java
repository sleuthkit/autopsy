/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

package org.sleuthkit.autopsy.casemodule;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.util.actions.SystemAction;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Log;

/**
 * The action to create a new case. This action class is always enabled.
 *
 * @author jantonius
 */
@ServiceProvider(service = CaseNewAction.class)
public final class CaseNewAction implements ActionListener {

    private NewCaseWizardAction wizard = SystemAction.get(NewCaseWizardAction.class);

    /**
     * Calls the "New Case" wizard panel action.
     * @param e
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        Log.noteAction(this.getClass());

        wizard.performAction();
    }
}
