/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import org.sleuthkit.autopsy.datamodel.Tags;
import org.sleuthkit.autopsy.report.ReportProgressPanel.ReportStatus;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Generates all TableReportModules and GeneralReportModules, given whether each module for both
 * types is enabled or disabled, and the base report path to save them at.
 * 
 * After creating an instance of ReportGenerator, one must tell it which reports to run,
 * TableReportModules on Tags or Artifacts, and the GeneralReportModules.
 * Then, one calls displayProgressPanels() to display the progress to the user.
 */
public class ReportGenerator {
    private static final Logger logger = Logger.getLogger(ReportGenerator.class.getName());
    
    private Case currentCase = Case.getCurrentCase();
    private SleuthkitCase skCase = currentCase.getSleuthkitCase();
    
    private Map<TableReportModule, ReportProgressPanel> tableProgress;
    private Map<GeneralReportModule, ReportProgressPanel> generalProgress;
    
    private String reportPath;
    private ReportGenerationPanel panel = new ReportGenerationPanel();
    
    static final String REPORTS_DIR = "Reports";
        
    ReportGenerator(Map<TableReportModule, Boolean> tableModuleStates, Map<GeneralReportModule, Boolean> generalModuleStates) {
        // Setup the reporting directory to be [CASE DIRECTORY]/Reports/[Case name] [Timestamp]/
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datenotime = dateFormat.format(date);
        this.reportPath = currentCase.getCaseDirectory() + File.separator + REPORTS_DIR + File.separator + currentCase.getName() + " " + datenotime + File.separator;
        // Create the reporting directory
        try {
            FileUtil.createFolder(new File(this.reportPath));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to make report folder, may be unable to generate reports.", ex);
        }
        
        // Initialize the progress panels
        generalProgress = new HashMap<>();
        tableProgress = new HashMap<>();
        setupProgressPanels(tableModuleStates, generalModuleStates);
    }
    
