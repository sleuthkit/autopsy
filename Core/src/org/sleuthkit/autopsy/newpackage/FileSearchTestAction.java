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
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.InvalidPathException;
import java.util.logging.Level;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import javax.swing.JOptionPane;
import java.awt.Frame;
import javax.swing.SwingWorker;
import org.apache.commons.io.FileUtils;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.utils.FileTypeUtils;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.datamodel.SleuthkitCase;
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
        System.out.println("\n#########################\nTesting file search!!!");
        
        // Set up all the test filters
        FileSearchFiltering.SubFilter size_medSmallXS = new FileSearchFiltering.SizeSubFilter(Arrays.asList(FileSearchData.FileSize.MEDIUM, FileSearchData.FileSize.SMALL, FileSearchData.FileSize.XS));
        FileSearchFiltering.SubFilter size_XL = new FileSearchFiltering.SizeSubFilter(Arrays.asList(FileSearchData.FileSize.XL));
        FileSearchFiltering.SubFilter size_largeXL = new FileSearchFiltering.SizeSubFilter(Arrays.asList(FileSearchData.FileSize.LARGE, FileSearchData.FileSize.XL));
        FileSearchFiltering.SubFilter size_medLargeXL = new FileSearchFiltering.SizeSubFilter(Arrays.asList(FileSearchData.FileSize.MEDIUM, FileSearchData.FileSize.LARGE, FileSearchData.FileSize.XL));

        FileSearchFiltering.SubFilter kw_alphaBeta = new FileSearchFiltering.KeywordListSubFilter(Arrays.asList("Alpha", "Beta"));
        FileSearchFiltering.SubFilter kw_alpha = new FileSearchFiltering.KeywordListSubFilter(Arrays.asList("Alpha"));
        
        FileSearchFiltering.SubFilter freq_uniqueRare = new FileSearchFiltering.FrequencySubFilter(Arrays.asList(FileSearchData.Frequency.UNIQUE, FileSearchData.Frequency.RARE));
        
        FileSearchFiltering.SubFilter path_II = new FileSearchFiltering.ParentSubFilter(Arrays.asList(new FileSearchFiltering.ParentSearchTerm("II", false)));
        FileSearchFiltering.SubFilter path_IIfolderA = new FileSearchFiltering.ParentSubFilter(Arrays.asList(new FileSearchFiltering.ParentSearchTerm("II", false), 
                new FileSearchFiltering.ParentSearchTerm("/Rare in CR/Folder A/", true)));
        
        FileSearchFiltering.SubFilter type_video = new FileSearchFiltering.FileTypeSubFilter(Arrays.asList(FileTypeUtils.FileTypeCategory.VIDEO));
        FileSearchFiltering.SubFilter type_imageAudio = new FileSearchFiltering.FileTypeSubFilter(Arrays.asList(FileTypeUtils.FileTypeCategory.IMAGE, 
                FileTypeUtils.FileTypeCategory.AUDIO));
        FileSearchFiltering.SubFilter type_image = new FileSearchFiltering.FileTypeSubFilter(Arrays.asList(FileTypeUtils.FileTypeCategory.IMAGE));
        FileSearchFiltering.SubFilter type_doc = new FileSearchFiltering.FileTypeSubFilter(Arrays.asList(FileTypeUtils.FileTypeCategory.DOCUMENTS));
 
        FileSearchFiltering.SubFilter ds_46 = null;
        try {
            DataSource ds = Case.getCurrentCase().getSleuthkitCase().getDataSource(46);
            ds_46 = new FileSearchFiltering.DataSourceSubFilter(Arrays.asList(ds));
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }
        
        //////////////////////
        // Set up test
        
        // Select test filters
        List<FileSearchFiltering.SubFilter> filters = new ArrayList<>();
        filters.add(size_medSmallXS);
        //filters.add(kw_alpha);
        filters.add(freq_uniqueRare);
        
        // Choose grouping attribute
        //FileSearch.AttributeType groupingAttr = new FileSearch.FileTypeAttribute();
        //FileSearch.AttributeType groupingAttr = new FileSearch.DefaultAttribute();
        FileSearch.AttributeType groupingAttr = new FileSearch.KeywordListAttribute();
        //FileSearch.AttributeType groupingAttr = new FileSearch.FrequencyAttribute();
        //FileSearch.AttributeType groupingAttr = new FileSearch.ParentPathAttribute();
        
        // Choose group sorting method
        //FileGroup.GroupSortingAlgorithm groupSortAlgorithm = FileGroup.GroupSortingAlgorithm.BY_ATTRIBUTE;
        FileGroup.GroupSortingAlgorithm groupSortAlgorithm = FileGroup.GroupSortingAlgorithm.BY_GROUP_SIZE;
        
        // Choose file sorting method
        Comparator<ResultFile> fileSort = FileSearch.getFileNameComparator();
        //Comparator<ResultFile> fileSort = new FileSearch.FrequencyAttribute().getDefaultFileComparator();
        //Comparator<ResultFile> fileSort = new FileSearch.FileSizeAttribute().getDefaultFileComparator();
        //Comparator<ResultFile> fileSort = new FileSearch.ParentPathAttribute().getDefaultFileComparator();
        //Comparator<ResultFile> fileSort = new FileSearch.FileTypeAttribute().getDefaultFileComparator();
        
        EamDb crDb = null;
        if (EamDb.isEnabled()) {
            try {
                crDb = EamDb.getInstance();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

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
