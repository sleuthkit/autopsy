/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.filequery;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Class to test the file search API. Allows the user to run searches and see results.
 */
@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.newpackage.FileSearchTestAction")
@ActionReference(path = "Menu/Tools", position = 1852, separatorBefore = 1851)
@ActionRegistration(displayName = "#CTL_FileSearchTestAction", lazy = false)
@NbBundle.Messages({"CTL_FileSearchTestAction=Test file search"})
public final class FileSearchTestAction extends CallableSystemAction {
    
    private final static Logger logger = Logger.getLogger(FileSearchTestAction.class.getName());
    private static final String DISPLAY_NAME = "Test file search";

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void performAction() {
        
        // Load the central repository database.
        EamDb crDb = null;
        if (EamDb.isEnabled()) {
            try {
                crDb = EamDb.getInstance();
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error loading central repository database", ex);
                return;
            }
        }
        
        FileDiscoveryDialog dialog = new FileDiscoveryDialog(null, true, Case.getCurrentCase().getSleuthkitCase(), crDb);
        
        // For testing, allow the user to run different searches in loop
        while (true) {
            
            // Display the dialog
            dialog.display();

            if (dialog.searchCancelled()) {
                return;
            }
            
            // Get the selected filters
            List<FileSearchFiltering.FileFilter> filters = dialog.getFilters();
        
            // Get the grouping attribute and group sorting method
            FileSearch.AttributeType groupingAttr = dialog.getGroupingAttribute();
            FileGroup.GroupSortingAlgorithm groupSortAlgorithm = dialog.getGroupSortingMethod();
            
            // Get the file sorting method
            FileSorter.SortingMethod fileSort = dialog.getFileSortingMethod();
            
            try {
            
                // Make a list of attributes that we want to add values for. This ensures the
                // ResultFile objects will have all needed fields set when it's time to group
                // and sort them. For example, if we're grouping by central repo frequency, we need
                // to make sure we've loaded those values before grouping.
                List<FileSearch.AttributeType> attrsForGroupingAndSorting = new ArrayList<>();
                attrsForGroupingAndSorting.add(groupingAttr);
                attrsForGroupingAndSorting.addAll(fileSort.getRequiredAttributes());

                // Run the search
                SearchResults results = FileSearch.runFileSearchDebug(filters, 
                    groupingAttr, 
                    groupSortAlgorithm, 
                    fileSort, 
                    attrsForGroupingAndSorting,
                    Case.getCurrentCase().getSleuthkitCase(), crDb);

                // Display the results
                ResultsDialog resultsDialog = new ResultsDialog(null, true, results.toString());
                resultsDialog.display();

                if ( ! resultsDialog.shouldRunAnotherSearch()) {
                    return;
                }

            } catch (FileSearchException ex) {
                logger.log(Level.SEVERE, "Error running file search test", ex);
                
                // Display the exception in the UI for easier debugging
                String message = ex.toString() + "\n" + ExceptionUtils.getStackTrace(ex);
                ResultsDialog resultsDialog = new ResultsDialog(null, true, message);
                resultsDialog.display();
                if (! resultsDialog.runAnotherSearch) {
                    return;
                }
            }
            
        }
    }

    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false; // run on edt
    }
}