    /**
     * For every ReportModule which the user enabled, create a ReportProgressPanel for that report.
     * 
     * @param tableModuleStates the enabled/disabled state of each TableReportModule
     * @param generalModuleStates the enabled/disabled state of each GeneralReportModule
     */
    private void setupProgressPanels(Map<TableReportModule, Boolean> tableModuleStates, Map<GeneralReportModule, Boolean> generalModuleStates) {
        for (Entry<TableReportModule, Boolean> entry : tableModuleStates.entrySet()) {
            if (entry.getValue()) {
                TableReportModule module = entry.getKey();
                tableProgress.put(module, panel.addReport(module.getName(), reportPath + module.getFilePath()));
            }
        }
        for (Entry<GeneralReportModule, Boolean> entry : generalModuleStates.entrySet()) {
            if (entry.getValue()) {
                GeneralReportModule module = entry.getKey();
                generalProgress.put(module, panel.addReport(module.getName(), reportPath + module.getFilePath()));
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
     * Generate the GeneralReportModule reports in a new SwingWorker.
     */
    public void generateGeneralReports() {
        GeneralWorker worker = new GeneralWorker();
        worker.execute();
    }
    
    /**
     * Generate the TableReportModule reports on Blackboard Artifacts in a new SwingWorker.
     * 
     * @param artifactTypeSelections the enabled/disabled state of the artifact types to be included in the report
     * @param tagSelections the enabled/disabled state of the tags to be included in the report
     */
    public void generateArtifactTableReports(Map<ARTIFACT_TYPE, Boolean> artifactTypeSelections, Map<String, Boolean> tagSelections) {
        ArtifactsReportsWorker worker = new ArtifactsReportsWorker(artifactTypeSelections, tagSelections);
        worker.execute();
    }
    
    /**
     * SwingWorker to generate a report on all GeneralReportModules.
     */
    private class GeneralWorker extends SwingWorker<Integer, Integer> {

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
     * SwingWorker to generate reports on blackboard artifacts.
     */
    private class ArtifactsReportsWorker extends SwingWorker<Integer, Integer> {
        private List<TableReportModule> tableModules  = new ArrayList<>();
        private List<ARTIFACT_TYPE> artifactTypes  = new ArrayList<>();
        private HashSet<String> tagNamesFilter = new HashSet<>();
        
        // Create an ArtifactWorker with the enabled/disabled state of all Artifacts
        ArtifactsReportsWorker(Map<ARTIFACT_TYPE, Boolean> artifactTypeSelections, Map<String, Boolean> tagSelections) {
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
            
            // Get the tags selected by the user.
            if (tagSelections != null) {
                for (Entry<String, Boolean> entry : tagSelections.entrySet()) {
                    if (entry.getValue() == true) {
                        tagNamesFilter.add(entry.getKey());
                    }
                }
            }
        }

        @Override
        protected Integer doInBackground() throws Exception {
            // Start the report
            for (TableReportModule module : tableModules) {
                ReportProgressPanel progress = tableProgress.get(module);
                if (progress.getStatus() != ReportStatus.CANCELED) {
                    module.startReport(reportPath);
                    progress.start();
                    progress.setIndeterminate(false);
                    progress.setMaximumProgress(ARTIFACT_TYPE.values().length);
                }
            }
            
            // Make a comment on the tags filter.
            StringBuilder comment = new StringBuilder();
            if (!tagNamesFilter.isEmpty()) {
                comment.append("This report only includes files and artifacts tagged with: ");
                comment.append(makeCommaSeparatedList(tagNamesFilter));
            }            
            
            // For every enabled artifact type
            for (ARTIFACT_TYPE type : artifactTypes) {
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
                
                // If the type is keyword hit or hashset hit, use the helper
                if (type.equals(ARTIFACT_TYPE.TSK_KEYWORD_HIT)) {
                    writeKeywordHits(tableModules, comment.toString(), tagNamesFilter);
                    continue;
                } else if (type.equals(ARTIFACT_TYPE.TSK_HASHSET_HIT)) {
                    writeHashsetHits(tableModules, comment.toString(), tagNamesFilter);
                    continue;
                }

                // Otherwise setup the unsorted list of artifacts, to later be sorted
                ArtifactComparator c = new ArtifactComparator();
                List<Entry<BlackboardArtifact, List<BlackboardAttribute>>> unsortedArtifacts = new ArrayList<>();
                try {
                    // For every artifact of the current type, add it and it's attributes to a list
                    for (BlackboardArtifact artifact : skCase.getBlackboardArtifacts(type)) {
                        try {
                            unsortedArtifacts.add(new ArtifactEntry<BlackboardArtifact, List<BlackboardAttribute>>(artifact, skCase.getBlackboardAttributes(artifact)));
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Failed to get Blackboard Attributes when generating report.", ex);
                        }
                    }
                } 
                catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Failed to get Blackboard Artifacts when generating report.", ex);
                }
                
                // The most efficient way to sort all the Artifacts is to add them to a List, and then
                // sort that List based off a Comparator. Adding to a TreeMap/Set/List sorts the list
                // each time an element is added, which adds unnecessary overhead if we only need it sorted once.
                Collections.sort(unsortedArtifacts, c);

                // Get the column headers appropriate for the artifact type.
                List<String> columnHeaders = getArtifactTableColumnHeaders(type.getTypeID());
                                                                
                // For every module start a new data type and table for the current artifact type.
                for (TableReportModule module : tableModules) {
                    tableProgress.get(module).updateStatusLabel("Now processing " + type.getDisplayName() + "...");                    
                                
                    // This is a temporary workaround to avoid modifying the TableReportModule interface.
                    if (module instanceof ReportHTML) {
                        ReportHTML htmlReportModule = (ReportHTML)module;
                        htmlReportModule.startDataType(type.getDisplayName(), comment.toString());                        
                        htmlReportModule.startTable(columnHeaders, type);
                    }
                    else if (module instanceof ReportExcel) {
                        ReportExcel excelReportModule = (ReportExcel)module;
                        excelReportModule.startDataType(type.getDisplayName(), comment.toString());                        
                        excelReportModule.startTable(columnHeaders);                    
                    }
                    else {
                        module.startDataType(type.getDisplayName());                        
                        module.startTable(columnHeaders);
                    }
                }
                
                // Add a row to the table for every artifact of the current type that satisfies the tags filter, if any.
                for (Entry<BlackboardArtifact, List<BlackboardAttribute>> artifactEntry : unsortedArtifacts) {                    
                    // Get any tags associated with the artifact and apply the tags filter, if any.
                    HashSet<String> tags = Tags.getUniqueTagNames(artifactEntry.getKey());
                    if (failsTagFilter(tags, tagNamesFilter)) {
                        continue;
                    }                    
                    String tagsList = makeCommaSeparatedList(tags);
                    
                    // Add the row data to all of the reports.
                    for (TableReportModule module : tableModules) {
                        // Get the row data for this type of artifact.
                        List<String> rowData; 
                        rowData = getArtifactRow(artifactEntry, module);   
                        
                        // Add the list of tag names if the artifact is not itself as tag.
                        if (artifactEntry.getKey().getArtifactTypeID() != ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID() &&
                            artifactEntry.getKey().getArtifactTypeID() != ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID())
                        {
                            rowData.add(tagsList);
                        }

                        // This is a temporary workaround to avoid modifying the TableReportModule interface.
                        if (module instanceof ReportHTML) {
                            ReportHTML htmlReportModule = (ReportHTML)module;
                            htmlReportModule.addRow(rowData, artifactEntry.getKey());
                        }
                        else {      
                            module.addRow(rowData);
                        }                        
                    }
                }
                
                // Finish up this data type
                for (TableReportModule module : tableModules) {
                    tableProgress.get(module).increment();
                    module.endTable();
                    module.endDataType();
                }
            }
            
            // End the report
            for (TableReportModule module : tableModules) {
                tableProgress.get(module).complete();
                module.endReport();
            }
            
            return 0;
        }
    }
    
    private Boolean failsTagFilter(HashSet<String> tags, HashSet<String> tagsFilter) 
    {
        if (null == tagsFilter || tagsFilter.isEmpty()) {
            return false;
        }

        HashSet<String> filteredTags = new HashSet<>(tags);
        filteredTags.retainAll(tagsFilter);
        return filteredTags.isEmpty();
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
                // This is a temporary workaround to avoid modifying the TableReportModule interface.
                if (module instanceof ReportHTML) {
                    ReportHTML htmlReportModule = (ReportHTML)module;
                    htmlReportModule.startDataType(ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName(), comment);                        
                }
                else if (module instanceof ReportExcel) {
                    ReportExcel excelReportModule = (ReportExcel)module;
                    excelReportModule.startDataType(ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName(), comment);                                            
                }
                else {
                   module.startDataType(ARTIFACT_TYPE.TSK_KEYWORD_HIT.getDisplayName());
                }            
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
                HashSet<String> tags = Tags.getUniqueTagNames(rs.getLong("artifact_id"), ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID());
                if (failsTagFilter(tags, tagNamesFilter)) {
                    continue;
                }                    
                String tagsList = makeCommaSeparatedList(tags);
                                                        
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
                // This is a temporary workaround to avoid modifying the TableReportModule interface.
                if (module instanceof ReportHTML) {
                    ReportHTML htmlReportModule = (ReportHTML)module;
                    htmlReportModule.startDataType(ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName(), comment);                        
                }
                else if (module instanceof ReportExcel) {
                    ReportExcel excelReportModule = (ReportExcel)module;
                    excelReportModule.startDataType(ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName(), comment);                                            
                }
                else {
                   module.startDataType(ARTIFACT_TYPE.TSK_HASHSET_HIT.getDisplayName());
                }            
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
                HashSet<String> tags = Tags.getUniqueTagNames(rs.getLong("artifact_id"), ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID());
                if (failsTagFilter(tags, tagNamesFilter)) {
                    continue;
                }                    
                String tagsList = makeCommaSeparatedList(tags);
                                                                        
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
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"URL", "Title", "Date Accessed", "Program", "Source File"}));
                break;
            case TSK_WEB_COOKIE: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"URL", "Date/Time", "Name", "Value", "Program", "Source File"}));
                break;
            case TSK_WEB_HISTORY: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"URL", "Date Accessed", "Referrer", "Name", "Program", "Source File"}));
                break;
            case TSK_WEB_DOWNLOAD: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Destination", "Source URL", "Date Accessed", "Program", "Source File"}));
                break;
            case TSK_RECENT_OBJECT: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Path", "Source File"}));
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
            case TSK_TAG_FILE: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"File", "Tag", "Comment"}));
                break;
            case TSK_TAG_ARTIFACT: 
                columnHeaders = new ArrayList<>(Arrays.asList(new String[] {"Result Type", "Tag", "Comment", "Source File"}));
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
            default:
                return null;
        }

        if (artifactTypeId != ARTIFACT_TYPE.TSK_TAG_FILE.getTypeID() && 
            artifactTypeId != ARTIFACT_TYPE.TSK_TAG_ARTIFACT.getTypeID()) {
            columnHeaders.add("Tags");
        }
        
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
    private List<String> getArtifactRow(Entry<BlackboardArtifact, List<BlackboardAttribute>> entry, TableReportModule module) throws TskCoreException {
        Map<Integer, String> attributes = getMappedAttributes(entry.getValue(), module);
        
        BlackboardArtifact.ARTIFACT_TYPE type = BlackboardArtifact.ARTIFACT_TYPE.fromID(entry.getKey().getArtifactTypeID());
        
        switch (type) {
            case TSK_WEB_BOOKMARK:
                List<String> bookmark = new ArrayList<>();
                bookmark.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                bookmark.add(attributes.get(ATTRIBUTE_TYPE.TSK_TITLE.getTypeID()));
                bookmark.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()));
                bookmark.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                bookmark.add(getFileUniquePath(entry.getKey().getObjectID()));
                return bookmark;
            case TSK_WEB_COOKIE:
                List<String> cookie = new ArrayList<>();
                cookie.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                cookie.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                cookie.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                cookie.add(attributes.get(ATTRIBUTE_TYPE.TSK_VALUE.getTypeID()));
                cookie.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                cookie.add(getFileUniquePath(entry.getKey().getObjectID()));
                return cookie;
            case TSK_WEB_HISTORY:
                List<String> history = new ArrayList<>();
                history.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                history.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()));
                history.add(attributes.get(ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID()));
                history.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME.getTypeID()));
                history.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                history.add(getFileUniquePath(entry.getKey().getObjectID()));
                return history;
            case TSK_WEB_DOWNLOAD:
                List<String> download = new ArrayList<>();
                download.add(attributes.get(ATTRIBUTE_TYPE.TSK_PATH.getTypeID()));
                download.add(attributes.get(ATTRIBUTE_TYPE.TSK_URL.getTypeID()));
                download.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()));
                download.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                download.add(getFileUniquePath(entry.getKey().getObjectID()));
                return download;
            case TSK_RECENT_OBJECT:
                List<String> recent = new ArrayList<>();
                recent.add(attributes.get(ATTRIBUTE_TYPE.TSK_PATH.getTypeID()));
                recent.add(getFileUniquePath(entry.getKey().getObjectID()));
                return recent;
            case TSK_INSTALLED_PROG:
                List<String> installed = new ArrayList<>();
                installed.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                installed.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                installed.add(getFileUniquePath(entry.getKey().getObjectID()));
                return installed;
            case TSK_DEVICE_ATTACHED:
                List<String> devices = new ArrayList<>();
                devices.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID()));
                devices.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_ID.getTypeID()));
                devices.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                devices.add(getFileUniquePath(entry.getKey().getObjectID()));
                return devices;
            case TSK_WEB_SEARCH_QUERY:
                List<String> search = new ArrayList<>();
                search.add(attributes.get(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()));
                search.add(attributes.get(ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID()));
                search.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()));
                search.add(attributes.get(ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID()));
                search.add(getFileUniquePath(entry.getKey().getObjectID()));
                return search;
            case TSK_METADATA_EXIF: 
                List<String> exif = new ArrayList<>();
                exif.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                exif.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_MAKE.getTypeID()));
                exif.add(attributes.get(ATTRIBUTE_TYPE.TSK_DEVICE_MODEL.getTypeID()));
                exif.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()));
                exif.add(attributes.get(ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()));
                exif.add(getFileUniquePath(entry.getKey().getObjectID()));
                return exif;
            case TSK_TAG_FILE:
                List<String> taggedFileRow = new ArrayList<>();
                AbstractFile taggedFile = getAbstractFile(entry.getKey().getObjectID());
                if (taggedFile != null) {
                    taggedFileRow.add(taggedFile.getUniquePath());
                } else {
                    taggedFileRow.add("");
                }
                taggedFileRow.add(attributes.get(ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID()));
                taggedFileRow.add(attributes.get(ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID()));
                return taggedFileRow;
            case TSK_TAG_ARTIFACT:
                List<String> taggedArtifactRow = new ArrayList<>();
                String taggedArtifactType = "";
                for (BlackboardAttribute attr : entry.getValue()) {
                    if (attr.getAttributeTypeID() == ATTRIBUTE_TYPE.TSK_TAGGED_ARTIFACT.getTypeID()) {
                        BlackboardArtifact taggedArtifact = getArtifact(attr.getValueLong());
                        if (taggedArtifact != null) {
                            taggedArtifactType = taggedArtifact.getDisplayName();
                        }
                        break;
                    }
                }
                taggedArtifactRow.add(taggedArtifactType);
                taggedArtifactRow.add(attributes.get(ATTRIBUTE_TYPE.TSK_TAG_NAME.getTypeID()));
                taggedArtifactRow.add(attributes.get(ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID()));
                AbstractFile sourceFile = getAbstractFile(entry.getKey().getObjectID());
                if (sourceFile != null) {
                    taggedArtifactRow.add(sourceFile.getUniquePath());
                } else {
                    taggedArtifactRow.add("");
                }
                return taggedArtifactRow;
             case TSK_CONTACT:
                List<String> contact = new ArrayList<>();
                contact.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME_PERSON.getTypeID()));
                contact.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID()));
                contact.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_HOME.getTypeID()));
                contact.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_OFFICE.getTypeID()));
                contact.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_MOBILE.getTypeID()));
                contact.add(attributes.get(ATTRIBUTE_TYPE.TSK_EMAIL.getTypeID()));
                contact.add(getFileUniquePath(entry.getKey().getObjectID()));
                return contact;
             case TSK_MESSAGE:
                List<String> message = new ArrayList<>();
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_EMAIL_FROM.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_EMAIL_TO.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_SUBJECT.getTypeID()));
                message.add(attributes.get(ATTRIBUTE_TYPE.TSK_TEXT.getTypeID()));
                message.add(getFileUniquePath(entry.getKey().getObjectID()));
                return message;
              case TSK_CALLLOG:
                List<String> call_log = new ArrayList<>();
                call_log.add(attributes.get(ATTRIBUTE_TYPE.TSK_NAME_PERSON.getTypeID()));
                call_log.add(attributes.get(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER.getTypeID()));
                call_log.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME.getTypeID()));
                call_log.add(attributes.get(ATTRIBUTE_TYPE.TSK_DIRECTION.getTypeID()));
                call_log.add(getFileUniquePath(entry.getKey().getObjectID()));
                return call_log;
              case TSK_CALENDAR_ENTRY:
                List<String> calEntry = new ArrayList<>();
                calEntry.add(attributes.get(ATTRIBUTE_TYPE.TSK_CALENDAR_ENTRY_TYPE.getTypeID()));
                calEntry.add(attributes.get(ATTRIBUTE_TYPE.TSK_DESCRIPTION.getTypeID()));
                calEntry.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_START.getTypeID()));
                calEntry.add(attributes.get(ATTRIBUTE_TYPE.TSK_DATETIME_END.getTypeID()));
                calEntry.add(attributes.get(ATTRIBUTE_TYPE.TSK_LOCATION.getTypeID()));
                calEntry.add(getFileUniquePath(entry.getKey().getObjectID()));
                return calEntry;
                 
        }
        return null;
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
     * Given a tsk_file's obj_id, return the name of that file.
     * 
     * @param objId tsk_file obj_id
     * @return String name
     */
    private String getFileName(long objId) {
        try {
            return skCase.getAbstractFileById(objId).getName();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex);
        }
        return "";
    }
    
    /**
     * Return the file associated with a tsk_file obj_id.
     * 
     * @param objId tsk_file obj_id
     * @return AbstractFile associated with objId
     */
    private AbstractFile getAbstractFile(long objId) {
        try {
            return skCase.getAbstractFileById(objId);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get Abstract File by ID.", ex);
        }
        return null;
    }
    
    /**
     * Get a BlackboardArtifact.
     * 
     * @param long artifactId An artifact id
     * @return The BlackboardArtifact associated with the artifact id
     */
    private BlackboardArtifact getArtifact(long artifactId) {
        try {
            return skCase.getBlackboardArtifact(artifactId);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get blackboard artifact by ID.", ex);
        }
        return null;
    }
    
    /**
     * Map.Entry for BlackboardArtifacts and lists of BlackboardAttributes.
     * 
     * @param <K> BlackboardArtifact
     * @param <V> List<BlackboardAttribute>
     */
    private class ArtifactEntry<K, V> implements Map.Entry<BlackboardArtifact, List<BlackboardAttribute>> {
        BlackboardArtifact artifact;
        List<BlackboardAttribute> attributes;
        
        private ArtifactEntry(BlackboardArtifact artifact, List<BlackboardAttribute> attributes) {
            this.artifact = artifact;
            this.attributes = attributes;
        }

        @Override
        public BlackboardArtifact getKey() {
            return artifact;
        }

        @Override
        public List<BlackboardAttribute> getValue() {
            return attributes;
        }

        @Override
        public List<BlackboardAttribute> setValue(List<BlackboardAttribute> value) {
            List<BlackboardAttribute> old = attributes;
            attributes = value;
            return old;
        }
    }
    
    /**
     * Compares entries of BlackboardArtifacts and lists of BlackboardAttributes, sorting by
     * the first attribute both artifacts have in common. If all attributes are the same, they are
     * assumed duplicates, and they are sorted on artifact ID. Should only be used on artifacts
     * of similar types.
     */
    private class ArtifactComparator implements Comparator<Entry<BlackboardArtifact, List<BlackboardAttribute>>> {
        @Override
        public int compare(Entry<BlackboardArtifact, List<BlackboardAttribute>> art1, Entry<BlackboardArtifact, List<BlackboardAttribute>> art2) {
            // Get all the attributes for each artifact
            int size = ATTRIBUTE_TYPE.values().length;
            Map<Integer, String> att1 = getMappedAttributes(art1.getValue());
            Map<Integer, String> att2 = getMappedAttributes(art2.getValue());
            // Compare the attributes one-by-one looking for differences
            for(int i=0; i < size; i++) {
                String a1 = att1.get(i);
                String a2 = att2.get(i);
                if((!a1.equals("") && !a2.equals("")) && a1.compareTo(a2) != 0) {
                    return a1.compareTo(a2);
                }
            }
            // If all attributes are the same, they're most likely duplicates so sort by artifact ID
            return ((Long)art1.getKey().getArtifactID()).compareTo((Long)art2.getKey().getArtifactID());
        }
    }
}


