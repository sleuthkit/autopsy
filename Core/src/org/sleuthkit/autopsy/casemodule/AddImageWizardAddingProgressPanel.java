/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.runIngestModuleWizard.ShortcutWizardDescriptorPanel;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * The final panel of the add image wizard. It displays a progress bar and
 * status updates.
 *
 * All the real work is kicked off in the previous panel:
 * {@link AddImageWizardIngestConfigPanel} (which is a bit weird if you ask m
 * -jm)
 */
class AddImageWizardAddingProgressPanel extends ShortcutWizardDescriptorPanel {

    private boolean readyToIngest = false;
    // task that will clean up the created database file if the wizard is cancelled before it finishes
    private AddImageAction.CleanupTask cleanupTask;

    private final AddImageAction addImageAction;

    private DataSourceProcessor dsProcessor = null;
    private boolean cancelled;
    /**
     * flag to indicate that the image adding process is finished and this panel
     * is completed(valid)
     */
    private boolean imgAdded = false;
    private boolean ingested = false;
    /**
     * The visual component that displays this panel. If you need to access the
     * component from this class, just use getComponent().
     */
    private AddImageWizardAddingProgressVisual component;
    private final Set<ChangeListener> listeners = new HashSet<>(1); // or can use ChangeSupport in NB 6.0
    private final List<Content> newContents = Collections.synchronizedList(new ArrayList<Content>());
    private final DSPProgressMonitorImpl dspProgressMonitorImpl = new DSPProgressMonitorImpl();
    private IngestJobSettings ingestJobSettings;

    AddImageWizardAddingProgressPanel(AddImageAction action) {
        this.addImageAction = action;
    }

    DSPProgressMonitorImpl getDSPProgressMonitorImpl() {
        return dspProgressMonitorImpl;
    }

    private class DSPProgressMonitorImpl implements DataSourceProcessorProgressMonitor {

