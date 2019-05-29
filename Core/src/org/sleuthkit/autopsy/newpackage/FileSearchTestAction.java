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
package org.sleuthkit.autopsy.newpackage;

import java.util.*;
import javax.swing.JDialog;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datamodel.utils.FileTypeUtils;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.datamodel.DataSource;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.newpackage.FileSearchTestAction")
@ActionReference(path = "Menu/Tools", position = 1852, separatorBefore = 1851)
@ActionRegistration(displayName = "#CTL_FileSearchTestAction", lazy = false)
@NbBundle.Messages({"CTL_FileSearchTestAction=Test file search"})
public final class FileSearchTestAction extends CallableSystemAction {

    private static final String DISPLAY_NAME = "Test file search thing";
    private ModalDialogProgressIndicator progressIndicator = null;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void performAction() {
        
        EamDb crDb = null;
        if (EamDb.isEnabled()) {
            try {
                crDb = EamDb.getInstance();
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
        }
        
        FileSearchDialog dialog = new FileSearchDialog(null, true);
        
        while ( ! dialog.searchCancelled()) {
            
            // Display the dialog
            dialog.setVisible(true);

            // Get the selected filters
            List<FileSearchFiltering.FileFilter> filters = dialog.getFilters();
        
            // Get the grouping attribute and group sorting method
            FileSearch.AttributeType groupingAttr = dialog.getGroupingAttribute();
            FileGroup.GroupSortingAlgorithm groupSortAlgorithm = dialog.getGroupSortingMethod();
            
            // Get the file sorting method
            FileSorter.SortingMethod fileSort = dialog.getFileSortingMethod();
            
            try {
            
                // Make a list of attributes that we want to add values for. Eventually this can be based on user input but
                // for now just add all of them.
                List<FileSearch.AttributeType> attributesNeededForSorting = Arrays.asList(new FileSearch.DataSourceAttribute(),
                        new FileSearch.FileSizeAttribute(), new FileSearch.FileTypeAttribute(), new FileSearch.FrequencyAttribute(),
                        new FileSearch.KeywordListAttribute(), new FileSearch.ParentPathAttribute());

                FileSearch.runFileSearch(filters, 
                    groupingAttr, 
                    groupSortAlgorithm, 
                    fileSort, 
                    attributesNeededForSorting,
                    Case.getCurrentCase().getSleuthkitCase(), crDb);



            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }
            
        }
        
        
        System.out.println("\n#########################\nTesting file search!!!");
        
        // Set up all the test filters
        FileSearchFiltering.FileFilter size_medSmallXS = new FileSearchFiltering.SizeFilter(Arrays.asList(FileSearchData.FileSize.MEDIUM, FileSearchData.FileSize.SMALL, FileSearchData.FileSize.XS));
        FileSearchFiltering.FileFilter size_XL = new FileSearchFiltering.SizeFilter(Arrays.asList(FileSearchData.FileSize.XL));
        FileSearchFiltering.FileFilter size_largeXL = new FileSearchFiltering.SizeFilter(Arrays.asList(FileSearchData.FileSize.LARGE, FileSearchData.FileSize.XL));
        FileSearchFiltering.FileFilter size_medLargeXL = new FileSearchFiltering.SizeFilter(Arrays.asList(FileSearchData.FileSize.MEDIUM, FileSearchData.FileSize.LARGE, FileSearchData.FileSize.XL));

        FileSearchFiltering.FileFilter kw_alphaBeta = new FileSearchFiltering.KeywordListFilter(Arrays.asList("Alpha", "Beta"));
        FileSearchFiltering.FileFilter kw_alpha = new FileSearchFiltering.KeywordListFilter(Arrays.asList("Alpha"));
        
        FileSearchFiltering.FileFilter freq_uniqueRare = new FileSearchFiltering.FrequencyFilter(Arrays.asList(FileSearchData.Frequency.UNIQUE, FileSearchData.Frequency.RARE));
        
        FileSearchFiltering.FileFilter path_II = new FileSearchFiltering.ParentFilter(Arrays.asList(new FileSearchFiltering.ParentSearchTerm("II", false)));
        FileSearchFiltering.FileFilter path_IIfolderA = new FileSearchFiltering.ParentFilter(Arrays.asList(new FileSearchFiltering.ParentSearchTerm("II", false), 
                new FileSearchFiltering.ParentSearchTerm("/Rare in CR/Folder A/", true)));
        
        FileSearchFiltering.FileFilter type_video = new FileSearchFiltering.FileTypeFilter(Arrays.asList(FileSearchData.FileType.VIDEO));
        FileSearchFiltering.FileFilter type_imageAudio = new FileSearchFiltering.FileTypeFilter(Arrays.asList(FileSearchData.FileType.IMAGE, 
                FileSearchData.FileType.AUDIO));
        FileSearchFiltering.FileFilter type_image = new FileSearchFiltering.FileTypeFilter(Arrays.asList(FileSearchData.FileType.IMAGE));
        FileSearchFiltering.FileFilter type_doc = new FileSearchFiltering.FileTypeFilter(Arrays.asList(FileSearchData.FileType.DOCUMENTS));
 
        FileSearchFiltering.FileFilter ds_46 = null;
        try {
            DataSource ds = Case.getCurrentCase().getSleuthkitCase().getDataSource(46);
            ds_46 = new FileSearchFiltering.DataSourceFilter(Arrays.asList(ds));
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
        
        
        //////////////////////
        // Set up test
        
        // Select test filters
        List<FileSearchFiltering.FileFilter> filters = new ArrayList<>();
        filters.add(size_medSmallXS);
        //filters.add(kw_alpha);
        filters.add(freq_uniqueRare);
        //filters.add(path_IIfolderA);
        
        // Choose grouping attribute
        //FileSearch.AttributeType groupingAttr = new FileSearch.FileTypeAttribute();
        //FileSearch.AttributeType groupingAttr = new FileSearch.DefaultAttribute();
        FileSearch.AttributeType groupingAttr = new FileSearch.KeywordListAttribute();
        //FileSearch.AttributeType groupingAttr = new FileSearch.FrequencyAttribute();
        //FileSearch.AttributeType groupingAttr = new FileSearch.ParentPathAttribute();
        //FileSearch.AttributeType groupingAttr = new FileSearch.NoGroupingAttribute();
        
        // Choose group sorting method
        FileGroup.GroupSortingAlgorithm groupSortAlgorithm = FileGroup.GroupSortingAlgorithm.BY_GROUP_KEY;
        //FileGroup.GroupSortingAlgorithm groupSortAlgorithm = FileGroup.GroupSortingAlgorithm.BY_GROUP_SIZE;
        
        // Choose file sorting method
        FileSorter.SortingMethod fileSort = FileSorter.SortingMethod.BY_FILE_SIZE;
        


        try {
            
            // Make a list of attributes that we want to add values for. Eventually this can be based on user input but
            // for now just add all of them.
            List<FileSearch.AttributeType> attributesNeededForSorting = Arrays.asList(new FileSearch.DataSourceAttribute(),
                    new FileSearch.FileSizeAttribute(), new FileSearch.FileTypeAttribute(), new FileSearch.FrequencyAttribute(),
                    new FileSearch.KeywordListAttribute(), new FileSearch.ParentPathAttribute());
            
            FileSearch.runFileSearch(filters, 
                groupingAttr, 
                groupSortAlgorithm, 
                fileSort, 
                attributesNeededForSorting,
                Case.getCurrentCase().getSleuthkitCase(), crDb);
            
            

        } catch (Exception ex) {
            ex.printStackTrace();
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
