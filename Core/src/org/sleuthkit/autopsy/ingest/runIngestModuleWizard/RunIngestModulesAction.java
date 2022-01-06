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
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.RootPaneContainer;
import org.apache.commons.collections.CollectionUtils;
import org.openide.DialogDisplayer;
import org.openide.WizardDescriptor;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/**
 * An action that invokes the Run Ingest Modules wizard for one or more data
 * sources or for the children of a file.
 */
public final class RunIngestModulesAction extends AbstractAction {

    @Messages("RunIngestModulesAction.name=Run Ingest Modules")
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(RunIngestModulesAction.class.getName());

    /*
     * Note that the execution context is the name of the dialog that used to be
     * used instead of this wizard and is retained for backwards compatibility.
     */
    private static final String EXECUTION_CONTEXT = "org.sleuthkit.autopsy.ingest.RunIngestModulesDialog";
    private final IngestJobSettings.IngestType ingestType;
    private final Long parentFileId;
    private AbstractFile parentFile = null;
    private final List<Long> dataSourceIds;
    private List<Content> dataSources = null;

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
        this(dataSources, dataSources.stream().map(ds -> ds.getId()).collect(Collectors.toList()));
    }

    /**
     * Constructs an action that invokes the Run Ingest Modules wizard for one
     * or more data sources.
     *
     * @param dataSourceIds - the data source ids you want to run ingest on
     */
    public static RunIngestModulesAction createDataSourceIdsAction(List<Long> dataSourceIds) {
        return new RunIngestModulesAction(null, dataSourceIds);
    }

    private RunIngestModulesAction(List<Content> dataSources, List<Long> dataSourceIds) {
        this.putValue(Action.NAME, Bundle.RunIngestModulesAction_name());
        this.dataSources = new ArrayList<>(dataSources);
        this.ingestType = IngestJobSettings.IngestType.ALL_MODULES;
        this.parentFile = null;
        this.parentFileId = null;
        this.dataSourceIds = new ArrayList<>(dataSourceIds);
    }

    /**
     * Constructs an action that invokes the Run Ingest Modules wizard for the
     * children of a file.
     *
     * @param parentFile The file.
     */
    public RunIngestModulesAction(AbstractFile parentFile) {
        this(parentFile, parentFile.getId());
    }

    /**
     * Constructs an action that invokes the Run Ingest Modules wizard for the
     * children of a file.
     *
     * @param parentFileId The file id.
     */
    public RunIngestModulesAction(long parentFileId) {
        this(null, parentFileId);
    }

    private RunIngestModulesAction(AbstractFile parentFile, Long parentFileId) {
        this.putValue(Action.NAME, Bundle.RunIngestModulesAction_name());
        this.parentFile = parentFile;
        this.parentFileId = parentFileId;
        this.dataSourceIds = null;
        this.ingestType = IngestJobSettings.IngestType.FILES_ONLY;
        try {
            this.setEnabled(parentFile.hasChildren());
        } catch (TskCoreException ex) {
            this.setEnabled(false);
            logger.log(Level.SEVERE, String.format("Failed to get children count for parent file %s (objId=%d), RunIngestModulesAction disabled", parentFile.getName(), parentFile.getId()), ex);
            MessageNotifyUtil.Message.error(Bundle.RunIngestModulesAction_actionPerformed_errorMessage());
        }
    }

    /**
     * Opens a run ingest modules wizard with the list of data sources.
     *
     * @param e the action event
     */
    @Messages({
        "RunIngestModulesAction.actionPerformed.errorMessage=Error querying the case database for the selected item."
    })
    @Override
    public synchronized void actionPerformed(ActionEvent e) {

        if (parentFile == null && parentFileId != null) {
            try {
                parentFile = Case.getCurrentCaseThrows().getSleuthkitCase().getAbstractFileById(parentFileId);
            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Failed to acquire parentFile from parentFileId (objId=%d), action failed", parentFileId), ex);
                MessageNotifyUtil.Message.error(Bundle.RunIngestModulesAction_actionPerformed_errorMessage());
                return;
            }
        } else if (CollectionUtils.isEmpty(dataSources) && !CollectionUtils.isEmpty(dataSourceIds)) {
            try {
                dataSources = new ArrayList<>();
                for (Long contentId : dataSourceIds) {
                    dataSources.add(Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(contentId));
                }
            } catch (NoCurrentCaseException | TskCoreException | TskDataException ex) {
                logger.log(Level.SEVERE, "Failed to acquire data sources for list: " + dataSourceIds.stream().map(id -> Long.toString(id)).collect(Collectors.joining(", ")), ex);
                MessageNotifyUtil.Message.error(Bundle.RunIngestModulesAction_actionPerformed_errorMessage());
                return;
            }
        }

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
            if (this.parentFile == null) {
                IngestManager.getInstance().queueIngestJob(this.dataSources, ingestJobSettings);
            } else {
                try {
                    Content dataSource = parentFile.getDataSource();
                    List<Content> children = parentFile.getChildren();
                    List<AbstractFile> files = new ArrayList<>();
                    for (Content child : children) {
                        if (child instanceof AbstractFile) {
                            files.add((AbstractFile) child);
                        }
                    }
                    if (!files.isEmpty()) {
                        IngestManager.getInstance().queueIngestJob(dataSource, files, ingestJobSettings);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to get data source or children for parent file %s (objId=%d), action failed", parentFile.getName(), parentFile.getId()), ex);
                    MessageNotifyUtil.Message.error(Bundle.RunIngestModulesAction_actionPerformed_errorMessage());
                }
            }
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException("Clone is not supported for the RunIngestModulesAction");
    }
}
