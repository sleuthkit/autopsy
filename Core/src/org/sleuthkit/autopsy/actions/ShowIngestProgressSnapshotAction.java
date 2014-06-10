/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.ingest.IngestProgressSnapShotDialog;
import org.sleuthkit.autopsy.ingest.IngestManager;

@ActionID(
        category = "Tools",
        id = "org.sleuthkit.autopsy.actions.ShowIngestProgressSnapshotAction")
@ActionRegistration(
        displayName = "#CTL_ShowIngestProgressSnapshotAction", lazy=false)
@ActionReference(path = "Menu/Tools", position = 800, separatorBefore = 550)
@Messages("CTL_ShowIngestProgressSnapshotAction=Get Ingest Progress Snapshot")
public final class ShowIngestProgressSnapshotAction extends SystemAction implements ActionListener {

    // RJCTODO: Bundle
//    private static final String ACTION_NAME = NbBundle.getMessage(ReportWizardAction.class, "ReportWizardAction.actionName.text");
    private static final String ACTION_NAME = "Get Ingest Progress Snapshot";
        
    public ShowIngestProgressSnapshotAction() {
        setEnabled(false);
        IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                setEnabled(IngestManager.getInstance().isIngestRunning());
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        IngestProgressSnapShotDialog dialog = new IngestProgressSnapShotDialog();
        dialog.display();
    }

    @Override
    public String getName() {
        return ACTION_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;        
    }
}
