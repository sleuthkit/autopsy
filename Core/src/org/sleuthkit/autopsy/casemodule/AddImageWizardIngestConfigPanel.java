/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.sleuthkit.autopsy.ingest.IngestConfigurator;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.ContentTypePanel.ContentType;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * second panel of add image wizard, allows user to configure ingest modules.
 *
 * TODO: review this for dead code. think about moving logic of adding image to
 * 3rd panel( {@link  AddImageWizardAddingProgressPanel}) separate class -jm
 */
class AddImageWizardIngestConfigPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    private static final Logger logger = Logger.getLogger(AddImageWizardIngestConfigPanel.class.getName());
    private IngestConfigurator ingestConfig;
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private Component component = null;
    private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>());
    private boolean ingested = false;
    private boolean readyToIngest = false;
    // the paths of the image files to be added
    private String dataSourcePath;
    private ContentType dataSourceType;
    // the time zone where the image is added
    private String timeZone;
    //whether to not process FAT filesystem orphans
    private boolean noFatOrphans;
    // task that will clean up the created database file if the wizard is cancelled before it finishes
    private AddImageAction.CleanupTask cleanupImage; // initialized to null in readSettings()
    private CurrentDirectoryFetcher fetcher;
    private AddImageProcess process;
    private AddImageAction action;
    private AddImageTask addImageTask;
    private AddLocalFilesTask addLocalFilesTask;
    private AddImageWizardAddingProgressPanel progressPanel;

    AddImageWizardIngestConfigPanel(AddImageAction action, AddImageWizardAddingProgressPanel proPanel) {
        this.action = action;
        this.progressPanel = proPanel;
        ingestConfig = Lookup.getDefault().lookup(IngestConfigurator.class);
        List<String> messages = ingestConfig.setContext(AddImageWizardIngestConfigPanel.class.getCanonicalName());
        if (messages.isEmpty() == false) {
            StringBuilder warning = new StringBuilder();
            for (String message : messages) {
                warning.append(message).append("\n");
            }
            JOptionPane.showMessageDialog(null, warning.toString());
        }
    }

    /**
     * Get the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * @return component the UI component of this wizard panel
     */
    @Override
    public Component getComponent() {
        if (component == null) {
            component = new AddImageWizardIngestConfigVisual(ingestConfig.getIngestConfigPanel());
        }
        return component;
    }

    /**
     * Help for this panel. When the panel is active, this is used as the help
     * for the wizard dialog.
     *
     * @return HelpCtx.DEFAULT_HELP the help for this panel
     */
    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
        // If you have context help:
        // return new HelpCtx(SampleWizardPanel1.class);
    }

    /**
     * Tests whether the panel is finished. If the panel is valid, the "Finish"
     * button will be enabled.
     *
     * @return true the finish button should be always enabled at this point
     */
    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return true;
        // If it depends on some condition (form filled out...), then:
        // return someCondition();
        // and when this condition changes (last form field filled in...) then:
        // fireChangeEvent();
        // and uncomment the complicated stuff below.
    }

    /**
     * Adds a listener to changes of the panel's validity.
     *
     * @param l the change listener to add
     */
    @Override
    public final void addChangeListener(ChangeListener l) {
    }

    /**
     * Removes a listener to changes of the panel's validity.
     *
     * @param l the change listener to move
     */
    @Override
    public final void removeChangeListener(ChangeListener l) {
    }

    // You can use a settings object to keep track of state. Normally the
    // settings object will be the WizardDescriptor, so you can use
    // WizardDescriptor.getProperty & putProperty to store information entered
    // by the user.
    /**
     * Provides the wizard panel with the current data--either the default data
     * or already-modified settings, if the user used the previous and/or next
     * buttons. This method can be called multiple times on one instance of
     * WizardDescriptor.Panel.
     *
     * @param settings the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        JButton cancel = new JButton("Cancel");
        cancel.setEnabled(false);
        settings.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, cancel});
        cleanupImage = null;
        readyToIngest = false;

        newContents.clear();
        dataSourcePath = (String) settings.getProperty(AddImageAction.DATASOURCEPATH_PROP);
        dataSourceType = (ContentType) settings.getProperty(AddImageAction.DATASOURCETYPE_PROP);
        timeZone = settings.getProperty(AddImageAction.TIMEZONE_PROP).toString();
        noFatOrphans = ((Boolean) settings.getProperty(AddImageAction.NOFATORPHANS_PROP)).booleanValue();

        //start the process of adding the content
        if (dataSourceType.equals(ContentType.LOCAL)) {
            addLocalFilesTask = new AddLocalFilesTask(settings);
            addLocalFilesTask.execute();
        } else {
            //disk or image
            addImageTask = new AddImageTask(settings);
            addImageTask.execute();
        }
    }

    /**
     * Provides the wizard panel with the opportunity to update the settings
     * with its current customized state. Rather than updating its settings with
     * every change in the GUI, it should collect them, and then only save them
     * when requested to by this method. This method can be called multiple
     * times on one instance of WizardDescriptor.Panel.
     *
     * @param settings the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
        //save previously selected config
        ingestConfig.save();
        // Start ingest if it hasn't already been started
        readyToIngest = true;
        startIngest();
    }

    /**
     * Start ingest after verifying we have a new image, we are ready to ingest,
     * and we haven't already ingested.
     */
    private void startIngest() {
        if (!newContents.isEmpty() && readyToIngest && !ingested) {
            ingested = true;
            ingestConfig.setContent(newContents);
            ingestConfig.start();
            progressPanel.setStateFinished();

        }
    }

    /**
     * Class for getting the currently processing directory.
     *
     */
    private static class CurrentDirectoryFetcher extends SwingWorker<Integer, Integer> {

        AddImageTask task;
        JProgressBar prog;
        AddImageWizardAddingProgressVisual wiz;
        AddImageProcess proc;

        CurrentDirectoryFetcher(JProgressBar prog, AddImageWizardAddingProgressVisual wiz, AddImageProcess proc) {
            this.wiz = wiz;
            this.proc = proc;
            this.prog = prog;
        }

        /**
         * @return the currently processing directory
         */
        @Override
        protected Integer doInBackground() {
            try {
                while (prog.getValue() < 100 || prog.isIndeterminate()) { //TODO Rely on state variable in AddImgTask class

                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            wiz.setCurrentDirText(proc.currentDirectory());
                        }
                    });

                    Thread.sleep(2 * 1000);
                }
                return 1;
            } catch (InterruptedException ie) {
                return -1;
            }
        }
    }

    /**
     * Thread that will add logical files to database, and then kick-off ingest
     * modules. Note: the add logical files task cannot currently be reverted as
     * the add image task can. This is a separate task from AddImgTask because
     * it is much simpler and does not require locks, since the underlying file
     * manager methods acquire the locks for each transaction when adding
     * logical files.
     */
    private class AddLocalFilesTask extends SwingWorker<Integer, Integer> {

        private JProgressBar progressBar;
        private Case currentCase;
        // true if the process was requested to stop
        private boolean interrupted = false;
        private boolean hasCritError = false;
        private String errorString = null;
        private WizardDescriptor settings;
        private Logger logger = Logger.getLogger(AddLocalFilesTask.class.getName());

        protected AddLocalFilesTask(WizardDescriptor settings) {
            this.progressBar = progressPanel.getComponent().getProgressBar();
            currentCase = Case.getCurrentCase();
            this.settings = settings;
        }

        /**
         * Starts the addImage process, but does not commit the results.
         *
         * @return
         *
         * @throws Exception
         */
        @Override
        protected Integer doInBackground() {
            this.setProgress(0);
            // Add a cleanup task to interupt the backgroud process if the
            // wizard exits while the background process is running.
            AddImageAction.CleanupTask cancelledWhileRunning = action.new CleanupTask() {
                @Override
                void cleanup() throws Exception {
                    logger.log(Level.INFO, "Add logical files process interrupted.");
                    //nothing to be cleanedup
                }
            };

            cancelledWhileRunning.enable();
            final LocalFilesAddProgressUpdater progUpdater = new LocalFilesAddProgressUpdater(this.progressBar, progressPanel.getComponent());
            try {
                final FileManager fileManager = currentCase.getServices().getFileManager();
                progressPanel.setStateStarted();
                String[] paths = dataSourcePath.split(LocalFilesPanel.FILES_SEP);
                List<String> absLocalPaths = new ArrayList<String>();
                for (String path : paths) {
                    absLocalPaths.add(path);
                }
                newContents.add(fileManager.addLocalFilesDirs(absLocalPaths, progUpdater));
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Errors occurred while running add logical files. ", ex);
                hasCritError = true;
                errorString = ex.getMessage();
            } finally {
                // process is over, doesn't need to be dealt with if cancel happens
                cancelledWhileRunning.disable();
                //enqueue what would be in done() to EDT thread
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        postProcess();
                    }
                });
            }
            return 0;
        }

        /**
         *
         * (called by EventDispatch Thread after doInBackground finishes)
         */
        protected void postProcess() {
            progressBar.setIndeterminate(false);
            setProgress(100);

            //clear updates
            //     progressPanel.getComponent().setProcessInvis();

            if (interrupted || hasCritError) {
                logger.log(Level.INFO, "Handling errors or interruption that occured in logical files process");
                if (hasCritError) {
                    //core error
                    progressPanel.getComponent().showErrors(errorString, true);
                }
                return;
            } else {
                if (errorString != null) {
                    //data error (non-critical)
                    logger.log(Level.INFO, "Handling non-critical errors that occured in logical files process");
                    progressPanel.getComponent().showErrors(errorString, false);
                }
            }
            try {
                // When everything happens without an error:
                if (errorString == null) { // complete progress bar
                    progressPanel.getComponent().setProgressBarTextAndColor("*Logical Files added.", 100, Color.black);
                }

                // Get attention for the process finish
                java.awt.Toolkit.getDefaultToolkit().beep(); //BEEP!
                AddImageWizardAddingProgressVisual panel = progressPanel.getComponent();
                if (panel != null) {
                    Window w = SwingUtilities.getWindowAncestor(panel);
                    if (w != null) {
                        w.toFront();
                    }
                }

                progressPanel.setStateFinished();

                //notify the case
                if (!newContents.isEmpty()) {
                    Case.getCurrentCase().addLocalDataSource(newContents.get(0));
                }

                // Start ingest if we can
                startIngest();

            } catch (Exception ex) {
                //handle unchecked exceptions
                logger.log(Level.WARNING, "Unexpected errors occurred while running post add image cleanup. ", ex);
                progressPanel.getComponent().setProgressBarTextAndColor("*Failed to add image.", 0, Color.black); // set error message
                logger.log(Level.SEVERE, "Error adding image to case", ex);
            }
        }

        /**
         * Updates the wizard status with logical file/folder
         */
        private class LocalFilesAddProgressUpdater implements FileManager.FileAddProgressUpdater {

            private int count = 0;
            private JProgressBar prog;
            private AddImageWizardAddingProgressVisual wiz;

            LocalFilesAddProgressUpdater(JProgressBar prog, AddImageWizardAddingProgressVisual wiz) {
                this.wiz = wiz;
                this.prog = prog;
            }

            @Override
            public void fileAdded(final AbstractFile newFile) {
                if (count++ % 10 == 0 && (prog.getValue() < 100 || prog.isIndeterminate())) {
                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            wiz.setCurrentDirText(newFile.getParentPath() + "/" + newFile.getName());
                        }
                    });

                }
            }
        }
    }

    /**
     * Thread that will make the JNI call to add image to database, and then
     * kick-off ingest modules.
     */
    private class AddImageTask extends SwingWorker<Integer, Integer> {

        private JProgressBar progressBar;
        private Case currentCase;
        // true if the process was requested to stop
        private boolean interrupted = false;
        private boolean hasCritError = false;
        private String errorString = null;
        private WizardDescriptor wizDescriptor;
        private Logger logger = Logger.getLogger(AddImageTask.class.getName());

        protected AddImageTask(WizardDescriptor settings) {
            this.progressBar = progressPanel.getComponent().getProgressBar();
            currentCase = Case.getCurrentCase();
            this.wizDescriptor = settings;
        }

        /**
         * Starts the addImage process, but does not commit the results.
         *
         * @return
         *
         * @throws Exception
         */
        @Override
        protected Integer doInBackground() {

            this.setProgress(0);


            // Add a cleanup task to interupt the backgroud process if the
            // wizard exits while the background process is running.
            AddImageAction.CleanupTask cancelledWhileRunning = action.new CleanupTask() {
                @Override
                void cleanup() throws Exception {
                    logger.log(Level.INFO, "Add image process interrupted.");
                    addImageTask.interrupt(); //it might take time to truly interrupt
                }
            };


            try {
                //lock DB for writes in EWT thread
                //wait until lock acquired in EWT
                EventQueue.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        SleuthkitCase.dbWriteLock();
                    }
                });
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "Errors occurred while running add image, could not acquire lock. ", ex);
                return 0;

            } catch (InvocationTargetException ex) {
                logger.log(Level.WARNING, "Errors occurred while running add image, could not acquire lock. ", ex);
                return 0;
            }

            process = currentCase.makeAddImageProcess(timeZone, true, noFatOrphans);
            fetcher = new CurrentDirectoryFetcher(this.progressBar, progressPanel.getComponent(), process);
            cancelledWhileRunning.enable();
            try {
                progressPanel.setStateStarted();
                fetcher.execute();
                process.run(new String[]{dataSourcePath});
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Core errors occurred while running add image. ", ex);
                //critical core/system error and process needs to be interrupted
                hasCritError = true;
                errorString = ex.getMessage();
            } catch (TskDataException ex) {
                logger.log(Level.WARNING, "Data errors occurred while running add image. ", ex);
                errorString = ex.getMessage();
            } finally {
                // process is over, doesn't need to be dealt with if cancel happens
                cancelledWhileRunning.disable();

//                //enqueue what would be in done() to EDT thread
//                EventQueue.invokeLater(new Runnable() {
//                    @Override
//                    public void run() {
//                        postProcessImage();
//                    }
//                });

                /////////////////Done() is already executed in EDT per SwingWorker javadocs -jm
            }

            return 0;
        }

        /**
         * Commit the finished AddImageProcess, and cancel the CleanupTask that
         * would have reverted it.
         *
         * @param settings property set to get AddImageProcess and CleanupTask
         *                 from
         *
         * @throws Exception if commit or adding the image to the case failed
         */
        private void commitImage(WizardDescriptor settings) throws Exception {

            String contentPath = (String) settings.getProperty(AddImageAction.DATASOURCEPATH_PROP);

            String timezone = settings.getProperty(AddImageAction.TIMEZONE_PROP).toString();
            settings.putProperty(AddImageAction.IMAGEID_PROP, "");

            long imageId = 0;
            try {
                imageId = process.commit();
            } catch (TskException e) {
                logger.log(Level.WARNING, "Errors occured while committing the image", e);
            } finally {
                //commit done, unlock db write in EWT thread
                //before doing anything else
                SleuthkitCase.dbWriteUnlock();

                if (imageId != 0) {
                    Image newImage = Case.getCurrentCase().addImage(contentPath, imageId, timezone);

                    newImage.getSsize();
                    String verificationErrors = newImage.verifyImageSize();
                    if (verificationErrors.equals("") == false) {
                        //data error (non-critical)
                        // @@@ Aren't we potentially overwriting existing errors...
                        progressPanel.setErrors(verificationErrors, false);
                    }


                    newContents.add(newImage);
                    settings.putProperty(AddImageAction.IMAGEID_PROP, imageId);
                }

                // Can't bail and revert image add after commit, so disable image cleanup
                // task
                cleanupImage.disable();
                settings.putProperty(AddImageAction.IMAGECLEANUPTASK_PROP, null);

                logger.log(Level.INFO, "Image committed, imageId: " + imageId);
                logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());

            }
        }

        /**
         *
         * (called by EventDispatch Thread after doInBackground finishes)
         */
        @Override
        protected void done() {
            //these are required to stop the CurrentDirectoryFetcher
            progressBar.setIndeterminate(false);
            setProgress(100);

            // attempt actions that might fail and force the process to stop

            if (interrupted || hasCritError) {
                logger.log(Level.INFO, "Handling errors or interruption that occured in add image process");
                revert();
                if (hasCritError) {
                    //core error
                    progressPanel.setErrors(errorString, true);
                }
                return;
            }
            if (errorString != null) {
                //data error (non-critical)
                logger.log(Level.INFO, "Handling non-critical errors that occured in add image process");
                progressPanel.setErrors(errorString, false);
            }

            errorString = null;



            try {
                // When everything happens without an error:

                // the add-image process needs to be reverted if the wizard doesn't finish
                cleanupImage = action.new CleanupTask() {
                    //note, CleanupTask runs inside EWT thread
                    @Override
                    void cleanup() throws Exception {
                        logger.log(Level.INFO, "Running cleanup task after add image process");
                        revert();
                    }
                };
                cleanupImage.enable();

                if (errorString == null) { // complete progress bar
                    progressPanel.getComponent().setProgressBarTextAndColor("*Data Source added.", 100, Color.black);
                }

                // Get attention for the process finish
                java.awt.Toolkit.getDefaultToolkit().beep(); //BEEP!
                AddImageWizardAddingProgressVisual panel = progressPanel.getComponent();
                if (panel != null) {
                    Window w = SwingUtilities.getWindowAncestor(panel);
                    if (w != null) {
                        w.toFront();
                    }
                }

                // Tell the panel we're done
                progressPanel.setStateFinished();

                // Commit the image
                if (!newContents.isEmpty()) //already commited
                {
                    logger.log(Level.INFO, "Assuming image already committed, will not commit.");
                    return;
                }

                if (process != null) { // and if we're done configuring ingest
                    // commit anything
                    try {
                        commitImage(wizDescriptor);
                    } catch (Exception ex) {
                        // Log error/display warning
                        logger.log(Level.SEVERE, "Error adding image to case.", ex);
                    }
                } else {
                    logger.log(Level.SEVERE, "Missing image process object");
                }




                // Start ingest if we can
                startIngest();

            } catch (Exception ex) {
                //handle unchecked exceptions post image add

                logger.log(Level.WARNING, "Unexpected errors occurred while running post add image cleanup. ", ex);

                progressPanel.getComponent().setProgressBarTextAndColor("*Failed to add image.", 0, Color.black); // set error message

                // Log error/display warning

                logger.log(Level.SEVERE, "Error adding image to case", ex);
            } finally {
            }
        }

        void interrupt() throws Exception {
            interrupted = true;
            try {
                logger.log(Level.INFO, "interrupt() add image process");
                process.stop();  //it might take time to truly stop processing and writing to db
            } catch (TskException ex) {
                throw new Exception("Error stopping add-image process.", ex);
            }
        }

        //runs in EWT
        void revert() {
            try {
                logger.log(Level.INFO, "Revert after add image process");
                try {
                    process.revert();
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error reverting add image process", ex);
                }
            } finally {
                //unlock db write within EWT thread
                SleuthkitCase.dbWriteUnlock();
            }
        }
    }
}
