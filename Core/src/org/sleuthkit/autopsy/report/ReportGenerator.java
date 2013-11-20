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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashSet;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingWorker;
import org.openide.filesystems.FileUtil;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class use GeneralReportModules, TableReportModules and 
 * FileReportModules to generate a report. If desired, displayProgressPanels()
 * can be called to show report generation progress using ReportProgressPanel 
 * objects displayed using a dialog box.
 */
public class ReportGenerator {
    private static final Logger logger = Logger.getLogger(ReportGenerator.class.getName());
    
    private Case currentCase = Case.getCurrentCase();
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();
    
    private Map<TableReportModule, ReportProgressPanel> tableProgress;
    private Map<GeneralReportModule, ReportProgressPanel> generalProgress;
    private Map<FileReportModule, ReportProgressPanel> fileProgress;
    
    private String reportPath;
    private ReportGenerationPanel panel = new ReportGenerationPanel();
    
    static final String REPORTS_DIR = "Reports";
        
    ReportGenerator(Map<TableReportModule, Boolean> tableModuleStates, Map<GeneralReportModule, Boolean> generalModuleStates, Map<FileReportModule, Boolean> fileListModuleStates) {
        // Create the root reports directory path of the form: <CASE DIRECTORY>/Reports/<Case name> <Timestamp>/
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String dateNoTime = dateFormat.format(date);
        this.reportPath = currentCase.getCaseDirectory() + File.separator + REPORTS_DIR + File.separator + currentCase.getName() + " " + dateNoTime + File.separator;
        
        // Create the root reports directory.
        try {
            FileUtil.createFolder(new File(this.reportPath));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to make report folder, may be unable to generate reports.", ex);
        }
        
        // Initialize the progress panels
        generalProgress = new HashMap<>();
        tableProgress = new HashMap<>();
        fileProgress = new HashMap<>();
        setupProgressPanels(tableModuleStates, generalModuleStates, fileListModuleStates);
    }
    
    /**
     * Create a ReportProgressPanel for each report generation module selected by the user.
     * 
     * @param tableModuleStates The enabled/disabled state of each TableReportModule
     * @param generalModuleStates The enabled/disabled state of each GeneralReportModule
     * @param fileListModuleStates The enabled/disabled state of each FileReportModule
     */
    private void setupProgressPanels(Map<TableReportModule, Boolean> tableModuleStates, Map<GeneralReportModule, Boolean> generalModuleStates, Map<FileReportModule, Boolean> fileListModuleStates) {
        if (null != tableModuleStates) {
            for (Entry<TableReportModule, Boolean> entry : tableModuleStates.entrySet()) {
                if (entry.getValue()) {
                    TableReportModule module = entry.getKey();
                    String moduleFilePath = module.getFilePath();
                    if (moduleFilePath != null) {
                        tableProgress.put(module, panel.addReport(module.getName(), reportPath + moduleFilePath));
                    }
                    else {
                        tableProgress.put(module, panel.addReport(module.getName(), null));                        
                    }
                }
            }
        }
        
        if (null != generalModuleStates) {
            for (Entry<GeneralReportModule, Boolean> entry : generalModuleStates.entrySet()) {
                if (entry.getValue()) {
                    GeneralReportModule module = entry.getKey();
                    String moduleFilePath = module.getFilePath();
                    if (moduleFilePath != null) {
                        generalProgress.put(module, panel.addReport(module.getName(), reportPath + moduleFilePath));
                    }
                    else {
                        generalProgress.put(module, panel.addReport(module.getName(), null));                        
                    }
                }
            }
        }
        
        if (null != fileListModuleStates) {
            for(Entry<FileReportModule, Boolean> entry : fileListModuleStates.entrySet()) {
                if (entry.getValue()) {
                    FileReportModule module = entry.getKey();
                    String moduleFilePath = module.getFilePath();
                    if (moduleFilePath != null) {
                        fileProgress.put(module, panel.addReport(module.getName(), reportPath + moduleFilePath));
                    }
                    else {
                        fileProgress.put(module, panel.addReport(module.getName(), null));                        
                    }
                }
            }
        }
    }
    
