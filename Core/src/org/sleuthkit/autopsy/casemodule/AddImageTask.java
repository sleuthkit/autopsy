/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2016 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitJNI;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

/*
 * A background task that adds the given image to database using the Sleuthkit
 * JNI interface.
 *
 * It updates the given ProgressMonitor as it works through adding the image,
 * and et the end, calls the specified Callback.
 */
class AddImageTask implements Runnable {

    private final Logger logger = Logger.getLogger(AddImageTask.class.getName());

    private final Case currentCase;

    // true if the process was requested to cancel
    private final Object lock = new Object();   // synchronization object for cancelRequested
    private volatile boolean cancelRequested = false;

    //true if revert has been invoked.
    private boolean reverted = false;

    // true if there was a critical error in adding the data source
    private boolean hasCritError = false;

    private final List<String> errorList = new ArrayList<>();

    private final DataSourceProcessorProgressMonitor progressMonitor;
    private final DataSourceProcessorCallback callbackObj;

    private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>());

    private SleuthkitJNI.CaseDbHandle.AddImageProcess addImageProcess;
    private Thread dirFetcher;

    private final String imagePath;
    String timeZone;
    boolean noFatOrphans;

    private final String dataSourceId;

    /*
     * A thread that updates the progressMonitor with the name of the directory
     * currently being processed by the AddImageTask
     */
    private class CurrentDirectoryFetcher implements Runnable {

        DataSourceProcessorProgressMonitor progressMonitor;
        SleuthkitJNI.CaseDbHandle.AddImageProcess process;

        CurrentDirectoryFetcher(DataSourceProcessorProgressMonitor aProgressMonitor, SleuthkitJNI.CaseDbHandle.AddImageProcess proc) {
            this.progressMonitor = aProgressMonitor;
            this.process = proc;
        }

        /**
         * @return the currently processing directory
         */
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    String currDir = process.currentDirectory();
                    if (currDir != null) {
                        if (!currDir.isEmpty()) {
                            progressMonitor.setProgressText(
                                    NbBundle.getMessage(this.getClass(), "AddImageTask.run.progress.adding",
                                            currDir));
                        }
                    }
                    // this sleep here prevents the UI from locking up 
                    // due to too frequent updates to the progressMonitor above
                    Thread.sleep(500);
                }
            } catch (InterruptedException ie) {
                // nothing to do, thread was interrupted externally  
                // signaling the end of AddImageProcess 
            }
        }
    }

    /**
     * Constructs a runnable task that adds an image to the case database.
     *
     * @param dataSourceId         An ASCII-printable identifier for the data
     *                             source that is intended to be unique across
     *                             multiple cases (e.g., a UUID).
     * @param imagePath            Path to the image file.
     * @param timeZone             The time zone to use when processing dates
     *                             and times for the image, obtained from
     *                             java.util.TimeZone.getID.
     * @param ignoreFatOrphanFiles Whether to parse orphans if the image has a
     *                             FAT filesystem.
     * @param monitor              Progress monitor to report progress during
     *                             processing.
     * @param cbObj                Callback to call when processing is done.
     */
    AddImageTask(String dataSourceId, String imagePath, String timeZone, boolean ignoreFatOrphanFiles, DataSourceProcessorProgressMonitor monitor, DataSourceProcessorCallback cbObj) {
        currentCase = Case.getCurrentCase();
        this.dataSourceId = dataSourceId;
        this.imagePath = imagePath;
        this.timeZone = timeZone;
        this.noFatOrphans = ignoreFatOrphanFiles;
        this.callbackObj = cbObj;
        this.progressMonitor = monitor;
    }

    /**
     * Starts the addImage process, but does not commit the results.
     *
     * @return
     *
     * @throws Exception
     */
    @Override
    public void run() {
        errorList.clear();
        try {
            currentCase.getSleuthkitCase().acquireExclusiveLock();
            addImageProcess = currentCase.makeAddImageProcess(timeZone, true, noFatOrphans);
            dirFetcher = new Thread(new CurrentDirectoryFetcher(progressMonitor, addImageProcess));
            try {
                progressMonitor.setIndeterminate(true);
                progressMonitor.setProgress(0);
                dirFetcher.start();
                addImageProcess.run(dataSourceId, new String[]{imagePath});
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Core errors occurred while running add image on " + imagePath, ex); //NON-NLS
                hasCritError = true;
                errorList.add(ex.getMessage());
            } catch (TskDataException ex) {
                logger.log(Level.WARNING, "Data errors occurred while running add image " + imagePath, ex); //NON-NLS
                errorList.add(ex.getMessage());
            }
            postProcess();
        } finally {
            currentCase.getSleuthkitCase().releaseExclusiveLock();
        }
    }

    /**
     * Commit the newly added image to DB
     *
     *
     * @throws Exception if commit or adding the image to the case failed
     */
    private void commitImage() throws Exception {

        long imageId = 0;
        try {
            imageId = addImageProcess.commit();
        } catch (TskCoreException e) {
            logger.log(Level.WARNING, "Errors occurred while committing the image " + imagePath, e); //NON-NLS
            errorList.add(e.getMessage());
        } finally {
            if (imageId != 0) {
                // get the newly added Image so we can return to caller
                Image newImage = currentCase.getSleuthkitCase().getImageById(imageId);

                //while we have the image, verify the size of its contents
                String verificationErrors = newImage.verifyImageSize();
                if (verificationErrors.equals("") == false) {
                    //data error (non-critical)
                    errorList.add(verificationErrors);
                }

                // Add the image to the list of new content
                newContents.add(newImage);
            }

            logger.log(Level.INFO, "Image committed, imageId: {0}", imageId); //NON-NLS
            logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());
        }
    }

    /**
     * Post processing after the addImageProcess is done.
     *
     */
    private void postProcess() {

        // cancel the directory fetcher
        dirFetcher.interrupt();

        if (cancelRequested() || hasCritError) {
            logger.log(Level.WARNING, "Critical errors or interruption in add image process on {0}. Image will not be committed.", imagePath); //NON-NLS
            revert();
        }

        if (!errorList.isEmpty()) {
            logger.log(Level.INFO, "There were errors that occurred in add image process for {0}", imagePath); //NON-NLS
        }

        // When everything happens without an error:
        if (!(cancelRequested() || hasCritError)) {
            try {
                if (addImageProcess != null) {
                    // commit image
                    try {
                        commitImage();
                    } catch (Exception ex) {
                        errorList.add(ex.getMessage());
                        // Log error/display warning
                        logger.log(Level.SEVERE, "Error adding image " + imagePath + " to case.", ex); //NON-NLS
                    }
                } else {
                    logger.log(Level.SEVERE, "Missing image process object"); //NON-NLS
                }

                // Tell the progress monitor we're done
                progressMonitor.setProgress(100);
            } catch (Exception ex) {
                //handle unchecked exceptions post image add
                errorList.add(ex.getMessage());

                logger.log(Level.WARNING, "Unexpected errors occurred while running post add image cleanup for " + imagePath, ex); //NON-NLS
                logger.log(Level.SEVERE, "Error adding image " + imagePath + " to case", ex); //NON-NLS
            }
        }

        // invoke the callBack, unless the caller cancelled 
        if (!cancelRequested()) {
            doCallBack();
        }
    }

    /*
     * Call the callback with results, new content, and errors, if any
     */
    private void doCallBack() {
        DataSourceProcessorCallback.DataSourceProcessorResult result;

        if (hasCritError) {
            result = DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS;
        } else if (!errorList.isEmpty()) {
            result = DataSourceProcessorCallback.DataSourceProcessorResult.NONCRITICAL_ERRORS;
        } else {
            result = DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS;
        }

        // invoke the callback, passing it the result, list of new contents, and list of errors
        callbackObj.done(result, errorList, newContents);
    }

    /*
     * cancel the image addition, if possible
     */
    public void cancelTask() {

        synchronized (lock) {
            cancelRequested = true;
            try {
                interrupt();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Failed to interrupt the add image task...");     //NON-NLS
            }
        }
    }

    /*
     * Interrupt the add image process if it is still running
     */
    private void interrupt() throws Exception {

        try {
            logger.log(Level.INFO, "interrupt() add image process"); //NON-NLS
            addImageProcess.stop();  //it might take time to truly stop processing and writing to db
        } catch (TskCoreException ex) {
            throw new Exception(NbBundle.getMessage(this.getClass(), "AddImageTask.interrupt.exception.msg"), ex);
        }
    }

    /*
     * Revert - if image has already been added but not committed yet
     */
    private void revert() {

        if (!reverted) {
            logger.log(Level.INFO, "Revert after add image process"); //NON-NLS
            try {
                addImageProcess.revert();
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Error reverting add image process", ex); //NON-NLS
            }
            reverted = true;
        }
    }

    private boolean cancelRequested() {
        synchronized (lock) {
            return cancelRequested;
        }
    }
}
