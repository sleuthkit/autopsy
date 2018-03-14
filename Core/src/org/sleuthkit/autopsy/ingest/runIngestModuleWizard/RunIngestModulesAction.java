/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.RootPaneContainer;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

/**
 * An action that invokes the Run Ingest Modules wizard for one or more data
 * sources or a subset of the files in a single data source.
 */
public final class RunIngestModulesAction extends AbstractAction {

    @Messages("RunIngestModulesAction.name=Run Ingest Modules")
    private static final long serialVersionUID = 1L;

    /*
     * Note that the execution context is the name of the dialog that used to be
     * used instead of this wizard and is retained for backwards compatibility.
     */
    private static final String EXECUTION_CONTEXT = "org.sleuthkit.autopsy.ingest.RunIngestModulesDialog";
    private final List<Content> dataSources = new ArrayList<>();
    private final IngestJobSettings.IngestType ingestType;
    private final List<AbstractFile> files;

    /**
     * Display any warnings that the ingestJobSettings have.
     *
     * @param ingestJobSettings
     */
    private static void showWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder(1024);
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), warningMessage.toString());
        }
    }

    /**
     * Constructs an action that invokes the Run Ingest Modules wizard for one
     * or more data sources.
     *
     * @param dataSources - the data sources you want to run ingest on
     */
    public RunIngestModulesAction(List<Content> dataSources) {
        this.putValue(Action.NAME, Bundle.RunIngestModulesAction_name());
        this.dataSources.addAll(dataSources);
        this.ingestType = IngestJobSettings.IngestType.ALL_MODULES;
        this.files = Collections.emptyList();
    }

    /**
     * Constructs an action that invokes the Run Ingest Modules wizard for a
     * subset of the files in a single data source.
     *
     * @param dataSource The data source.
     * @param files      The files.
     */
    public RunIngestModulesAction(Content dataSource, List<AbstractFile> files) {
        this.putValue(Action.NAME, Bundle.RunIngestModulesAction_name());
        this.dataSources.add(dataSource);
        this.ingestType = IngestJobSettings.IngestType.FILES_ONLY;
        this.files = new ArrayList<>(files);
    }

    /**
     * Opens a run ingest modules wizard with the list of data sources.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        /**
         * Create and display a Run Ingest Modules wizard. Note that the
         * argument in the title format string will be supplied by
         * WizardDescriptor.Panel.getComponent().getName().
         */
        RootPaneContainer root = (RootPaneContainer) WindowManager.getDefault().getMainWindow();
        root.getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        root.getGlassPane().setVisible(true);
        RunIngestModulesWizardIterator wizard = new RunIngestModulesWizardIterator(EXECUTION_CONTEXT, this.ingestType, this.dataSources);
        WizardDescriptor wiz = new WizardDescriptor(wizard);
        wiz.setTitleFormat(new MessageFormat("{0}"));
        wiz.setTitle(Bundle.RunIngestModulesAction_name());
        root.getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        root.getGlassPane().setVisible(false);
        if (DialogDisplayer.getDefault().notify(wiz) == WizardDescriptor.FINISH_OPTION) {
            IngestJobSettings ingestJobSettings = wizard.getIngestJobSettings();
            showWarnings(ingestJobSettings);
            if (this.files.isEmpty()) {
                IngestManager.getInstance().queueIngestJob(this.dataSources, ingestJobSettings);
            } else {
                IngestManager.getInstance().queueIngestJob(this.dataSources.get(0), this.files, ingestJobSettings);
            }
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Clone is not supported for the RunIngestModulesAction");
    }
}