    /**
     * Display the progress panels to the user, and add actions to close the parent dialog.
     */
    public void displayProgressPanels() {
        final JDialog dialog = new JDialog(new JFrame(), true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setTitle("Report Generation Progress...");
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
     * @param artifactTypeSelections the enabled/disabled state of the artifact types to be included in the report
     * @param tagSelections the enabled/disabled state of the tag names to be included in the report
     */
    public void generateBlackboardArtifactsReports(Map<ARTIFACT_TYPE, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections) {
        if (!tableProgress.isEmpty() && null != artifactTypeSelections) {
            TableReportsWorker worker = new TableReportsWorker(artifactTypeSelections, tagNameSelections);
            worker.execute();
        }
    }
    
    /**
     * Run the FileReportModules using a SwingWorker.
     * 
     * @param enabledInfo the Information that should be included about each file
     * in the report.
     */
    public void generateFileListReports(Map<FileReportDataTypes, Boolean> enabledInfo) {
        if (!fileProgress.isEmpty() && null != enabledInfo) {
            List<FileReportDataTypes> enabled = new ArrayList<>();
            for (Entry<FileReportDataTypes, Boolean> e : enabledInfo.entrySet()) {
                if(e.getValue()) {
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
                    progress.updateStatusLabel("Querying database...");
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
                        progress.updateStatusLabel("Now processing " + file.getName());
                    }
                }
                i++;
            }
            
            for (FileReportModule module : fileModules) {
                module.endTable();
                module.endReport();
                fileProgress.get(module).complete();
            }
            
            return 0;
        }
        
        /**
         * Get all files in the image.
         * @return 
         */
        private List<AbstractFile> getFiles() {
            List<AbstractFile> absFiles;
            try {
                SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
                absFiles = skCase.findAllFilesWhere("NOT meta_type = 2");
                return absFiles;
            } catch (TskCoreException ex) {
                // TODO
                return Collections.EMPTY_LIST;
            }
        }
    }
    
    /**
     * SwingWorker to run TableReportModules to report on blackboard artifacts, 
     * content tags, and blackboard artifact tags.
     */
    private class TableReportsWorker extends SwingWorker<Integer, Integer> {
        private List<TableReportModule> tableModules  = new ArrayList<>();
        private List<ARTIFACT_TYPE> artifactTypes  = new ArrayList<>();
        private HashSet<String> tagNamesFilter = new HashSet<>();
        
        TableReportsWorker(Map<ARTIFACT_TYPE, Boolean> artifactTypeSelections, Map<String, Boolean> tagNameSelections) {
            // Get the report modules selected by the user.
            for (Entry<TableReportModule, ReportProgressPanel> entry : tableProgress.entrySet()) {
                tableModules.add(entry.getKey());
            }
            
            // Get the artifact types selected by the user.
            for (Entry<ARTIFACT_TYPE, Boolean> entry : artifactTypeSelections.entrySet()) {
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
                    progress.setMaximumProgress(ARTIFACT_TYPE.values().length + 2); // +2 for content and blackboard artifact tags
                }
            }
                        
            makeBlackboardArtifactTables();
            makeContentTagsTables();
            makeBlackboardArtifactTagsTables();
            
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).complete();
                module.endReport();
            }
            
