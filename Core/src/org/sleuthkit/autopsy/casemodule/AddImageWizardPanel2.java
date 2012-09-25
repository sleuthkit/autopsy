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
import java.awt.EventQueue;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskException;

/**
 * The "Add Image" wizard panel2. Handles processing the image in a worker
 * thread, and any errors that may occur during the add process.
 */
class AddImageWizardPanel2 implements WizardDescriptor.Panel<WizardDescriptor> {

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
    private AddImageProcess process;
    private AddImgTask addImageTask;
    private static final Logger logger = Logger.getLogger(AddImageWizardPanel2.class.getName());
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private AddImageVisualPanel2 component;
    private AddImageAction action;

    AddImageWizardPanel2(AddImageAction action) {
        this.action = action;
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
    public AddImageVisualPanel2 getComponent() {
        if (component == null) {
            component = new AddImageVisualPanel2();
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
    }

    /**
     * Tests whether the panel is finished and it is safe to proceed to the next
     * one. If the panel is valid, the "Next" button will be enabled.
     *
     * @return boolean true if can proceed to the next one, false otherwise
     */
    @Override
    public boolean isValid() {
        // set the focus to the next button of the wizard dialog if it's enabled
        if (imgAdded) {
            Lookup.getDefault().lookup(AddImageAction.class).requestFocusButton("Next >");
        }

        return imgAdded;
    }

    /**
     * Creates the database and adds the image to the XML configuration file.
     *
     */
    private void startAddImage() {
        component.getCrDbProgressBar().setIndeterminate(true);
        component.changeProgressBarTextAndColor("*Adding the image may take some time for large images.", 0, Color.black);

        addImageTask = new AddImgTask();
        addImageTask.execute();
    }

    /**
     * Sets the isDbCreated variable in this class and also invoke
     * fireChangeEvent() method.
     *
     * @param created whether the database already created or not
     */
    private void setDbCreated(Boolean created) {
        imgAdded = created;
        fireChangeEvent();
    }
    private final Set<ChangeListener> listeners = new HashSet<ChangeListener>(1); // or can use ChangeSupport in NB 6.0

    /**
     * Adds a listener to changes of the panel's validity.
     *
     * @param l the change listener to add
     */
    @Override
    public final void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    /**
     * Removes a listener to changes of the panel's validity.
     *
     * @param l the change listener to move
     */
    @Override
    public final void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /**
     * This method is auto-generated. It seems that this method is used to
     * listen to any change in this wizard panel.
     */
    protected final void fireChangeEvent() {
        Iterator<ChangeListener> it;
        synchronized (listeners) {
            it = new HashSet<ChangeListener>(listeners).iterator();
        }
        ChangeEvent ev = new ChangeEvent(this);
        while (it.hasNext()) {
            it.next().stateChanged(ev);
        }
    }

    /**
     * Load the image locations from the WizardDescriptor settings object, and
     * the
     *
     * @param settings the setting to be read from
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        cleanupImage = null;
        imgAdded = false;
        imgPath = (String) settings.getProperty(AddImageAction.IMGPATH_PROP);
        timeZone = settings.getProperty(AddImageAction.TIMEZONE_PROP).toString();
        noFatOrphans = ((Boolean) settings.getProperty(AddImageAction.NOFATORPHANS_PROP)).booleanValue();

        getComponent().resetInfoPanel();

        startAddImage();
    }

    /**
     *
     * @param settings the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {

        // Store process so it can be committed if wizard finishes
        settings.putProperty(AddImageAction.PROCESS_PROP, process);

        // Need to make the cleanup accessible to the finished wizard so it can
        // be cancelled if all goes well, and availble if we return to this
        // panel so the the previously added image can be reverted
        settings.putProperty(AddImageAction.IMAGECLEANUPTASK_PROP, cleanupImage);
        
        getComponent().resetInfoPanel();
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

        protected AddImgTask() {
            this.progressBar = getComponent().getCrDbProgressBar();
            currentCase = Case.getCurrentCase();
        }

        /**
         * Starts the addImage process, but does not commit the results.
         *
         * @return
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
            cancelledWhileRunning.enable();
            try {
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
            }
            this.setProgress(100);
            return 0;
        }

        /**
         *
         * (called by EventDispatch Thread after doInBackground finishes)
         */
        @Override
        protected void done() {
            progressBar.setIndeterminate(false);

            // attempt actions that might fail and force the process to stop

            try {
                //get() will block until doInBackground done and throw any exceptions 
                //that were thrown in the background task
                //if process was stopped, stop should have been complete (otherwise, unsafe to revert() )
                get();
            } catch (InterruptedException e) {
            } catch (ExecutionException e) {
            } finally {
                if (interrupted || hasCritError) {
                    logger.log(Level.INFO, "Handling errors or interruption that occured in add image process");
                    revert();
                    if (hasCritError) {
                        //core error
                         getComponent().setErrors(errorString, true);
                    }
                    return;
                } else if (errorString != null) {
                    //data error (non-critical)
                    logger.log(Level.INFO, "Handling non-critical errors that occured in add image process");
                    getComponent().setErrors(errorString, false);
                }
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

                if (errorString == null) 
                    getComponent().changeProgressBarTextAndColor("*Image added.", 100, Color.black); // complete progress bar

                // Get attention for the process finish
                java.awt.Toolkit.getDefaultToolkit().beep(); //BEEP!
                AddImageVisualPanel2 panel = getComponent();
                if (panel != null) {
                    Window w = SwingUtilities.getWindowAncestor(panel);
                    if (w != null) {
                        w.toFront();
                    }
                }

                setDbCreated(true);

            } catch (Exception ex) {
                //handle unchecked exceptions post image add

                logger.log(Level.WARNING, "Unexpected errors occurred while running post add image cleanup. ", ex);

                getComponent().changeProgressBarTextAndColor("*Failed to add image.", 0, Color.black); // set error message

                // Log error/display warning
                Logger logger = Logger.getLogger(AddImgTask.class.getName());
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
