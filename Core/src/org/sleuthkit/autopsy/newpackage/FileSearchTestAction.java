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
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;

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
        
        List<FileSearchFiltering.SubFilter> filters = new ArrayList<>();
        filters.add( new FileSearchFiltering.SizeSubFilter(Arrays.asList(FileSearchData.FileSize.MEDIUM, FileSearchData.FileSize.SMALL)));
        filters.add( new FileSearchFiltering.FrequencySubFilter(Arrays.asList(FileSearchData.Frequency.UNIQUE)));
        EamDb crDb = null;
        if (EamDb.isEnabled()) {
            try {
                crDb = EamDb.getInstance();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        
        try {
            List<ResultFile> results = FileSearchFiltering.runFilters(filters, 
                    Case.getCurrentCase().getSleuthkitCase(), crDb);
            
            int count = 0;
            for (ResultFile file: results) {
                file.print();
                count++;
                if (count > 10) {
                    int left = results.size() - 10;
                    System.out.println(" Not displaying " + left + " more files");
                    break;
                }
            }
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
