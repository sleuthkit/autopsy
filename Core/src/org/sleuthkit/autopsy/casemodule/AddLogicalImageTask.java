/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagewriter.ImageWriterSettings;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

public class AddLogicalImageTask extends AddImageTask {

    private final static Logger logger = Logger.getLogger(AddLogicalImageTask.class.getName());   
    private final static String ALERT_TXT = "alert.txt"; //NON-NLS
    private final static String USERS_TXT = "users.txt"; //NON-NLS
    private final File src;
    private final File dest;
    private final DataSourceProcessorCallback callback;
    private final DataSourceProcessorProgressMonitor progressMonitor;
    
    public AddLogicalImageTask(String deviceId, String imagePath, int sectorSize, 
            String timeZone, boolean ignoreFatOrphanFiles, 
            String md5, String sha1, String sha256, 
            ImageWriterSettings imageWriterSettings, 
            File src, File dest,
            DataSourceProcessorProgressMonitor progressMonitor, 
            DataSourceProcessorCallback callback
    ) {
        super(deviceId, imagePath, sectorSize, timeZone, ignoreFatOrphanFiles, 
                md5, sha1, sha256, imageWriterSettings, progressMonitor, callback);
        this.src = src;
        this.dest = dest;
        this.progressMonitor = progressMonitor;
        this.callback = callback;
    }
    
    /**
     * Copy the src directory to dest.
     * Add alert.txt and users.txt to the case report
     * Adds the image to the case database.
     */
    @Messages({
        "# {0} - src", "# {1} - dest", "AddLogicalImageTask.copyingImageFromTo=Copying image from {0} to {1}",
        "AddLogicalImageTask.doneCopying=Done copying",
        "# {0} - src", "# {1} - dest", "AddLogicalImageTask.failedToCopyDirectory=Failed to copy directory {0} to {1}",
        "# {0} - file", "AddLogicalImageTask.addingToReport=Adding {0} to report",
        "# {0} - file", "AddLogicalImageTask.doneAddingToReport=Done adding {0} to report"
    })
    @Override
    public void run() {
        List<String> errorList = new ArrayList<>();
        List<Content> emptyDataSources = new ArrayList<>();
        
        try {
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_copyingImageFromTo(src.toString(), dest.toString()));
            FileUtils.copyDirectory(src, dest);
            progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneCopying());
        } catch (IOException ex) {
            // Copy directory failed
            String msg = Bundle.AddLogicalImageTask_failedToCopyDirectory(src.toString(), dest.toString());
            errorList.add(msg);
            logger.log(Level.SEVERE, String.format("Failed to copy directory {0} to {1}", src.toString(), dest.toString()));
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }
        
        // Add the alert.txt and users.txt to the case report
        progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingToReport(ALERT_TXT));
        String status = addReport(Paths.get(dest.toString(), ALERT_TXT), ALERT_TXT + " " + src.getName());
        if (status != null) {
            errorList.add(status);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }
        progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneAddingToReport(ALERT_TXT));

        progressMonitor.setProgressText(Bundle.AddLogicalImageTask_addingToReport(USERS_TXT));
        status = addReport(Paths.get(dest.toString(), USERS_TXT), USERS_TXT + " " + src.getName());
        if (status != null) {
            errorList.add(status);
            callback.done(DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS, errorList, emptyDataSources);
            return;
        }
        progressMonitor.setProgressText(Bundle.AddLogicalImageTask_doneAddingToReport(USERS_TXT));
        
        super.run();
    }
    
    /** 
     * Add a file specified by the reportPath to the case report.
     * 
     * @param reportPath Path to the report to be added
     * @param reportName Name associated the report
     * @returns null if success, or exception message if failure
     *
     */
    @Messages({
        "# {0} - file", "# {1} - exception message", "AddLogicalImageTask.failedToAddReport=Failed to add report {0}. Reason= {1}"
    })
    private String addReport(Path reportPath, String reportName) {
        if (!reportPath.toFile().exists()) {
            return null; // if the reportPath doesn't exist, just ignore it.
        }
        try {
            Case.getCurrentCase().addReport(reportPath.toString(), "LogicalImager", reportName); //NON-NLS
            return null;
        } catch (TskCoreException ex) {
            String msg = Bundle.AddLogicalImageTask_failedToAddReport(reportPath.toString(), ex.getMessage());
            logger.log(Level.SEVERE, String.format("Failed to add report {0}. Reason= {1}", reportPath.toString(), ex.getMessage()));
            return msg;
        }        
    }
}
