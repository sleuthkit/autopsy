/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest.runIngestModuleWizard;

import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Directory;

/**
 * This class is used to add the action to the run ingest modules menu item.
 * When the data source is pressed, it should open the wizard for ingest
 * modules.
 */
public final class RunIngestModulesAction extends AbstractAction {
   
    @Messages("RunIngestModulesAction.name=Run Ingest Modules")
    
    //'dialog' context name required so existing settings do not need to be reconfigured 
    private static final String DEFAULT_CONTEXT = "org.sleuthkit.autopsy.ingest.RunIngestModulesDialog";

    /**
     * Returns the name of the default context which will be used when profiles are not available.
     * 
     * @return the DEFAULT_CONTEXT
     */
    static String getDefaultContext() {
        return DEFAULT_CONTEXT;
    }

   
    private final List<Content> dataSources = new ArrayList<>();
    private final IngestJobSettings.IngestType ingestType;
    
    /**
     * Creates an action which will make a run ingest modules wizard when it 
     * is performed.
     * 
     * @param dataSources - the data sources you want to run ingest on
     */
    public RunIngestModulesAction(List<Content> dataSources) {
        this.putValue(Action.NAME, Bundle.RunIngestModulesAction_name());
        this.dataSources.addAll(dataSources);
        this.ingestType = IngestJobSettings.IngestType.ALL_MODULES;
    }
    
    /**
     * Creates an action which will make a run ingest modules wizard when it 
     * is performed.
     * 
     * @param dir - the directory you want to run ingest on
     */
    public RunIngestModulesAction(Directory dir) {
        this.putValue(Action.NAME, Bundle.RunIngestModulesAction_name());
        this.dataSources.add(dir);
        this.ingestType = IngestJobSettings.IngestType.FILES_ONLY;
    }
    /**
     * Opens a run ingest modules wizard with the list of data sources.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        WizardDescriptor wiz = new WizardDescriptor(new RunIngestModulesWizardIterator());
        // {0} will be replaced by WizardDescriptor.Panel.getComponent().getName()
        wiz.setTitleFormat(new MessageFormat("{0}"));
        wiz.setTitle(Bundle.RunIngestModulesAction_name());
        
        if (DialogDisplayer.getDefault().notify(wiz) == WizardDescriptor.FINISH_OPTION) {
            String executionContext = (String)wiz.getProperty("executionContext"); //NON-NLS 
            IngestJobSettings ingestJobSettings = new IngestJobSettings(executionContext, this.ingestType);
            showWarnings(ingestJobSettings);
            IngestManager.getInstance().queueIngestJob(this.dataSources, ingestJobSettings);
        }
    }

    /**
     * Display any warnings that the ingestJobSettings have.
     * 
     * @param ingestJobSettings 
     */
    private static void showWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder();
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(null, warningMessage.toString());
        }
    }
}