        @Override
        public void setIndeterminate(final boolean indeterminate) {
            // update the progress bar asynchronously
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    getComponent().getProgressBar().setIndeterminate(indeterminate);
                }
            });
        }

        @Override
        public void setProgress(final int progress) {
            // update the progress bar asynchronously
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    getComponent().getProgressBar().setValue(progress);
                }
            });
        }

        @Override
        public void setProgressText(final String text) {
            // update the progress UI asynchronously
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    getComponent().setProgressMsgText(text);
                }
            });
        }

    }

    /**
     * Get the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * It also separates the view from the control - jm
     *
     * @return component the UI component of this wizard panel
     */
    @Override
    public AddImageWizardAddingProgressVisual getComponent() {
        if (component == null) {
            component = new AddImageWizardAddingProgressVisual();
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
            Lookup.getDefault().lookup(AddImageAction.class).requestFocusButton(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardAddingProgressPanel.isValid.focusNext"));
        }

        return imgAdded;
    }

    /**
     * Updates the UI to display the add image process has begun.
     */
    void setStateStarted() {
        component.getProgressBar().setIndeterminate(true);
        component.setProgressBarTextAndColor(
                NbBundle.getMessage(this.getClass(), "AddImageWizardAddingProgressPanel.stateStarted.progressBarText"), 0, Color.black);
    }

    /**
     * Updates the UI to display the add image process is over.
     */
    void setStateFinished() {
        imgAdded = true;
        getComponent().setStateFinished();
        fireChangeEvent();
    }

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
            it = new HashSet<>(listeners).iterator();
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
        // Start ingest if it hasn't already been started 
        startIngest();
        settings.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, WizardDescriptor.CANCEL_OPTION});
        if (imgAdded) {
            getComponent().setStateFinished();
        }
    }

    void resetReadyToIngest() {
        this.readyToIngest = false;
    }

    void setIngestJobSettings(IngestJobSettings ingestSettings) {
        showWarnings(ingestSettings);
        this.readyToIngest = true;
        this.ingestJobSettings = ingestSettings;
    }

    /**
     * this doesn't appear to store anything? plus, there are no settings in
     * this panel -jm
     *
     * @param settings the setting to be stored to
     */
    @Override
    public void storeSettings(WizardDescriptor settings) {
        //why did we do this? -jm
        //  getComponent().resetInfoPanel();
    }

    /**
     * forward errors to visual component
     *
     * should this be modified to handle a list of errors? -jm
     *
     *
     * @param errorString the error string to be displayed
     * @param critical    true if this is a critical error
     */
    void addErrors(String errorString, boolean critical) {
        getComponent().showErrors(errorString, critical);
    }

    /**
     * Start ingest after verifying we have a new image, we are ready to ingest,
     * and we haven't already ingested.
     */
    private void startIngest() {
        if (!newContents.isEmpty() && readyToIngest && !ingested) {
            ingested = true;
            IngestManager.getInstance().queueIngestJob(newContents, ingestJobSettings);
            setStateFinished();
        }
    }

    private static void showWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder();
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), warningMessage.toString());
        }
    }

    /**
     * Starts the Data source processing by kicking off the selected
     * DataSourceProcessor
     */
    void startDataSourceProcessing(DataSourceProcessor dsp) {
        if (dsProcessor == null) {  //this can only be run once
            final UUID dataSourceId = UUID.randomUUID();
            newContents.clear();
            cleanupTask = null;
            readyToIngest = false;
            dsProcessor = dsp;

            // Add a cleanup task to interrupt the background process if the
            // wizard exits while the background process is running.
            cleanupTask = addImageAction.new CleanupTask() {
                @Override
                void cleanup() throws Exception {
                    WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    cancelDataSourceProcessing(dataSourceId);
                    cancelled = true;
                    WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            };

            cleanupTask.enable();

            new Thread(() -> {
                try {
                    Case.getOpenCase().notifyAddingDataSource(dataSourceId);
                } catch (NoCurrentCaseException ex) {
                     Logger.getLogger(AddImageWizardAddingProgressVisual.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
                }
            }).start();
            DataSourceProcessorCallback cbObj = new DataSourceProcessorCallback() {
                @Override
                public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> contents) {
                    dataSourceProcessorDone(dataSourceId, result, errList, contents);
                }
            };

            setStateStarted();

            // Kick off the DSProcessor 
            dsProcessor.run(getDSPProgressMonitorImpl(), cbObj);
        }
    }

    /*
     * Cancels the data source processing - in case the users presses 'Cancel'
     */
    private void cancelDataSourceProcessing(UUID dataSourceId) {
        dsProcessor.cancel();
    }

    /*
     * Callback for the data source processor. Invoked by the DSP on the EDT
     * thread, when it finishes processing the data source.
     */
    private void dataSourceProcessorDone(UUID dataSourceId, DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> contents) {
        // disable the cleanup task
        cleanupTask.disable();

        // Get attention for the process finish
        // this caused a crash on OS X
        if (PlatformUtil.isWindowsOS() == true) {
            java.awt.Toolkit.getDefaultToolkit().beep(); //BEEP!
        }
        AddImageWizardAddingProgressVisual panel = getComponent();
        if (panel != null) {
            Window w = SwingUtilities.getWindowAncestor(panel);
            if (w != null) {
                w.toFront();
            }
        }
        // Tell the panel we're done
        setStateFinished();

        //check the result and display to user
        if (result == DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS) {
            getComponent().setProgressBarTextAndColor(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.dsProcDone.noErrs.text"), 100, Color.black);
        } else {
            getComponent().setProgressBarTextAndColor(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.dsProcDone.errs.text"), 100, Color.red);
        }

        //if errors, display them on the progress panel
        boolean critErr = false;
        if (result == DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS) {
            critErr = true;
        }
        for (String err : errList) {
            //  TBD: there probably should be an error level for each error
            addErrors(err, critErr);
        }

        //notify the UI of the new content added to the case
        new Thread(() -> {
            try {
                if (!contents.isEmpty()) {
                    Case.getOpenCase().notifyDataSourceAdded(contents.get(0), dataSourceId);
                } else {
                    Case.getOpenCase().notifyFailedAddingDataSource(dataSourceId);
                }
            } catch (NoCurrentCaseException ex) {
                Logger.getLogger(AddImageWizardAddingProgressVisual.class.getName()).log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            }
        }).start();

        if (!cancelled) {
            newContents.clear();
            newContents.addAll(contents);
            setStateStarted();
            startIngest();
        } else {
            cancelled = false;
        }

    }
}
