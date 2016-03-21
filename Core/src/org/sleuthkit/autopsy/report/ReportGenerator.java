/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbQuery;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardAttribute.Type;

/**
 * Instances of this class use GeneralReportModules, TableReportModules and
 * FileReportModules to generate a report. If desired, displayProgressPanels()
 * can be called to show report generation progress using ReportProgressPanel
 * objects displayed using a dialog box.
 */
class ReportGenerator {

    private static final Logger logger = Logger.getLogger(ReportGenerator.class.getName());

    private Case currentCase = Case.getCurrentCase();
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();

    private Map<TableReportModule, ReportProgressPanel> tableProgress;
    private Map<GeneralReportModule, ReportProgressPanel> generalProgress;
    private Map<FileReportModule, ReportProgressPanel> fileProgress;
    private Map<Integer, List<Column>> columnHeaderMap;

    private String reportPath;
    private ReportGenerationPanel panel = new ReportGenerationPanel();

    static final String REPORTS_DIR = "Reports"; //NON-NLS

    private List<String> errorList;

    /**
     * Displays the list of errors during report generation in user-friendly
     * way. MessageNotifyUtil used to display bubble notification.
     *
     * @param listOfErrors List of strings explaining the errors.
     */
    private void displayReportErrors() {
        if (!errorList.isEmpty()) {
            String errorString = "";
            for (String error : errorList) {
                errorString += error + "\n";
            }
            MessageNotifyUtil.Notify.error(
                    NbBundle.getMessage(this.getClass(), "ReportGenerator.notifyErr.errsDuringRptGen"), errorString);
            return;
        }
    }

    ReportGenerator(Map<TableReportModule, Boolean> tableModuleStates, Map<GeneralReportModule, Boolean> generalModuleStates, Map<FileReportModule, Boolean> fileListModuleStates) {
        // Create the root reports directory path of the form: <CASE DIRECTORY>/Reports/<Case fileName> <Timestamp>/
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String dateNoTime = dateFormat.format(date);
        this.reportPath = currentCase.getReportDirectory() + File.separator + currentCase.getName() + " " + dateNoTime + File.separator;

        this.errorList = new ArrayList<String>();

        // Create the root reports directory.
        try {
            FileUtil.createFolder(new File(this.reportPath));
        } catch (IOException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedMakeRptFolder"));
            logger.log(Level.SEVERE, "Failed to make report folder, may be unable to generate reports.", ex); //NON-NLS
            return;
        }

        // Initialize the progress panels
        generalProgress = new HashMap<>();
        tableProgress = new HashMap<>();
        fileProgress = new HashMap<>();
        setupProgressPanels(tableModuleStates, generalModuleStates, fileListModuleStates);
        this.columnHeaderMap = new HashMap<>();
    }

    /**
     * Create a ReportProgressPanel for each report generation module selected
     * by the user.
     *
     * @param tableModuleStates    The enabled/disabled state of each
     *                             TableReportModule
     * @param generalModuleStates  The enabled/disabled state of each
     *                             GeneralReportModule
     * @param fileListModuleStates The enabled/disabled state of each
     *                             FileReportModule
     */
    private void setupProgressPanels(Map<TableReportModule, Boolean> tableModuleStates, Map<GeneralReportModule, Boolean> generalModuleStates, Map<FileReportModule, Boolean> fileListModuleStates) {
        if (null != tableModuleStates) {
            for (Entry<TableReportModule, Boolean> entry : tableModuleStates.entrySet()) {
                if (entry.getValue()) {
                    TableReportModule module = entry.getKey();
                    String reportFilePath = module.getRelativeFilePath();
                    if (!reportFilePath.isEmpty()) {
                        tableProgress.put(module, panel.addReport(module.getName(), reportPath + reportFilePath));
                    } else {
                        tableProgress.put(module, panel.addReport(module.getName(), null));
                    }
                }
            }
        }

        if (null != generalModuleStates) {
            for (Entry<GeneralReportModule, Boolean> entry : generalModuleStates.entrySet()) {
                if (entry.getValue()) {
                    GeneralReportModule module = entry.getKey();
                    String reportFilePath = module.getRelativeFilePath();
                    if (!reportFilePath.isEmpty()) {
                        generalProgress.put(module, panel.addReport(module.getName(), reportPath + reportFilePath));
                    } else {
                        generalProgress.put(module, panel.addReport(module.getName(), null));
                    }
                }
            }
        }

        if (null != fileListModuleStates) {
            for (Entry<FileReportModule, Boolean> entry : fileListModuleStates.entrySet()) {
                if (entry.getValue()) {
                    FileReportModule module = entry.getKey();
                    String reportFilePath = module.getRelativeFilePath();
                    if (!reportFilePath.isEmpty()) {
                        fileProgress.put(module, panel.addReport(module.getName(), reportPath + reportFilePath));
                    } else {
                        fileProgress.put(module, panel.addReport(module.getName(), null));
                    }
                }
            }
        }
    }

