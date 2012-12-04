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

import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;

/**
 * The "Add Image" wizard panel3. Presents the options to finish/cancel
 * image-add and run ingest.
 */
class AddImageWizardPanel3 implements WizardDescriptor.Panel<WizardDescriptor> {

    private Logger logger = Logger.getLogger(AddImageWizardPanel3.class.getName());
    private IngestConfigurator ingestConfig = Lookup.getDefault().lookup(IngestConfigurator.class);
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private Component component = null;
    private volatile Image newImage = null;
    private boolean ingested = false;
    private boolean readyToIngest = false;
    // the paths of the image files to be added
    private String imgPath;
    // the time zone where the image is added
    private String timeZone;
    //whether to not process FAT filesystem orphans
    private boolean noFatOrphans;
    // task that will clean up the created database file if the wizard is cancelled before it finishes
    private AddImageAction.CleanupTask cleanupImage; // initialized to null in readSettings()
    // flag to control the availiablity of next action
    private boolean imgAdded; // initalized to false in readSettings()
    private CurrentDirectoryFetcher fetcher;
    private AddImageProcess process;
    private AddImageAction action;
    private AddImgTask addImageTask;
    private AddImageWizardPanel2 wizPanel;

    AddImageWizardPanel3(AddImageAction action, AddImageWizardPanel2 wizPanel) {
        this.action = action;
        this.wizPanel = wizPanel;
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
            component = new AddImageVisualPanel3(ingestConfig.getIngestConfigPanel());
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
        settings.setOptions(new Object[] {WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, cancel});
        cleanupImage = null;
        readyToIngest = false;
        imgAdded = false;
        imgPath = (String) settings.getProperty(AddImageAction.IMGPATH_PROP);
        timeZone = settings.getProperty(AddImageAction.TIMEZONE_PROP).toString();
        noFatOrphans = ((Boolean) settings.getProperty(AddImageAction.NOFATORPHANS_PROP)).booleanValue();

        addImageTask = new AddImgTask(settings);
        addImageTask.execute();
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
        if (newImage != null && readyToIngest && !ingested) {
            ingested = true;
            ingestConfig.setImage(newImage);
            ingestConfig.start();
            wizPanel.getComponent().appendProgressText(" Ingest started.");
        }
    }

    /**
     * Class for getting the currently processing directory.
     *
     */
    
    private static class CurrentDirectoryFetcher extends SwingWorker<Integer,Integer> {
        AddImgTask task;
        JProgressBar prog;
        AddImageVisualPanel2 wiz;
        AddImageProcess proc;
		
        CurrentDirectoryFetcher(JProgressBar prog, AddImageVisualPanel2 wiz, AddImageProcess proc){
            this.wiz = wiz;
            this.proc = proc;
            this.prog = prog;
        }

        /**
         * @return the currently processing directory
         */
        @Override
        protected Integer doInBackground(){
            try{
                while(prog.getValue() < 100 || prog.isIndeterminate()){ //TODO Rely on state variable in AddImgTask class
                    
                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            wiz.changeCurrentDir(proc.currentDirectory());
                        }
                    });

                    Thread.sleep(2 * 1000);
                }
                return 1;
            } catch (InterruptedException ie) {
                return -1;
            }
        }

        /**
         * When done, set the Wizards processing tags to be invisible
         */
        @Override
        protected void done() {
            wiz.setProcessInvis();
        }
    }



    /**
     * Thread that will make the JNI call to ingest the image.
     */
    private class AddImgTask extends SwingWorker<Integer, Integer> {

        private JProgressBar progressBar;
        private Case currentCase;
        // true if the process was requested to stop
        private boolean interrupted = false;
        private boolean hasCritError = false;
        private String errorString = null;
        private long start;
        private WizardDescriptor settings;
        private Logger logger = Logger.getLogger(AddImgTask.class.getName());

        protected AddImgTask(WizardDescriptor settings) {
            this.progressBar = wizPanel.getComponent().getCrDbProgressBar();
            currentCase = Case.getCurrentCase();
            this.settings = settings;
        }

        /**
         * Starts the addImage process, but does not commit the results.
         *
         * @return
         * @throws Exception
         */
        @Override
        protected Integer doInBackground() {
            start = System.currentTimeMillis();
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
            fetcher = new CurrentDirectoryFetcher(this.progressBar, wizPanel.getComponent(), process);
            cancelledWhileRunning.enable();
            try {
                wizPanel.setStateStarted();
                fetcher.execute();
                process.run(new String[]{imgPath});
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

                //enqueue what would be in done() to EDT thread
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        postProcessImage();
                    }
                });

            }

            return 0;
        }

        /**
         * Commit the finished AddImageProcess, and cancel the CleanupTask that
         * would have reverted it.
         *
         * @param settings property set to get AddImageProcess and CleanupTask
         * from
         * @throws Exception if commit or adding the image to the case failed
         */
        private void commitImage(WizardDescriptor settings) throws Exception {

            String imgPath = (String) settings.getProperty(AddImageAction.IMGPATH_PROP);
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
                    newImage = Case.getCurrentCase().addImage(imgPath, imageId, timezone);
                    settings.putProperty(AddImageAction.IMAGEID_PROP, imageId);
                }

                // Can't bail and revert image add after commit, so disable image cleanup
                // task
                cleanupImage.disable();
                settings.putProperty(AddImageAction.IMAGECLEANUPTASK_PROP, null);
            }
        }

        /**
         *
         * (called by EventDispatch Thread after doInBackground finishes)
         */
        protected void postProcessImage() {
            progressBar.setIndeterminate(false);
            setProgress(100);

            // attempt actions that might fail and force the process to stop

            if (interrupted || hasCritError) {
                logger.log(Level.INFO, "Handling errors or interruption that occured in add image process");
                revert();
                if (hasCritError) {
                    //core error
                    wizPanel.getComponent().setErrors(errorString, true);
                }
                return;
            } else if (errorString != null) {
                //data error (non-critical)
                logger.log(Level.INFO, "Handling non-critical errors that occured in add image process");
                wizPanel.getComponent().setErrors(errorString, false);
            }



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
                    wizPanel.getComponent().changeProgressBarTextAndColor("*Image added.", 100, Color.black);
                }

                // Get attention for the process finish
                java.awt.Toolkit.getDefaultToolkit().beep(); //BEEP!
                AddImageVisualPanel2 panel = wizPanel.getComponent();
                if (panel != null) {
                    Window w = SwingUtilities.getWindowAncestor(panel);
                    if (w != null) {
                        w.toFront();
                    }
                }

                // Tell the panel we're done
                wizPanel.setStateFinished();

                // Commit the image
                if (newImage != null) //already commited
                {
                    logger.log(Level.INFO, "Assuming image already committed, will not commit.");
                    return;
                }

                if (process != null) { // and if we're done configuring ingest
                    // commit anything
                    try {
                        commitImage(settings);
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

                wizPanel.getComponent().changeProgressBarTextAndColor("*Failed to add image.", 0, Color.black); // set error message

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
