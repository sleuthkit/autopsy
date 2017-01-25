/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.Collections;
import javax.swing.AbstractAction;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.RunIngestModuleWizardWizardIterator;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;

/**
 * This class is used to add the action to the run ingest modules menu item.
 * When the data source is pressed, it should open the wizard for ingest
 * modules.
 */
final class RunIngestModulesAction extends AbstractAction {

    Content dataSource;

    /**
     * the constructor
     */
    public RunIngestModulesAction(Content dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Runs the ingest modules wizard on the data source.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        WizardDescriptor wiz = new WizardDescriptor(new RunIngestModuleWizardWizardIterator());
        // {0} will be replaced by WizardDescriptor.Panel.getComponent().getName()
        wiz.setTitleFormat(new MessageFormat("{0}"));
        wiz.setTitle("Run Ingest Modules");
        if (DialogDisplayer.getDefault().notify(wiz) == WizardDescriptor.FINISH_OPTION) {
        }
    }
}