    /**
     * Display the progress panels to the user, and add actions to close the
     * parent dialog.
     */
    public void displayProgressPanels() {
        final JDialog dialog = new JDialog(new JFrame(), true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setTitle(NbBundle.getMessage(this.getClass(), "ReportGenerator.displayProgress.title.text"));
        dialog.add(this.panel);
        dialog.pack();

        panel.addCloseAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                panel.close();
            }
        });

        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        int w = dialog.getSize().width;
        int h = dialog.getSize().height;

        // set the location of the popUp Window on the center of the screen
        dialog.setLocation((screenDimension.width - w) / 2, (screenDimension.height - h) / 2);
        dialog.setVisible(true);
    }

    /**
     * Run the GeneralReportModules using a SwingWorker.
     */
    public void generateGeneralReports() {
        GeneralReportsWorker worker = new GeneralReportsWorker();
        worker.execute();
    }

    /**
     * Run the TableReportModules using a SwingWorker.
     *
     * @param artifactTypeSelections the enabled/disabled state of the artifact
     *                               types to be included in the report
     * @param tagSelections          the enabled/disabled state of the tag names
     *                               to be included in the report
     */
    public void generateTableReports(Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections) {
        if (!tableProgress.isEmpty() && null != artifactTypeSelections) {
            TableReportsWorker worker = new TableReportsWorker(artifactTypeSelections, tagNameSelections);
            worker.execute();
        }
    }

    /**
     * Run the FileReportModules using a SwingWorker.
     *
     * @param enabledInfo the Information that should be included about each
     *                    file in the report.
     */
    public void generateFileListReports(Map<FileReportDataTypes, Boolean> enabledInfo) {
        if (!fileProgress.isEmpty() && null != enabledInfo) {
            List<FileReportDataTypes> enabled = new ArrayList<>();
            for (Entry<FileReportDataTypes, Boolean> e : enabledInfo.entrySet()) {
                if (e.getValue()) {
                    enabled.add(e.getKey());
                }
            }
            FileReportsWorker worker = new FileReportsWorker(enabled);
            worker.execute();
        }
    }

    /**
     * SwingWorker to run GeneralReportModules.
     */
    private class GeneralReportsWorker extends SwingWorker<Integer, Integer> {

        @Override
        protected Integer doInBackground() throws Exception {
            for (Entry<GeneralReportModule, ReportProgressPanel> entry : generalProgress.entrySet()) {
                GeneralReportModule module = entry.getKey();
                if (generalProgress.get(module).getStatus() != ReportStatus.CANCELED) {
                    module.generateReport(reportPath, generalProgress.get(module));
                }
            }
            return 0;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorTitle"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getLocalizedMessage(),
                        MessageNotifyUtil.MessageType.ERROR);
                logger.log(Level.SEVERE, "failed to generate reports", ex); //NON-NLS
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            } finally {
                displayReportErrors();
                errorList.clear();
            }
        }

    }

    /**
     * SwingWorker to run FileReportModules.
     */
    private class FileReportsWorker extends SwingWorker<Integer, Integer> {

        private List<FileReportDataTypes> enabledInfo = Arrays.asList(FileReportDataTypes.values());
        private List<FileReportModule> fileModules = new ArrayList<>();

        FileReportsWorker(List<FileReportDataTypes> enabled) {
            enabledInfo = enabled;
            for (Entry<FileReportModule, ReportProgressPanel> entry : fileProgress.entrySet()) {
                fileModules.add(entry.getKey());
            }
        }

        @Override
        protected Integer doInBackground() throws Exception {
            for (FileReportModule module : fileModules) {
                ReportProgressPanel progress = fileProgress.get(module);
                if (progress.getStatus() != ReportStatus.CANCELED) {
                    progress.start();
                    progress.updateStatusLabel(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.queryingDb.text"));
                }
            }

            List<AbstractFile> files = getFiles();
            int numFiles = files.size();
            for (FileReportModule module : fileModules) {
                module.startReport(reportPath);
                module.startTable(enabledInfo);
                fileProgress.get(module).setIndeterminate(false);
                fileProgress.get(module).setMaximumProgress(numFiles);
            }

            int i = 0;
            // Add files to report.
            for (AbstractFile file : files) {
                // Check to see if any reports have been cancelled.
                if (fileModules.isEmpty()) {
                    break;
                }
                // Remove cancelled reports, add files to report otherwise.
                Iterator<FileReportModule> iter = fileModules.iterator();
                while (iter.hasNext()) {
                    FileReportModule module = iter.next();
                    ReportProgressPanel progress = fileProgress.get(module);
                    if (progress.getStatus() == ReportStatus.CANCELED) {
                        iter.remove();
                    } else {
                        module.addRow(file, enabledInfo);
                        progress.increment();
                    }

                    if ((i % 100) == 0) {
                        progress.updateStatusLabel(
                                NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processingFile.text",
                                        file.getName()));
                    }
                }
                i++;
            }

            for (FileReportModule module : fileModules) {
                module.endTable();
                module.endReport();
                fileProgress.get(module).complete(ReportStatus.COMPLETE);
            }

            return 0;
        }

        /**
         * Get all files in the image.
         *
         * @return
         */
        private List<AbstractFile> getFiles() {
            List<AbstractFile> absFiles;
            try {
                SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
                absFiles = skCase.findAllFilesWhere("meta_type != " + TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue()); //NON-NLS
                return absFiles;
            } catch (TskCoreException ex) {
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorTitle"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getLocalizedMessage(),
                        MessageNotifyUtil.MessageType.ERROR);
                logger.log(Level.SEVERE, "failed to generate reports. Unable to get all files in the image.", ex); //NON-NLS
                return Collections.<AbstractFile>emptyList();
            }
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorTitle"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getLocalizedMessage(),
                        MessageNotifyUtil.MessageType.ERROR);
                logger.log(Level.SEVERE, "failed to generate reports", ex); //NON-NLS
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            } finally {
                displayReportErrors();
                errorList.clear();
            }
        }
    }

    /**
     * SwingWorker to run TableReportModules to report on blackboard artifacts,
     * content tags, and blackboard artifact tags.
     */
    private class TableReportsWorker extends SwingWorker<Integer, Integer> {

        private List<TableReportModule> tableModules = new ArrayList<>();
        private List<BlackboardArtifact.Type> artifactTypes = new ArrayList<>();
        private HashSet<String> tagNamesFilter = new HashSet<>();

        private List<Content> images = new ArrayList<>();

        TableReportsWorker(Map<BlackboardArtifact.Type, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections) {
            // Get the report modules selected by the user.
            for (Entry<TableReportModule, ReportProgressPanel> entry : tableProgress.entrySet()) {
                tableModules.add(entry.getKey());
            }

            // Get the artifact types selected by the user.
            for (Entry<BlackboardArtifact.Type, Boolean> entry : artifactTypeSelections.entrySet()) {
                if (entry.getValue()) {
                    artifactTypes.add(entry.getKey());
                }
            }

            // Get the tag names selected by the user and make a tag names filter.
            if (null != tagNameSelections) {
                for (Entry<String, Boolean> entry : tagNameSelections.entrySet()) {
                    if (entry.getValue() == true) {
                        tagNamesFilter.add(entry.getKey());
                    }
                }
            }
        }

        @Override
        protected Integer doInBackground() throws Exception {
            // Start the progress indicators for each active TableReportModule.
            for (TableReportModule module : tableModules) {
                ReportProgressPanel progress = tableProgress.get(module);
                if (progress.getStatus() != ReportStatus.CANCELED) {
                    module.startReport(reportPath);
                    progress.start();
                    progress.setIndeterminate(false);
                    progress.setMaximumProgress(this.artifactTypes.size() + 2); // +2 for content and blackboard artifact tags
                }
            }

            // report on the blackboard results
            makeBlackboardArtifactTables();

            // report on the tagged files and artifacts
            makeContentTagsTables();
            makeBlackboardArtifactTagsTables();

            // report on the tagged images
            makeThumbnailTable();

            // finish progress, wrap up
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).complete(ReportStatus.COMPLETE);
                module.endReport();
            }

            return 0;
        }

        /**
         * Generate the tables for the selected blackboard artifacts
         */
        private void makeBlackboardArtifactTables() {
            // Make a comment string describing the tag names filter in effect. 
            StringBuilder comment = new StringBuilder();
            if (!tagNamesFilter.isEmpty()) {
                comment.append(NbBundle.getMessage(this.getClass(), "ReportGenerator.artifactTable.taggedResults.text"));
                comment.append(makeCommaSeparatedList(tagNamesFilter));
            }

            // Add a table to the report for every enabled blackboard artifact type.
            for (BlackboardArtifact.Type type : artifactTypes) {
                // Check for cancellaton.
                removeCancelledTableReportModules();
                if (tableModules.isEmpty()) {
                    return;
                }

                for (TableReportModule module : tableModules) {
                    tableProgress.get(module).updateStatusLabel(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                                    type.getDisplayName()));
                }

                // Keyword hits and hashset hit artifacts get special handling.
                if (type.getTypeID() == ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                    writeKeywordHits(tableModules, comment.toString(), tagNamesFilter);
                    continue;
                } else if (type.getTypeID() == ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                    writeHashsetHits(tableModules, comment.toString(), tagNamesFilter);
                    continue;
                }

                List<ArtifactData> artifactList = getFilteredArtifacts(type, tagNamesFilter);

                if (artifactList.isEmpty()) {
                    continue;
                }

                /*
                 Gets all of the attribute types of this artifact type by adding
                 all of the types to a set
                 */
                Set<BlackboardAttribute.Type> attrTypeSet = new TreeSet<>((Type o1, Type o2) -> o1.getDisplayName().compareTo(o2.getDisplayName()));
                for (ArtifactData data : artifactList) {
                    List<BlackboardAttribute> attributes = data.getAttributes();
                    for (BlackboardAttribute attribute : attributes) {
                        attrTypeSet.add(attribute.getAttributeType());
                    }
                }
                // Get the columns appropriate for the artifact type. This is
                // used to get the data that will be in the cells below based on
                // type, and display the column headers.
                List<Column> columns = getArtifactTableColumns(type.getTypeID(), attrTypeSet);
                if (columns.isEmpty()) {
                    continue;
                }
                ReportGenerator.this.columnHeaderMap.put(type.getTypeID(), columns);

                // The artifact list is sorted now, as getting the row data is 
                // dependent on having the columns, which is necessary for 
                // sorting.
                Collections.sort(artifactList);
                List<String> columnHeaderNames = new ArrayList<>();
                for (Column currColumn : columns) {
                    columnHeaderNames.add(currColumn.getColumnHeader());
                }

                for (TableReportModule module : tableModules) {
                    module.startDataType(type.getDisplayName(), comment.toString());
                    module.startTable(columnHeaderNames);
                }
                for (ArtifactData artifactData : artifactList) {
                    // Add the row data to all of the reports.
                    for (TableReportModule module : tableModules) {

                        // Get the row data for this artifact, and has the 
                        // module add it.
                        List<String> rowData = artifactData.getRow();
                        if (rowData.isEmpty()) {
                            continue;
                        }

                        module.addRow(rowData);
                    }
                }
                // Finish up this data type
                for (TableReportModule module : tableModules) {
                    tableProgress.get(module).increment();
                    module.endTable();
                    module.endDataType();
                }
            }
        }

        /**
         * Make table for tagged files
         */
        @SuppressWarnings("deprecation")
        private void makeContentTagsTables() {
            // Check for cancellaton.
            removeCancelledTableReportModules();
            if (tableModules.isEmpty()) {
                return;
            }

            // Get the content tags.
            List<ContentTag> tags;
            try {
                tags = Case.getCurrentCase().getServices().getTagsManager().getAllContentTags();
            } catch (TskCoreException ex) {
                errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetContentTags"));
                logger.log(Level.SEVERE, "failed to get content tags", ex); //NON-NLS
                return;
            }

            // Tell the modules reporting on content tags is beginning.
            for (TableReportModule module : tableModules) {
                // @@@ This casting is a tricky little workaround to allow the HTML report module to slip in a content hyperlink.
                // @@@ Alos Using the obsolete ARTIFACT_TYPE.TSK_TAG_FILE is also an expedient hack.
                tableProgress.get(module).updateStatusLabel(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                                ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName()));
                ArrayList<String> columnHeaders = new ArrayList<>(Arrays.asList(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.tag"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.file"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.comment"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeModified"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeChanged"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeAccessed"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.timeCreated"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.size"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.htmlOutput.header.hash")));

                StringBuilder comment = new StringBuilder();
                if (!tagNamesFilter.isEmpty()) {
                    comment.append(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.makeContTagTab.taggedFiles.msg"));
                    comment.append(makeCommaSeparatedList(tagNamesFilter));
                }
                if (module instanceof ReportHTML) {
                    ReportHTML htmlReportModule = (ReportHTML) module;
                    htmlReportModule.startDataType(ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName(), comment.toString());
                    htmlReportModule.startContentTagsTable(columnHeaders);
                } else {
                    module.startDataType(ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName(), comment.toString());
                    module.startTable(columnHeaders);
                }
            }

            // Give the modules the rows for the content tags. 
            for (ContentTag tag : tags) {
                // skip tags that we are not reporting on 
                if (passesTagNamesFilter(tag.getName().getDisplayName()) == false) {
                    continue;
                }

                String fileName;
                try {
                    fileName = tag.getContent().getUniquePath();
                } catch (TskCoreException ex) {
                    fileName = tag.getContent().getName();
                }

                ArrayList<String> rowData = new ArrayList<>(Arrays.asList(tag.getName().getDisplayName(), fileName, tag.getComment()));
                for (TableReportModule module : tableModules) {
                    // @@@ This casting is a tricky little workaround to allow the HTML report module to slip in a content hyperlink.
                    if (module instanceof ReportHTML) {
                        ReportHTML htmlReportModule = (ReportHTML) module;
                        htmlReportModule.addRowWithTaggedContentHyperlink(rowData, tag);
                    } else {
                        module.addRow(rowData);
                    }
                }

                // see if it is for an image so that we later report on it
                checkIfTagHasImage(tag);
            }

            // The the modules content tags reporting is ended.
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endTable();
                module.endDataType();
            }
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                MessageNotifyUtil.Notify.show(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorTitle"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errors.reportErrorText") + ex.getCause().getLocalizedMessage(),
                        MessageNotifyUtil.MessageType.ERROR);
                logger.log(Level.SEVERE, "failed to generate reports", ex); //NON-NLS
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            } finally {
                displayReportErrors();
                errorList.clear();
            }
        }

        /**
         * Generate the tables for the tagged artifacts
         */
        @SuppressWarnings("deprecation")
        private void makeBlackboardArtifactTagsTables() {
            // Check for cancellaton.
            removeCancelledTableReportModules();
            if (tableModules.isEmpty()) {
                return;
            }

            List<BlackboardArtifactTag> tags;
            try {
                tags = Case.getCurrentCase().getServices().getTagsManager().getAllBlackboardArtifactTags();
            } catch (TskCoreException ex) {
                errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetBBArtifactTags"));
                logger.log(Level.SEVERE, "failed to get blackboard artifact tags", ex); //NON-NLS
                return;
            }

            // Tell the modules reporting on blackboard artifact tags data type is beginning.
            // @@@ Using the obsolete ARTIFACT_TYPE.TSK_TAG_ARTIFACT is an expedient hack.
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).updateStatusLabel(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                                ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getDisplayName()));
                StringBuilder comment = new StringBuilder();
                if (!tagNamesFilter.isEmpty()) {
                    comment.append(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.makeBbArtTagTab.taggedRes.msg"));
                    comment.append(makeCommaSeparatedList(tagNamesFilter));
                }
                module.startDataType(ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getDisplayName(), comment.toString());
                module.startTable(new ArrayList<>(Arrays.asList(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.resultType"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.tag"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.comment"),
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.tagTable.header.srcFile"))));
            }

            // Give the modules the rows for the content tags. 
            for (BlackboardArtifactTag tag : tags) {
                if (passesTagNamesFilter(tag.getName().getDisplayName()) == false) {
                    continue;
                }

                List<String> row;
                for (TableReportModule module : tableModules) {
                    row = new ArrayList<>(Arrays.asList(tag.getArtifact().getArtifactTypeName(), tag.getName().getDisplayName(), tag.getComment(), tag.getContent().getName()));
                    module.addRow(row);
                }

                // check if the tag is an image that we should later make a thumbnail for
                checkIfTagHasImage(tag);
            }

            // The the modules blackboard artifact tags reporting is ended.
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endTable();
                module.endDataType();
            }
        }

        /**
         * Test if the user requested that this tag be reported on
         *
         * @param tagName
         *
         * @return true if it should be reported on
         */
        private boolean passesTagNamesFilter(String tagName) {
            return tagNamesFilter.isEmpty() || tagNamesFilter.contains(tagName);
        }

        void removeCancelledTableReportModules() {
            Iterator<TableReportModule> iter = tableModules.iterator();
            while (iter.hasNext()) {
                TableReportModule module = iter.next();
                if (tableProgress.get(module).getStatus() == ReportStatus.CANCELED) {
                    iter.remove();
                }
            }
        }

        /**
         * Make a report for the files that were previously found to be images.
         */
        private void makeThumbnailTable() {
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).updateStatusLabel(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.createdThumb.text"));

                if (module instanceof ReportHTML) {
                    ReportHTML htmlModule = (ReportHTML) module;
                    htmlModule.startDataType(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.thumbnailTable.name"),
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.thumbnailTable.desc"));
                    List<String> emptyHeaders = new ArrayList<>();
                    for (int i = 0; i < ReportHTML.THUMBNAIL_COLUMNS; i++) {
                        emptyHeaders.add("");
                    }
                    htmlModule.startTable(emptyHeaders);

                    htmlModule.addThumbnailRows(images);

                    htmlModule.endTable();
                    htmlModule.endDataType();
                }
            }
        }

        /**
         * Analyze artifact associated with tag and add to internal list if it
         * is associated with an image.
         *
         * @param artifactTag
         */
        private void checkIfTagHasImage(BlackboardArtifactTag artifactTag) {
            AbstractFile file;
            try {
                file = Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(artifactTag.getArtifact().getObjectID());
            } catch (TskCoreException ex) {
                errorList.add(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.errGetContentFromBBArtifact"));
                logger.log(Level.WARNING, "Error while getting content from a blackboard artifact to report on.", ex); //NON-NLS
                return;
            }

            if (file != null) {
                checkIfFileIsImage(file);
            }
        }

        /**
         * Analyze file that tag is associated with and determine if it is an
         * image and should have a thumbnail reported for it. Images are added
         * to internal list.
         *
         * @param contentTag
         */
        private void checkIfTagHasImage(ContentTag contentTag) {
            Content c = contentTag.getContent();
            if (c instanceof AbstractFile == false) {
                return;
            }
            checkIfFileIsImage((AbstractFile) c);
        }

        /**
         * If file is an image file, add it to the internal 'images' list.
         *
         * @param file
         */
        private void checkIfFileIsImage(AbstractFile file) {

            if (file.isDir()
                    || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS
                    || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS) {
                return;
            }

            if (ImageUtils.thumbnailSupported(file)) {
                images.add(file);
            }
        }
    }

    /// @@@ Should move the methods specific to TableReportsWorker into that scope.
    private Boolean failsTagFilter(HashSet<String> tagNames, HashSet<String> tagsNamesFilter) {
        if (null == tagsNamesFilter || tagsNamesFilter.isEmpty()) {
            return false;
        }

        HashSet<String> filteredTagNames = new HashSet<>(tagNames);
        filteredTagNames.retainAll(tagsNamesFilter);
        return filteredTagNames.isEmpty();
    }

    /**
     * Get a List of the artifacts and data of the given type that pass the
     * given Tag Filter.
     *
     * @param type           The artifact type to get
     * @param tagNamesFilter The tag names that should be included.
     *
     * @return a list of the filtered tags.
     */
    private List<ArtifactData> getFilteredArtifacts(BlackboardArtifact.Type type, HashSet<String> tagNamesFilter) {
        List<ArtifactData> artifacts = new ArrayList<>();
        try {
            for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(type.getTypeID())) {
                List<BlackboardArtifactTag> tags = Case.getCurrentCase().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact);
                HashSet<String> uniqueTagNames = new HashSet<>();
                for (BlackboardArtifactTag tag : tags) {
                    uniqueTagNames.add(tag.getName().getDisplayName());
                }
                if (failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                    continue;
                }
                try {
                    artifacts.add(new ArtifactData(artifact, skCase.getBlackboardAttributes(artifact), uniqueTagNames));
                } catch (TskCoreException ex) {
                    errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetBBAttribs"));
                    logger.log(Level.SEVERE, "Failed to get Blackboard Attributes when generating report.", ex); //NON-NLS
                }
            }
        } catch (TskCoreException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetBBArtifacts"));
            logger.log(Level.SEVERE, "Failed to get Blackboard Artifacts when generating report.", ex); //NON-NLS
        }
        return artifacts;
    }

    /**
     * Write the keyword hits to the provided TableReportModules.
     *
     * @param tableModules modules to report on
     */
    @SuppressWarnings("deprecation")
    private void writeKeywordHits(List<TableReportModule> tableModules, String comment, HashSet<String> tagNamesFilter) {

        // Query for keyword lists-only so that we can tell modules what lists
        // will exist for their index.
        // @@@ There is a bug in here.  We should use the tags in the below code
        // so that we only report the lists that we will later provide with real
        // hits.  If no keyord hits are tagged, then we make the page for nothing.
        String orderByClause;
        if (currentCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att.value_text, 'SQL_ASCII') ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY list ASC"; //NON-NLS
        }
        String keywordListQuery
                = "SELECT att.value_text AS list " + //NON-NLS
                "FROM blackboard_attributes AS att, blackboard_artifacts AS art " + //NON-NLS
                "WHERE att.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " " + //NON-NLS
                "AND art.artifact_type_id = " + ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + " " + //NON-NLS
                "AND att.artifact_id = art.artifact_id " + //NON-NLS
                "GROUP BY list " + orderByClause; //NON-NLS

        try (CaseDbQuery dbQuery = skCase.executeQuery(keywordListQuery)) {
            ResultSet listsRs = dbQuery.getResultSet();
            List<String> lists = new ArrayList<>();
            while (listsRs.next()) {
                String list = listsRs.getString("list"); //NON-NLS
                if (list.isEmpty()) {
                    list = NbBundle.getMessage(this.getClass(), "ReportGenerator.writeKwHits.userSrchs");
                }
                lists.add(list);
            }

            // Make keyword data type and give them set index
            for (TableReportModule module : tableModules) {
                module.startDataType(ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName(), comment);
                module.addSetIndex(lists);
                tableProgress.get(module).updateStatusLabel(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                                ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName()));
            }
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryKWLists"));
            logger.log(Level.SEVERE, "Failed to query keyword lists: ", ex); //NON-NLS
            return;
        }

        if (currentCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att3.value_text, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(att1.value_text, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.parent_path, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.name, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(att2.value_text, 'SQL_ASCII') ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY list ASC, keyword ASC, parent_path ASC, name ASC, preview ASC"; //NON-NLS
        }
        // Query for keywords, grouped by list
        String keywordsQuery
                = "SELECT art.artifact_id, art.obj_id, att1.value_text AS keyword, att2.value_text AS preview, att3.value_text AS list, f.name AS name, f.parent_path AS parent_path " + //NON-NLS
                "FROM blackboard_artifacts AS art, blackboard_attributes AS att1, blackboard_attributes AS att2, blackboard_attributes AS att3, tsk_files AS f " + //NON-NLS
                "WHERE (att1.artifact_id = art.artifact_id) " + //NON-NLS
                "AND (att2.artifact_id = art.artifact_id) " + //NON-NLS
                "AND (att3.artifact_id = art.artifact_id) " + //NON-NLS
                "AND (f.obj_id = art.obj_id) " + //NON-NLS
                "AND (att1.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID() + ") " + //NON-NLS
                "AND (att2.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID() + ") " + //NON-NLS
                "AND (att3.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ") " + //NON-NLS
                "AND (art.artifact_type_id = " + ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + ") " + //NON-NLS
                orderByClause; //NON-NLS

        try (CaseDbQuery dbQuery = skCase.executeQuery(keywordsQuery)) {
            ResultSet resultSet = dbQuery.getResultSet();

            String currentKeyword = "";
            String currentList = "";
            while (resultSet.next()) {
                // Check to see if all the TableReportModules have been canceled
                if (tableModules.isEmpty()) {
                    break;
                }
                Iterator<TableReportModule> iter = tableModules.iterator();
                while (iter.hasNext()) {
                    TableReportModule module = iter.next();
                    if (tableProgress.get(module).getStatus() == ReportStatus.CANCELED) {
                        iter.remove();
                    }
                }

                // Get any tags that associated with this artifact and apply the tag filter.
                HashSet<String> uniqueTagNames = getUniqueTagNames(resultSet.getLong("artifact_id")); //NON-NLS
                if (failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                    continue;
                }
                String tagsList = makeCommaSeparatedList(uniqueTagNames);

                Long objId = resultSet.getLong("obj_id"); //NON-NLS
                String keyword = resultSet.getString("keyword"); //NON-NLS
                String preview = resultSet.getString("preview"); //NON-NLS
                String list = resultSet.getString("list"); //NON-NLS
                String uniquePath = "";

                try {
                    AbstractFile f = skCase.getAbstractFileById(objId);
                    if (f != null) {
                        uniquePath = skCase.getAbstractFileById(objId).getUniquePath();
                    }
                } catch (TskCoreException ex) {
                    errorList.add(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetAbstractFileByID"));
                    logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex); //NON-NLS
                }

                // If the lists aren't the same, we've started a new list
                if ((!list.equals(currentList) && !list.isEmpty()) || (list.isEmpty() && !currentList.equals(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.writeKwHits.userSrchs")))) {
                    if (!currentList.isEmpty()) {
                        for (TableReportModule module : tableModules) {
                            module.endTable();
                            module.endSet();
                        }
                    }
                    currentList = list.isEmpty() ? NbBundle
                            .getMessage(this.getClass(), "ReportGenerator.writeKwHits.userSrchs") : list;
                    currentKeyword = ""; // reset the current keyword because it's a new list
                    for (TableReportModule module : tableModules) {
                        module.startSet(currentList);
                        tableProgress.get(module).updateStatusLabel(
                                NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processingList",
                                        ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName(), currentList));
                    }
                }
                if (!keyword.equals(currentKeyword)) {
                    if (!currentKeyword.equals("")) {
                        for (TableReportModule module : tableModules) {
                            module.endTable();
                        }
                    }
                    currentKeyword = keyword;
                    for (TableReportModule module : tableModules) {
                        module.addSetElement(currentKeyword);
                        List<String> columnHeaderNames = new ArrayList<>();
                        columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.preview"));
                        columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.srcFile"));
                        columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags"));
                        module.startTable(columnHeaderNames);
                    }
                }

                String previewreplace = EscapeUtil.escapeHtml(preview);
                for (TableReportModule module : tableModules) {
                    module.addRow(Arrays.asList(new String[]{previewreplace.replaceAll("<!", ""), uniquePath, tagsList}));
                }
            }

            // Finish the current data type
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endDataType();
            }
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryKWs"));
            logger.log(Level.SEVERE, "Failed to query keywords: ", ex); //NON-NLS
        }
    }

    /**
     * Write the hash set hits to the provided TableReportModules.
     *
     * @param tableModules modules to report on
     */
    @SuppressWarnings("deprecation")
    private void writeHashsetHits(List<TableReportModule> tableModules, String comment, HashSet<String> tagNamesFilter) {
        String orderByClause;
        if (currentCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att.value_text, 'SQL_ASCII') ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY att.value_text ASC"; //NON-NLS
        }
        String hashsetsQuery
                = "SELECT att.value_text AS list " + //NON-NLS
                "FROM blackboard_attributes AS att, blackboard_artifacts AS art " + //NON-NLS
                "WHERE att.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " " + //NON-NLS
                "AND art.artifact_type_id = " + ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() + " " + //NON-NLS
                "AND att.artifact_id = art.artifact_id " + //NON-NLS
                "GROUP BY list " + orderByClause; //NON-NLS

        try (CaseDbQuery dbQuery = skCase.executeQuery(hashsetsQuery)) {
            // Query for hashsets
            ResultSet listsRs = dbQuery.getResultSet();
            List<String> lists = new ArrayList<>();
            while (listsRs.next()) {
                lists.add(listsRs.getString("list")); //NON-NLS
            }

            for (TableReportModule module : tableModules) {
                module.startDataType(ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName(), comment);
                module.addSetIndex(lists);
                tableProgress.get(module).updateStatusLabel(
                        NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processing",
                                ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName()));
            }
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryHashsetLists"));
            logger.log(Level.SEVERE, "Failed to query hashset lists: ", ex); //NON-NLS
            return;
        }

        if (currentCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            orderByClause = "ORDER BY convert_to(att.value_text, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.parent_path, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "convert_to(f.name, 'SQL_ASCII') ASC NULLS FIRST, " //NON-NLS
                    + "size ASC NULLS FIRST"; //NON-NLS
        } else {
            orderByClause = "ORDER BY att.value_text ASC, f.parent_path ASC, f.name ASC, size ASC"; //NON-NLS
        }
        String hashsetHitsQuery
                = "SELECT art.artifact_id, art.obj_id, att.value_text AS setname, f.name AS name, f.size AS size, f.parent_path AS parent_path " + //NON-NLS
                "FROM blackboard_artifacts AS art, blackboard_attributes AS att, tsk_files AS f " + //NON-NLS
                "WHERE (att.artifact_id = art.artifact_id) " + //NON-NLS
                "AND (f.obj_id = art.obj_id) " + //NON-NLS
                "AND (att.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ") " + //NON-NLS
                "AND (art.artifact_type_id = " + ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() + ") " + //NON-NLS
                orderByClause; //NON-NLS

        try (CaseDbQuery dbQuery = skCase.executeQuery(hashsetHitsQuery)) {
            // Query for hashset hits
            ResultSet resultSet = dbQuery.getResultSet();
            String currentSet = "";
            while (resultSet.next()) {
                // Check to see if all the TableReportModules have been canceled
                if (tableModules.isEmpty()) {
                    break;
                }
                Iterator<TableReportModule> iter = tableModules.iterator();
                while (iter.hasNext()) {
                    TableReportModule module = iter.next();
                    if (tableProgress.get(module).getStatus() == ReportStatus.CANCELED) {
                        iter.remove();
                    }
                }

                // Get any tags that associated with this artifact and apply the tag filter.
                HashSet<String> uniqueTagNames = getUniqueTagNames(resultSet.getLong("artifact_id")); //NON-NLS
                if (failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                    continue;
                }
                String tagsList = makeCommaSeparatedList(uniqueTagNames);

                Long objId = resultSet.getLong("obj_id"); //NON-NLS
                String set = resultSet.getString("setname"); //NON-NLS
                String size = resultSet.getString("size"); //NON-NLS
                String uniquePath = "";

                try {
                    AbstractFile f = skCase.getAbstractFileById(objId);
                    if (f != null) {
                        uniquePath = skCase.getAbstractFileById(objId).getUniquePath();
                    }
                } catch (TskCoreException ex) {
                    errorList.add(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetAbstractFileFromID"));
                    logger.log(Level.WARNING, "Failed to get Abstract File from ID.", ex); //NON-NLS
                    return;
                }

                // If the sets aren't the same, we've started a new set
                if (!set.equals(currentSet)) {
                    if (!currentSet.isEmpty()) {
                        for (TableReportModule module : tableModules) {
                            module.endTable();
                            module.endSet();
                        }
                    }
                    currentSet = set;
                    for (TableReportModule module : tableModules) {
                        module.startSet(currentSet);
                        List<String> columnHeaderNames = new ArrayList<>();
                        columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.file"));
                        columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.size"));
                        columnHeaderNames.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags"));
                        module.startTable(columnHeaderNames);
                        tableProgress.get(module).updateStatusLabel(
                                NbBundle.getMessage(this.getClass(), "ReportGenerator.progress.processingList",
                                        ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName(), currentSet));
                    }
                }

                // Add a row for this hit to every module
                for (TableReportModule module : tableModules) {
                    module.addRow(Arrays.asList(new String[]{uniquePath, size, tagsList}));
                }
            }

            // Finish the current data type
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endDataType();
            }
        } catch (TskCoreException | SQLException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedQueryHashsetHits"));
            logger.log(Level.SEVERE, "Failed to query hashsets hits: ", ex); //NON-NLS
        }
    }

    /**
     * For a given artifact type ID, return the list of the columns that we are
     * reporting on.
     *
     * @param artifactTypeId   artifact type ID
     * @param attributeTypeSet The set of attributeTypeSet available for this
     *                         artifact type
     *
     * @return List<String> row titles
     */
    private List<Column> getArtifactTableColumns(int artifactTypeId, Set<BlackboardAttribute.Type> attributeTypeSet) {
        ArrayList<Column> columns = new ArrayList<>();

        // Long switch statement to retain ordering of attribute types that are 
        // attached to pre-defined artifact types.
        if (ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.title"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_TITLE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateCreated"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.value"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_VALUE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateAccessed"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.referrer"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_REFERRER)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.title"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_TITLE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.urlDomainDecoded"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL_DECODED)));

        } else if (ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dest"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.sourceUrl"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateAccessed"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.path"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.progName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.instDateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() == artifactTypeId) {
            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.preview")));

        } else if (ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() == artifactTypeId) {
            columns.add(new SourceFileColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.file")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.size")));

        } else if (ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devMake"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devModel"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.deviceId"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_ID)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.text"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_TEXT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.domain"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DOMAIN)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateAccessed"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.progName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTaken"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devManufacturer"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.devModel"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

        } else if (ARTIFACT_TYPE.TSK_CONTACT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumber"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumHome"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumOffice"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_OFFICE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumMobile"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.email"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL)));

        } else if (ARTIFACT_TYPE.TSK_MESSAGE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.msgType"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.direction"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DIRECTION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.readStatus"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_READ_STATUS)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.fromPhoneNum"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.fromEmail"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.toPhoneNum"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.toEmail"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.subject"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SUBJECT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.text"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_TEXT)));

        } else if (ARTIFACT_TYPE.TSK_CALLLOG.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.fromPhoneNum"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.toPhoneNum"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.direction"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DIRECTION)));

        } else if (ARTIFACT_TYPE.TSK_CALENDAR_ENTRY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.calendarEntryType"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CALENDAR_ENTRY_TYPE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.description"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DESCRIPTION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.startDateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.endDateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_END)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.location"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_LOCATION)));

        } else if (ARTIFACT_TYPE.TSK_SPEED_DIAL_ENTRY.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.shortCut"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SHORTCUT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME_PERSON)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.phoneNumber"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER)));

        } else if (ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.deviceName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.deviceAddress"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DEVICE_ID)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.locationAddress"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.locationAddress"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.altitude"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.locationAddress"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.category"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CATEGORY)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.userId"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_USER_ID)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.password"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PASSWORD)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.personName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.appName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.url"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_URL)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.appPath"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.description"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DESCRIPTION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.replytoAddress"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_REPLYTO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.mailServer"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SERVER_NAME)));

        } else if (ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

        } else if (ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID() == artifactTypeId) {
            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.file")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.extension.text")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.mimeType.text")));

            columns.add(new HeaderOnlyColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.path")));

        } else if (ARTIFACT_TYPE.TSK_OS_INFO.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.processorArchitecture.text"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROCESSOR_ARCHITECTURE)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.osName.text"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.osInstallDate.text"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

        } else if (ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailTo"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_TO)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailFrom"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_FROM)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskSubject"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SUBJECT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskDateTimeSent"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_SENT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskDateTimeRcvd"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskPath"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailCc"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_CC)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskEmailBcc"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_EMAIL_BCC)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskMsgId"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_MSG_ID)));

        } else if (ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskSetName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskInterestingFilesCategory"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CATEGORY)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskPath"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH)));

        } else if (ARTIFACT_TYPE.TSK_GPS_ROUTE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskGpsRouteCategory"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CATEGORY)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitudeEnd"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_END)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitudeEnd"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_END)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.latitudeStart"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.longitudeStart"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE_START)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.name"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.location"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_LOCATION)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tskSetName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.associatedArtifact"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

        } else if (ARTIFACT_TYPE.TSK_PROG_RUN.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.program"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PROG_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.associatedArtifact"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.dateTime"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_DATETIME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.count"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_COUNT)));

        } else if (ARTIFACT_TYPE.TSK_OS_ACCOUNT.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.userName"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_USER_NAME)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.userId"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_USER_ID)));

        } else if (ARTIFACT_TYPE.TSK_REMOTE_DRIVE.getTypeID() == artifactTypeId) {
            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.localPath"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_LOCAL_PATH)));

            columns.add(new AttributeColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.remotePath"),
                    new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_REMOTE_PATH)));
        } else {
            // This is the case that it is a custom type. The reason an else is 
            // necessary is to make sure that the source file column is added
            for (BlackboardAttribute.Type type : attributeTypeSet) {
                columns.add(new AttributeColumn(type.getDisplayName(), type));
            }
            columns.add(new SourceFileColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.srcFile")));
            columns.add(new TaggedResultsColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags")));

            // Short circuits to guarantee that the attribute types aren't added
            // twice.
            return columns;
        }
        // If it is an attribute column, it removes the attribute type of that 
        // column from the set, so types are not reported more than once.
        for (Column column : columns) {
            attributeTypeSet = column.removeTypeFromSet(attributeTypeSet);
        }
        // Now uses the remaining types in the set to construct columns
        for (BlackboardAttribute.Type type : attributeTypeSet) {
            columns.add(new AttributeColumn(type.getDisplayName(), type));
        }
        // Source file column is added here for ordering purposes.
        if (artifactTypeId == ARTIFACT_TYPE.TSK_WEB_BOOKMARK.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_WEB_COOKIE.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_WEB_HISTORY.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_WEB_DOWNLOAD.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_RECENT_OBJECT.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_INSTALLED_PROG.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_DEVICE_ATTACHED.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_WEB_SEARCH_QUERY.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_CONTACT.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_CALLLOG.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_CALENDAR_ENTRY.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_SPEED_DIAL_ENTRY.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_BLUETOOTH_PAIRING.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_SERVICE_ACCOUNT.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID()
                || artifactTypeId == ARTIFACT_TYPE.TSK_OS_INFO.getTypeID()) {
            columns.add(new SourceFileColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.srcFile")));
        }
        columns.add(new TaggedResultsColumn(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags")));

        return columns;
    }

    /**
     * Converts a collection of strings into a single string of comma-separated
     * items
     *
     * @param items A collection of strings
     *
     * @return A string of comma-separated items
     */
    private String makeCommaSeparatedList(Collection<String> items) {
        String list = "";
        for (Iterator<String> iterator = items.iterator(); iterator.hasNext();) {
            list += iterator.next() + (iterator.hasNext() ? ", " : "");
        }
        return list;
    }

    /**
     * Given a tsk_file's obj_id, return the unique path of that file.
     *
     * @param objId tsk_file obj_id
     *
     * @return String unique path
     */
    private String getFileUniquePath(Content content) {
        try {
            if (content != null) {
                return content.getUniquePath();
            } else {
                return "";
            }
        } catch (TskCoreException ex) {
            errorList.add(NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.failedGetAbstractFileByID"));
            logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex); //NON-NLS
        }
        return "";

    }

    /**
     * Container class that holds data about an Artifact to eliminate duplicate
     * calls to the Sleuthkit database.
     */
    private class ArtifactData implements Comparable<ArtifactData> {

        private BlackboardArtifact artifact;
        private List<BlackboardAttribute> attributes;
        private HashSet<String> tags;
        private List<String> rowData = null;
        private Content content;

        ArtifactData(BlackboardArtifact artifact, List<BlackboardAttribute> attrs, HashSet<String> tags) {
            this.artifact = artifact;
            this.attributes = attrs;
            this.tags = tags;
            try {
                this.content = Case.getCurrentCase().getSleuthkitCase().getContentById(artifact.getObjectID());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get content from database");
            }
        }

        public BlackboardArtifact getArtifact() {
            return artifact;
        }

        public List<BlackboardAttribute> getAttributes() {
            return attributes;
        }

        public HashSet<String> getTags() {
            return tags;
        }

        public long getArtifactID() {
            return artifact.getArtifactID();
        }

        public long getObjectID() {
            return artifact.getObjectID();
        }

        /**
         * @return the content
         */
        public Content getContent() {
            return content;
        }

        /**
         * Compares ArtifactData objects by the first attribute they have in
         * common in their List<BlackboardAttribute>. Should only be used on two
         * artifacts of the same type
         *
         * If all attributes are the same, they are assumed duplicates and are
         * compared by their artifact id. Should only be used with attributes of
         * the same type.
         */
        @Override
        public int compareTo(ArtifactData otherArtifactData) {
            List<String> thisRow = getRow();
            List<String> otherRow = otherArtifactData.getRow();
            for (int i = 0; i < thisRow.size(); i++) {
                int compare = thisRow.get(i).compareTo(otherRow.get(i));
                if (compare != 0) {
                    return compare;
                }
            }
            return ((Long) this.getArtifactID()).compareTo((Long) otherArtifactData.getArtifactID());
        }

        /**
         * Get the values for each row in the table report.
         *
         * the value types of custom artifacts
         *
         * @return A list of string representing the data for this artifact.
         */
        public List<String> getRow() {
            if (rowData == null) {
                try {
                    rowData = getOrderedRowDataAsStrings();
                    // If else is done so that row data is not set before 
                    // columns are added to the hash map.
                    if (rowData.size() > 0) {
                        // replace null values if attribute was not defined
                        for (int i = 0; i < rowData.size(); i++) {
                            if (rowData.get(i) == null) {
                                rowData.set(i, "");
                            }
                        }
                    } else {
                        rowData = null;
                        return new ArrayList<>();
                    }
                } catch (TskCoreException ex) {
                    errorList.add(
                            NbBundle.getMessage(this.getClass(), "ReportGenerator.errList.coreExceptionWhileGenRptRow"));
                    logger.log(Level.WARNING, "Core exception while generating row data for artifact report.", ex); //NON-NLS
                    rowData = Collections.<String>emptyList();
                }
            }
            return rowData;
        }

        /**
         * Get a list of Strings with all the row values for the Artifact in the
         * correct order to be written to the report.
         *
         * @return List<String> row values. Values could be null if attribute is
         *         not defined in artifact
         *
         * @throws TskCoreException
         */
        private List<String> getOrderedRowDataAsStrings() throws TskCoreException {

            List<String> orderedRowData = new ArrayList<>();
            if (ARTIFACT_TYPE.TSK_EXT_MISMATCH_DETECTED.getTypeID() == getArtifact().getArtifactTypeID()) {
                if (content != null && content instanceof AbstractFile) {
                    AbstractFile file = (AbstractFile) content;
                    orderedRowData.add(file.getName());
                    orderedRowData.add(file.getNameExtension());
                    String mimeType = file.getMIMEType();
                    if (mimeType == null) {
                        orderedRowData.add("");
                    } else {
                        orderedRowData.add(mimeType);
                    }
                    orderedRowData.add(file.getUniquePath());
                } else {
                    // Make empty rows to make sure the formatting is correct
                    orderedRowData.add(null);
                    orderedRowData.add(null);
                    orderedRowData.add(null);
                    orderedRowData.add(null);
                }
                orderedRowData.add(makeCommaSeparatedList(getTags()));

            } else if (ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID() == getArtifact().getArtifactTypeID()) {
                String[] attributeDataArray = new String[3];
                // Array is used so that the order of the attributes is 
                // maintained.
                for (BlackboardAttribute attr : attributes) {
                    if (attr.getAttributeType().equals(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME))) {
                        attributeDataArray[0] = attr.getDisplayString();
                    } else if (attr.getAttributeType().equals(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CATEGORY))) {
                        attributeDataArray[1] = attr.getDisplayString();
                    } else if (attr.getAttributeType().equals(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_PATH))) {
                        String pathToShow = attr.getDisplayString();
                        if (pathToShow.isEmpty()) {
                            pathToShow = getFileUniquePath(content);
                        }
                        attributeDataArray[2] = pathToShow;
                    }
                }
                orderedRowData.addAll(Arrays.asList(attributeDataArray));
                orderedRowData.add(makeCommaSeparatedList(getTags()));

            } else {
                if (ReportGenerator.this.columnHeaderMap.containsKey(this.artifact.getArtifactTypeID())) {

                    for (Column currColumn : ReportGenerator.this.columnHeaderMap.get(this.artifact.getArtifactTypeID())) {
                        String cellData = currColumn.getCellData(this);
                        orderedRowData.add(cellData);
                    }
                }
            }

            return orderedRowData;
        }

    }

    /**
     * Get any tags associated with an artifact
     *
     * @param artifactId
     *
     * @return hash set of tag display names
     *
     * @throws SQLException
     */
    @SuppressWarnings("deprecation")
    private HashSet<String> getUniqueTagNames(long artifactId) throws TskCoreException {
        HashSet<String> uniqueTagNames = new HashSet<>();

        String query = "SELECT display_name, artifact_id FROM tag_names AS tn, blackboard_artifact_tags AS bat " + //NON-NLS 
                "WHERE tn.tag_name_id = bat.tag_name_id AND bat.artifact_id = " + artifactId; //NON-NLS

        try (CaseDbQuery dbQuery = skCase.executeQuery(query)) {
            ResultSet tagNameRows = dbQuery.getResultSet();
            while (tagNameRows.next()) {
                uniqueTagNames.add(tagNameRows.getString("display_name")); //NON-NLS
            }
        } catch (TskCoreException | SQLException ex) {
            throw new TskCoreException("Error getting tag names for artifact: ", ex);
        }

        return uniqueTagNames;

    }

    private interface Column {

        String getColumnHeader();

        String getCellData(ArtifactData artData);

        Set<BlackboardAttribute.Type> removeTypeFromSet(Set<BlackboardAttribute.Type> types);
    }

    private class AttributeColumn implements Column {

        private String columnHeader;
        private BlackboardAttribute.Type attributeType;

        /**
         * Constructs an ArtifactCell
         *
         * @param columnHeader  The header text of this column
         * @param attributeType The attribute type associated with this column
         */
        AttributeColumn(String columnHeader, BlackboardAttribute.Type attributeType) {
            this.columnHeader = Objects.requireNonNull(columnHeader);
            this.attributeType = attributeType;
        }

        @Override
        public String getColumnHeader() {
            return this.columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            List<BlackboardAttribute> attributes = artData.getAttributes();
            for (BlackboardAttribute attribute : attributes) {
                if (attribute.getAttributeType().equals(this.attributeType)) {
                    if (attribute.getAttributeType().getValueType() != BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                        return attribute.getDisplayString();
                    } else {
                        return ContentUtils.getStringTime(attribute.getValueLong(), artData.getContent());
                    }
                }
            }
            return "";
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<Type> types) {
            types.remove(this.attributeType);
            return types;
        }
    }

    private class SourceFileColumn implements Column {

        private String columnHeader;

        SourceFileColumn(String columnHeader) {
            this.columnHeader = columnHeader;
        }

        @Override
        public String getColumnHeader() {
            return this.columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            return getFileUniquePath(artData.getContent());
            /*else if (this.columnHeader.equals(NbBundle.getMessage(this.getClass(), "ReportGenerator.artTableColHdr.tags"))) {
             return makeCommaSeparatedList(artData.getTags());
             }
             return "";*/
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<Type> types) {
            // This column doesn't have a type, so nothing to remove
            return types;
        }
    }

    private class TaggedResultsColumn implements Column {

        private String columnHeader;

        TaggedResultsColumn(String columnHeader) {
            this.columnHeader = columnHeader;
        }

        @Override
        public String getColumnHeader() {
            return this.columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            return makeCommaSeparatedList(artData.getTags());
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<Type> types) {
            // This column doesn't have a type, so nothing to remove
            return types;
        }
    }

    private class HeaderOnlyColumn implements Column {

        private String columnHeader;

        HeaderOnlyColumn(String columnHeader) {
            this.columnHeader = columnHeader;
        }

        @Override
        public String getColumnHeader() {
            return columnHeader;
        }

        @Override
        public String getCellData(ArtifactData artData) {
            throw new UnsupportedOperationException("Cannot get cell data of unspecified column");
        }

        @Override
        public Set<BlackboardAttribute.Type> removeTypeFromSet(Set<Type> types) {
            // This column doesn't have a type, so nothing to remove
            return types;
        }
    }
}