            return 0;
        }
        
        private void makeBlackboardArtifactTables() {
            // Make a comment string describing the tag names filter in effect. 
            StringBuilder comment = new StringBuilder();
            if (!tagNamesFilter.isEmpty()) {
                comment.append("This report only includes results tagged with: ");
                comment.append(makeCommaSeparatedList(tagNamesFilter));
            }            

            // Add a table to the report for every enabled blackboard artifact type.
            for (ARTIFACT_TYPE type : artifactTypes) {
                // Check for cancellaton.
                removeCancelledTableReportModules();
                if (tableModules.isEmpty()) {
                    return;
                }
                        
                // Keyword hits and hashset hit artifacts get sepcial handling.
                if (type.equals(ARTIFACT_TYPE.TSK_KEYWORD_HIT)) {
                    writeKeywordHits(tableModules, comment.toString(), tagNamesFilter);
                    continue;
                } else if (type.equals(ARTIFACT_TYPE.TSK_HASHSET_HIT)) {
                    writeHashsetHits(tableModules, comment.toString(), tagNamesFilter);
                    continue;
                }

                List<ArtifactData> unsortedArtifacts = getFilteredArtifacts(type, tagNamesFilter);
                if (unsortedArtifacts.isEmpty()) {
                    continue;
                }

                // The most efficient way to sort all the Artifacts is to add them to a List, and then
                // sort that List based off a Comparator. Adding to a TreeMap/Set/List sorts the list
                // each time an element is added, which adds unnecessary overhead if we only need it sorted once.
                Collections.sort(unsortedArtifacts);

                // Get the column headers appropriate for the artifact type.
                /* @@@ BC: Seems like a better design here would be to have a method that 
                 * takes in the artifact as an argument and returns the attributes. We then use that
                 * to make the headers and to make each row afterwards so that we don't have artifact-specific
                 * logic in both getArtifactTableCoumnHeaders and getArtifactRow()
                 */
                List<String> columnHeaders = getArtifactTableColumnHeaders(type.getTypeID());
                if (columnHeaders == null) {
                    // @@@ Hack to prevent system from hanging.  Better solution is to merge all attributes into a single column or analyze the artifacts to find out how many are needed.
                    MessageNotifyUtil.Notify.show("Skipping artifact type " + type + " in reports", "Unknown columns to report on", MessageNotifyUtil.MessageType.ERROR);
                    continue;
                }

                for (TableReportModule module : tableModules) {
                    tableProgress.get(module).updateStatusLabel("Now processing " + type.getDisplayName() + "...");                    
                    module.startDataType(type.getDisplayName(), comment.toString());                                            
                    module.startTable(columnHeaders);                    
                }

                boolean msgSent = false;    
                for(ArtifactData artifactData : unsortedArtifacts) {                    
                    // Add the row data to all of the reports.
                    for (TableReportModule module : tableModules) {                        
                        List<String> rowData; 
                        rowData = getArtifactRow(artifactData, module);   
                        if (rowData.isEmpty()) {
                            if (msgSent == false) {
                                MessageNotifyUtil.Notify.show("Skipping artifact rows for type " + type + " in reports", "Unknown columns to report on", MessageNotifyUtil.MessageType.ERROR);
                                msgSent = true;
                            }
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
            }
            catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "failed to get content tags", ex);
                return;
            }
                        
            // Tell the modules reporting on content tags is beginning.
            for (TableReportModule module : tableModules) {            
                // @@@ This casting is a tricky little workaround to allow the HTML report module to slip in a content hyperlink.
                // @@@ Alos Using the obsolete ARTIFACT_TYPE.TSK_TAG_FILE is also an expedient hack.
                tableProgress.get(module).updateStatusLabel("Now processing " + ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName() + "...");                                
                ArrayList<String> columnHeaders = new ArrayList<>(Arrays.asList("File", "Tag", "Comment"));                
                StringBuilder comment = new StringBuilder();
                if (!tagNamesFilter.isEmpty()) {
                    comment.append("This report only includes file tagged with: ");
                    comment.append(makeCommaSeparatedList(tagNamesFilter));
                }            
                if (module instanceof ReportHTML) {
                    ReportHTML htmlReportModule = (ReportHTML)module;
                    htmlReportModule.startDataType(ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName(), comment.toString());                        
                    htmlReportModule.startContentTagsTable(columnHeaders); 
                }
                else {
                    module.startDataType(ARTIFACT_TYPE.TSK_TAG_FILE.getDisplayName(), comment.toString());                        
                    module.startTable(columnHeaders);
                }                
            }
                        
            // Give the modules the rows for the content tags. 
            for (ContentTag tag : tags) {
                if (passesTagNamesFilter(tag.getName().getDisplayName())) {                                                               
                    ArrayList<String> rowData = new ArrayList<>(Arrays.asList(tag.getContent().getName(), tag.getName().getDisplayName(), tag.getComment()));
                    for (TableReportModule module : tableModules) {                                                                                       
                        // @@@ This casting is a tricky little workaround to allow the HTML report module to slip in a content hyperlink.
                        if (module instanceof ReportHTML) {
                            ReportHTML htmlReportModule = (ReportHTML)module;
                            htmlReportModule.addRowWithTaggedContentHyperlink(rowData, tag); 
                        }
                        else {      
                            module.addRow(rowData);
                        }                        
                    }
                }
            }                
                
            // The the modules content tags reporting is ended.
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endTable();
                module.endDataType();
            }            
        }
        
        private void makeBlackboardArtifactTagsTables() {
            // Check for cancellaton.
            removeCancelledTableReportModules();
            if (tableModules.isEmpty()) {
                return;
            }
                        
            List<BlackboardArtifactTag> tags;
            try {
                tags = Case.getCurrentCase().getServices().getTagsManager().getAllBlackboardArtifactTags();
            }
            catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "failed to get blackboard artifact tags", ex);
                return;
            }

            // Tell the modules reporting on blackboard artifact tags data type is beginning.
            // @@@ Using the obsolete ARTIFACT_TYPE.TSK_TAG_ARTIFACT is an expedient hack.
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).updateStatusLabel("Now processing " + ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getDisplayName() + "...");                                
                StringBuilder comment = new StringBuilder();
                if (!tagNamesFilter.isEmpty()) {
                    comment.append("This report only includes results tagged with: ");
                    comment.append(makeCommaSeparatedList(tagNamesFilter));
                }                        
                module.startDataType(ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getDisplayName(), comment.toString());                        
                module.startTable(new ArrayList<>(Arrays.asList("Result Type", "Tag", "Comment", "Source File")));
            }
                        
            // Give the modules the rows for the content tags. 
            for (BlackboardArtifactTag tag : tags) {
                if (passesTagNamesFilter(tag.getName().getDisplayName())) {                               
                    for (TableReportModule module : tableModules) {
                        module.addRow(new ArrayList<>(Arrays.asList(tag.getArtifact().getArtifactTypeName(), tag.getName().getDisplayName(), tag.getComment(), tag.getContent().getName()))); 
                    }
                }
            }                

            // The the modules blackboard artifact tags reporting is ended.
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endTable();
                module.endDataType();
            }            
        }     
        
        boolean passesTagNamesFilter(String tagName) {
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
    }
        
    /// @@@ Should move the methods specific to TableReportsWorker into that scope.
    private Boolean failsTagFilter(HashSet<String> tagNames, HashSet<String> tagsNamesFilter) 
    {
        if (null == tagsNamesFilter || tagsNamesFilter.isEmpty()) {
            return false;
        }

        HashSet<String> filteredTagNames = new HashSet<>(tagNames);
        filteredTagNames.retainAll(tagsNamesFilter);
        return filteredTagNames.isEmpty();
    }
    
    /**
     * Get a List of the artifacts and data of the given type that pass the given Tag Filter.
     * 
     * @param type The artifact type to get
     * @param tagNamesFilter The tag names that should be included.
     * @return a list of the filtered tags.
     */
    private List<ArtifactData> getFilteredArtifacts(ARTIFACT_TYPE type, HashSet<String> tagNamesFilter) {
        List<ArtifactData> artifacts = new ArrayList<>();
        try {
             for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(type)) {
                 List<BlackboardArtifactTag> tags = Case.getCurrentCase().getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact);
                 HashSet<String> uniqueTagNames = new HashSet<>();
                 for (BlackboardArtifactTag tag : tags) {
                     uniqueTagNames.add(tag.getName().getDisplayName());
                 }
                 if(failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                     continue;
                 }
                 try {
                     artifacts.add(new ArtifactData(artifact, skCase.getBlackboardAttributes(artifact), uniqueTagNames));
                 } catch (TskCoreException ex) {
                     logger.log(Level.SEVERE, "Failed to get Blackboard Attributes when generating report.", ex);
                 }
             }
         } 
         catch (TskCoreException ex) {
             logger.log(Level.SEVERE, "Failed to get Blackboard Artifacts when generating report.", ex);
         }
        return artifacts;
    }
            
    /**
     * Write the keyword hits to the provided TableReportModules.
     * @param tableModules modules to report on
     */
    @SuppressWarnings("deprecation")
    private void writeKeywordHits(List<TableReportModule> tableModules, String comment, HashSet<String> tagNamesFilter) {
        ResultSet listsRs = null;
        try {
            // Query for keyword lists
            listsRs = skCase.runQuery("SELECT att.value_text AS list " +
                                                "FROM blackboard_attributes AS att, blackboard_artifacts AS art " +
                                                "WHERE att.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " " +
                                                    "AND art.artifact_type_id = " + ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + " " +
                                                    "AND att.artifact_id = art.artifact_id " + 
                                                "GROUP BY list");
            List<String> lists = new ArrayList<>();
            while(listsRs.next()) {
                String list = listsRs.getString("list");
                if(list.isEmpty()) {
                    list = "User Searches";
                }
                lists.add(list);
            }
            
            // Make keyword data type and give them set index
            for (TableReportModule module : tableModules) {
                module.startDataType(ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName(), comment);
                module.addSetIndex(lists);
                tableProgress.get(module).updateStatusLabel("Now processing "
                        + ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName() + "...");
            }
        }
        catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to query keyword lists.", ex);
        } finally {
            if (listsRs != null) {
                try {
                    skCase.closeRunQuery(listsRs);
                } catch (SQLException ex) {
                }
            }
        }
        
        ResultSet rs = null;
        try {
            // Query for keywords
            rs = skCase.runQuery("SELECT art.artifact_id, art.obj_id, att1.value_text AS keyword, att2.value_text AS preview, att3.value_text AS list, f.name AS name " +
                                           "FROM blackboard_artifacts AS art, blackboard_attributes AS att1, blackboard_attributes AS att2, blackboard_attributes AS att3, tsk_files AS f " +
                                           "WHERE (att1.artifact_id = art.artifact_id) " +
                                                 "AND (att2.artifact_id = art.artifact_id) " + 
                                                 "AND (att3.artifact_id = art.artifact_id) " + 
                                                 "AND (f.obj_id = art.obj_id) " +
                                                 "AND (att1.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID() + ") " +
                                                 "AND (att2.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID() + ") " +
                                                 "AND (att3.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ") " +
                                                 "AND (art.artifact_type_id = " + ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID() + ") " +
                                           "ORDER BY list, keyword, name");
            String currentKeyword = "";
            String currentList = "";
            while (rs.next()) {
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
               HashSet<String> uniqueTagNames = new HashSet<>();
               ResultSet tagNameRows = skCase.runQuery("SELECT display_name FROM tag_names WHERE artifact_id = " + rs.getLong("artifact_id"));
               while (tagNameRows.next()) {
                   uniqueTagNames.add(tagNameRows.getString("display_name"));
               }
               if(failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                   continue;
               }                    
               String tagsList = makeCommaSeparatedList(uniqueTagNames);
                                                        
                Long objId = rs.getLong("obj_id");
                String keyword = rs.getString("keyword");
                String preview = rs.getString("preview");
                String list = rs.getString("list");
                String uniquePath = "";

                 try {
                    uniquePath = skCase.getAbstractFileById(objId).getUniquePath();
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex);
                }

                // If the lists aren't the same, we've started a new list
                if((!list.equals(currentList) && !list.isEmpty()) || (list.isEmpty() && !currentList.equals("User Searches"))) {
                    if(!currentList.isEmpty()) {
                        for (TableReportModule module : tableModules) {
                            module.endTable();
                            module.endSet();
                        }
                    }
                    currentList = list.isEmpty() ? "User Searches" : list;
                    currentKeyword = ""; // reset the current keyword because it's a new list
                    for (TableReportModule module : tableModules) {
                        module.startSet(currentList);
                        tableProgress.get(module).updateStatusLabel("Now processing "
                                + ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName()
                                + " (" + currentList + ")...");
                    }
                }
                if (!keyword.equals(currentKeyword)) {
                    if(!currentKeyword.equals("")) {
                        for (TableReportModule module : tableModules) {
                            module.endTable();
                        }
                    }
                    currentKeyword = keyword;
                    for (TableReportModule module : tableModules) {
                        module.addSetElement(currentKeyword);
                        module.startTable(getArtifactTableColumnHeaders(ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()));
                    }
                }
                
                String previewreplace = EscapeUtil.escapeHtml(preview);
                for (TableReportModule module : tableModules) {
                    module.addRow(Arrays.asList(new String[] {previewreplace.replaceAll("<!", ""), uniquePath, tagsList}));
                }
            }
            
            // Finish the current data type
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endDataType();
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to query keywords.", ex);
        } finally {
            if (rs != null) {
                try {
                    skCase.closeRunQuery(rs);
                } catch (SQLException ex) {
                }
            }
        }
    }
    
    /**
     * Write the hash set hits to the provided TableReportModules.
     * @param tableModules modules to report on
     */
    @SuppressWarnings("deprecation")
    private void writeHashsetHits(List<TableReportModule> tableModules,  String comment, HashSet<String> tagNamesFilter) {
        ResultSet listsRs = null;
        try {
            // Query for hashsets
            listsRs = skCase.runQuery("SELECT att.value_text AS list " +
                                                "FROM blackboard_attributes AS att, blackboard_artifacts AS art " +
                                                "WHERE att.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + " " +
                                                    "AND art.artifact_type_id = " + ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() + " " +
                                                    "AND att.artifact_id = art.artifact_id " + 
                                                "GROUP BY list");
            List<String> lists = new ArrayList<>();
            while(listsRs.next()) {
                lists.add(listsRs.getString("list"));
            }
            
            for (TableReportModule module : tableModules) {
                module.startDataType(ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName(), comment);
                module.addSetIndex(lists);
                tableProgress.get(module).updateStatusLabel("Now processing "
                        + ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName() + "...");
            }
        } catch (SQLException ex) {        
            logger.log(Level.SEVERE, "Failed to query hashset lists.", ex);
        } finally {
            if (listsRs != null) {
                try {
                    skCase.closeRunQuery(listsRs);
                } catch (SQLException ex) {
                }
            }
        }
        
        ResultSet rs = null;
        try {
            // Query for hashset hits
            rs = skCase.runQuery("SELECT art.artifact_id, art.obj_id, att.value_text AS setname, f.name AS name, f.size AS size " +
                                           "FROM blackboard_artifacts AS art, blackboard_attributes AS att, tsk_files AS f " +
                                           "WHERE (att.artifact_id = art.artifact_id) " +
                                                 "AND (f.obj_id = art.obj_id) " +
                                                 "AND (att.attribute_type_id = " + ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID() + ") " +
                                                 "AND (art.artifact_type_id = " + ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID() + ") " +
                                           "ORDER BY setname, name, size");
            String currentSet = "";
            while (rs.next()) {
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
                HashSet<String> uniqueTagNames = new HashSet<>();
                ResultSet tagNameRows = skCase.runQuery("SELECT display_name FROM tag_names WHERE artifact_id = " + rs.getLong("artifact_id"));
                while (tagNameRows.next()) {
                    uniqueTagNames.add(tagNameRows.getString("display_name"));
                }
                if(failsTagFilter(uniqueTagNames, tagNamesFilter)) {
                    continue;
                }                    
                String tagsList = makeCommaSeparatedList(uniqueTagNames);
                                                                        
                Long objId = rs.getLong("obj_id");
                String set = rs.getString("setname");
                String size = rs.getString("size");
                String uniquePath = "";
                
                try {
                    uniquePath = skCase.getAbstractFileById(objId).getUniquePath();
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Failed to get Abstract File from ID.", ex);
                }

                // If the sets aren't the same, we've started a new set
                if(!set.equals(currentSet)) {
                    if(!currentSet.isEmpty()) {
                        for (TableReportModule module : tableModules) {
                            module.endTable();
                            module.endSet();
                        }
                    }
                    currentSet = set;
                    for (TableReportModule module : tableModules) {
                        module.startSet(currentSet);
                        module.startTable(getArtifactTableColumnHeaders(ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()));
                        tableProgress.get(module).updateStatusLabel("Now processing "
                                + ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName()
                                + " (" + currentSet + ")...");
                    }
                }
                
                // Add a row for this hit to every module
                for (TableReportModule module : tableModules) {
                    module.addRow(Arrays.asList(new String[] {uniquePath, size, tagsList}));
                }
            }
            
            // Finish the current data type
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).increment();
                module.endDataType();
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to query hashsets hits.", ex);
        } finally {
            if (rs != null) {
                try {
                    skCase.closeRunQuery(rs);
                } catch (SQLException ex) {
                }
            }
        }
    }
        
    /**
     * For a given artifact type ID, return the list of the row titles we're reporting on.
     * 
     * @param artifactTypeId artifact type ID
     * @return List<String> row titles
     */
    private List<String> getArtifactTableColumnHeaders(int artifactTypeId) {
        ArrayList<String> columnHeaders;

        BlackboardArtifact.ARTIFACT_TYPE type = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifactTypeId);        
        switch (type) {
            case TSK_WEB_BOOKMARK:
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"URL", "Title", "Date Created", "Program", "Source File"}));
                break;
            case TSK_WEB_COOKIE: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"URL", "Date/Time", "Name", "Value", "Program", "Source File"}));
                break;
            case TSK_WEB_HISTORY: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"URL", "Date Accessed", "Referrer", "Title", "Program", "Source File"}));
                break;
            case TSK_WEB_DOWNLOAD: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Destination", "Source URL", "Date Accessed", "Program", "Source File"}));
                break;
            case TSK_RECENT_OBJECT: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Path", "Date/Time", "Source File"}));
                break;
            case TSK_INSTALLED_PROG: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Program Name", "Install Date/Time", "Source File"}));
                break;
            case TSK_KEYWORD_HIT: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Preview", "Source File"}));
                break;
            case TSK_HASHSET_HIT: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"File", "Size"}));
                break;
            case TSK_DEVICE_ATTACHED: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Name", "Device ID", "Date/Time", "Source File"}));
                break;
            case TSK_WEB_SEARCH_QUERY: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Text", "Domain", "Date Accessed", "Program Name", "Source File"}));
                break;
            case TSK_METADATA_EXIF: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Date Taken", "Device Manufacturer", "Device Model", "Latitude", "Longitude", "Source File"}));
                break;
            case TSK_CONTACT: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Person Name", "Phone Number", "Phone Number (Home)", "Phone Number (Office)", "Phone Number (Mobile)", "Email", "Source File"  }));
                break;
            case TSK_MESSAGE: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Message Type", "Direction", "Date/Time",  "From Phone Number", "From Email", "To Phone Number", "To Email", "Subject", "Text", "Source File"  }));
                break;
            case TSK_CALLLOG:
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Person Name", "Phone Number", "Date/Time", "Direction", "Source File"  }));
                break;
            case TSK_CALENDAR_ENTRY:
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Calendar Entry Type", "Description", "Start Date/Time", "End Date/Time", "Location", "Source File"  }));
                break;
            case TSK_SPEED_DIAL_ENTRY:
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Short Cut", "Person Name", "Phone Number",  "Source File"  }));
                break;
            case TSK_BLUETOOTH_PAIRING:
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Device Name", "Device Address", "Date/Time",  "Source File"  }));
                break;
            case TSK_GPS_TRACKPOINT:
                 columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Latitude", "Longitude", "Altitude",  "Name", "Location Address", "Date/Time", "Source File"  }));
                break;
            case TSK_GPS_BOOKMARK:
                 columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Latitude", "Longitude", "Altitude",  "Name", "Location Address", "Date/Time", "Source File"  }));
                break;
            case TSK_GPS_LAST_KNOWN_LOCATION:
                 columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Latitude", "Longitude", "Altitude",  "Name", "Location Address", "Date/Time", "Source File"  }));
                break;
            case TSK_GPS_SEARCH:
                 columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Latitude", "Longitude", "Altitude",  "Name", "Location Address", "Date/Time", "Source File"  }));
                break;
            case TSK_SERVICE_ACCOUNT:
                 columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Category", "User ID", "Password",  "Person Name", "App Name", "URL", "App Path", "Description", "ReplyTo Address", "Mail Server", "Source File" }));
                break;
            case TSK_TOOL_OUTPUT: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Program Name", "Text", "Source File"}));
                break;
            default:
                return null;
        }
        columnHeaders.add("Tags");
        
        return columnHeaders;
    }
    
    /**
     * Map all BlackboardAttributes' values in a list of BlackboardAttributes to each attribute's attribute
     * type ID, using module's dateToString method for date/time conversions if a module is supplied.
     * 
     * @param attList list of BlackboardAttributes to be mapped
     * @param module the TableReportModule the mapping is for
     * @return Map<Integer, String> of the BlackboardAttributes mapped to their attribute type ID
     */
    public Map<Integer, String> getMappedAttributes(List<BlackboardAttribute> attList, TableReportModule... module) {
        Map<Integer, String> attributes = new HashMap<>();
        int size = ATTRIBUTE_TYPE.values().length;
        for (int n = 0; n <= size; n++) {
            attributes.put(n, "");
        }
        for (BlackboardAttribute tempatt : attList) {
            String value = "";
            Integer type = tempatt.getAttributeTypeID();
            if (type.equals(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()) || 
                type.equals(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()) ||
                type.equals(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID()) ||
                type.equals(ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED.getTypeID()) ||
                type.equals(ATTRIBUTE_TYPE.TSK_DATETIME_SENT.getTypeID()) ||
                type.equals(ATTRIBUTE_TYPE.TSK_DATETIME_RCVD.getTypeID()) ||
                type.equals(ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()) ||
                type.equals(ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID())
                    ) {
                if (module.length > 0) {
                    value = module[0].dateToString(tempatt.getValueLong());
                } else {
                    SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    value = sdf.format(new java.util.Date((tempatt.getValueLong() * 1000)));
                }
            } else if(type.equals(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()) ||
                    type.equals(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()) ||
                    type.equals(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID())) {
                value = Double.toString(tempatt.getValueDouble());
            } else {
                value = tempatt.getValueString();
            }
            if (value == null) {
                value = "";
            }
            value = EscapeUtil.escapeHtml(value);
            attributes.put(type, value);
        }
        return attributes;
    }
    
    /**
     * Converts a collection of strings into a single string of comma-separated items
     * 
     * @param items A collection of strings
     * @return A string of comma-separated items 
     */
    private String makeCommaSeparatedList(Collection<String> items) {
        String list = "";
        for (Iterator<String> iterator = items.iterator(); iterator.hasNext(); ) {
            list += iterator.next() + (iterator.hasNext() ? ", " : "");
        }
        return list;
    }
        
    /**
     * Get a list of Strings with all the row values for a given BlackboardArtifact and 
     * list of BlackboardAttributes Entry, basing the date/time field on the given TableReportModule.
     * 
     * @param entry BlackboardArtifact and list of BlackboardAttributes
     * @param module TableReportModule for which the row is desired
     * @return List<String> row values
     * @throws TskCoreException 
     */
    private List<String> getArtifactRow(ArtifactData artifactData, TableReportModule module) {
        Map<Integer, String> attributes = getMappedAttributes(artifactData.getAttributes(), module);
        
        List<String> rowData = new ArrayList<>();
        BlackboardArtifact.ARTIFACT_TYPE type = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifactData.getArtifact().getArtifactTypeID());        
        switch (type) {
            case TSK_WEB_BOOKMARK:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_CREATED.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_WEB_COOKIE:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_WEB_HISTORY:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_WEB_DOWNLOAD:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PATH.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_RECENT_OBJECT:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PATH.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_INSTALLED_PROG:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_DEVICE_ATTACHED:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_WEB_SEARCH_QUERY:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
            case TSK_METADATA_EXIF: 
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
             case TSK_CONTACT:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME_PERSON.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_OFFICE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_EMAIL.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
             case TSK_MESSAGE:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_CALLLOG:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME_PERSON.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_CALENDAR_ENTRY:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_CALENDAR_ENTRY_TYPE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_SPEED_DIAL_ENTRY:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_SHORTCUT.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME_PERSON.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_BLUETOOTH_PAIRING:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_GPS_TRACKPOINT:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_GPS_BOOKMARK:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_GPS_LAST_KNOWN_LOCATION:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_GPS_SEARCH:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_ALTITUDE.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
              case TSK_SERVICE_ACCOUNT:
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_CATEGORY.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_USER_ID.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PASSWORD.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PATH.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_EMAIL_REPLYTO.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_SERVER_NAME.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
             case TSK_TOOL_OUTPUT: 
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                rowData.add(attributes.get(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()));
                rowData.add(getFileUniquePath(artifactData.getObjectID()));
                break;
        }
        rowData.add(makeCommaSeparatedList(artifactData.getTags()));
        
        return rowData;
    }
    
    /**
     * Given a tsk_file's obj_id, return the unique path of that file.
     * 
     * @param objId tsk_file obj_id
     * @return String unique path
     */
    private String getFileUniquePath(long objId) {
        try {
            return skCase.getAbstractFileById(objId).getUniquePath();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex);
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
        
        ArtifactData(BlackboardArtifact artifact, List<BlackboardAttribute> attrs, HashSet<String> tags) {
            this.artifact = artifact;
            this.attributes = attrs;
            this.tags = tags;
        }
        
        public BlackboardArtifact getArtifact() { return artifact; }
        
        public List<BlackboardAttribute> getAttributes() { return attributes; }
        
        public HashSet<String> getTags() { return tags; }
        
        public long getArtifactID() { return artifact.getArtifactID(); }
        
        public long getObjectID() { return artifact.getObjectID(); }

        @Override
        /**
         * Compares ArtifactData objects by the first attribute they have in
         * common in their List<BlackboardAttribute>. 
         * 
         * If all attributes are the same, they are assumed duplicates and are
         * compared by their artifact id. Should only be used with attributes
         * of the same type.
         */
        public int compareTo(ArtifactData data) {
            // Get all the attributes for each artifact
            int size = ATTRIBUTE_TYPE.values().length;
            Map<Integer, String> att1 = getMappedAttributes(this.attributes);
            Map<Integer, String> att2 = getMappedAttributes(data.getAttributes());
            // Compare the attributes one-by-one looking for differences
            for(int i=0; i < size; i++) {
                String a1 = att1.get(i);
                String a2 = att2.get(i);
                if((!a1.equals("") && !a2.equals("")) && a1.compareTo(a2) != 0) {
                    return a1.compareTo(a2);
                }
            }
            // If all attributes are the same, they're most likely duplicates so sort by artifact ID
            return ((Long)this.getArtifactID()).compareTo((Long)data.getArtifactID());
        }
    }
}


