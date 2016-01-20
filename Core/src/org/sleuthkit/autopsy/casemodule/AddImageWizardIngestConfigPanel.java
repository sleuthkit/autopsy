/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import org.openide.util.NbBundle;
import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CallableSystemAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.ingest.IngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestJobSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * The second panel of the add data source wizard. The visual component of this
 * panel allows a user to configure the ingest modules for the ingest job for
 * the data source, and this panel both runs the data source processor and
 * starts the ingest job.
 */
// JM: Think about moving the logic of adding image to the 3rd panel
// ( {@link  AddImageWizardAddingProgressPanel})
final class AddImageWizardIngestConfigPanel implements WizardDescriptor.Panel<WizardDescriptor> {

    private final AddImageWizardChooseDataSourcePanel selectDataSourcePanel;
    private final AddImageWizardAddingProgressPanel progressPanel;
    private final ChangeSupport changeSupport;
    private final List<Content> newContents;
    private final IngestJobSettingsPanel ingestJobSettingsPanel;
    private Component component;
    private volatile boolean ingestModulesConfigured;
    private volatile boolean ingestJobStarted;
    private AddImageAction.CleanupTask cleanupTask;
    private DataSourceProcessor dsProcessor;

    /**
     * Constructs an instance of The second panel of the add data source wizard.
     * The visual component of this panel allows a user to configure the ingest
     * modules for the ingest job for the data source.
     *
     * @param selectDataSourcePanel The second panel of the add data source
     *                              wizard.
     * @param progressPanel         The third panel of the data source wizard.
     */
    AddImageWizardIngestConfigPanel(AddImageWizardChooseDataSourcePanel selectDataSourcePanel, AddImageWizardAddingProgressPanel progressPanel) {
        this.selectDataSourcePanel = selectDataSourcePanel;
        this.progressPanel = progressPanel;
        changeSupport = new ChangeSupport(this);

        /*
         * Create a collection to receive the Content objects returned by the
         * data source processor.
         */
        newContents = Collections.synchronizedList(new ArrayList<Content>());

        /*
         * Get the ingest job settings for the add data source wizard context
         * and use them to create an ingest module configuration panel.
         */
        IngestJobSettings ingestJobSettings = new IngestJobSettings(AddImageWizardIngestConfigPanel.class.getCanonicalName());
        showIngestModuleConfigurationWarnings(ingestJobSettings);
        ingestJobSettingsPanel = new IngestJobSettingsPanel(ingestJobSettings);

        /*
         * This flag is used to stop the data source processing thread from
         * starting the ingest job before the user has finished configuring the
         * ingest modules. It is only set in the EDT.
         */
        ingestModulesConfigured = false;

        /*
         * This flag is required because because the storeSettings method is
         * currently called twice during the course of executing the add data
         * source wizard, whether invoked from the new case wizard or
         * independently. This means that the tryStartDataSourceIngestJob method
         * gets called three times: once when the data source processor
         * completes, and twice when the two storeSettings calls are made.
         *
         * TODO (AUT-1864): Figure out whether storeSettings is always called
         * twice by NetBeans, or the extra call is due to an Autopsy bug.
         */
        ingestJobStarted = false;
    }

    /**
     * Gets the visual component for the panel. In this template, the component
     * is kept separate. This can be more efficient: if the wizard is created
     * but never displayed, or not all panels are displayed, it is better to
     * create only those which really need to be visible.
     *
     * @return The UI component of this wizard panel.
     */
    @Override
    public Component getComponent() {
        if (null == component) {
            component = new AddImageWizardIngestConfigVisual(this.ingestJobSettingsPanel);
        }
        return component;
    }

    /**
     * Gets the help for this panel. When the panel is active, this is used as
     * the help for the wizard dialog.
     *
     * @return The help for this panel
     */
    @Override
    public HelpCtx getHelp() {
        // Show no Help button for this panel:
        return HelpCtx.DEFAULT_HELP;
    }

    /**
     * Tests whether or not the panel is finished. If the panel is valid, the
     * "Finish" button will be enabled.
     *
     * @return True or false.
     */
    @Override
    public boolean isValid() {
        // If it is always OK to press Next or Finish, then:
        return true;
        // If it depends on some condition (form filled out...), then:
        // return someCondition();
        // and when this condition changes (last form field filled in...) then:
        // fireChangeEvent();
    }

    /**
     * @inheritDoc
     */
    @Override
    public final void addChangeListener(ChangeListener listener) {
        changeSupport.addChangeListener(listener);
    }

    /**
     * @inheritDoc
     */
    @Override
    public final void removeChangeListener(ChangeListener listener) {
        changeSupport.removeChangeListener(listener);
    }

    /**
     * @inheritDoc
     */
    protected final void fireChangeEvent() {
        changeSupport.fireChange();
    }

    /**
     * Provides the wizard panel with the current data--either the default data
     * or already-modified settings, if the user used the previous and/or next
     * buttons. This method can be called multiple times on one instance of
     * WizardDescriptor.Panel.
     *
     * @param settings The settings.
     */
    @Override
    public void readSettings(WizardDescriptor settings) {
        /*
         * The user has pushed the next button on the previous panel of the add
         * data source wizard. Start the data source processor so it can run
         * while the user is doing the ingest module configuration. It is ok to
         * do this now because the back button is disabled for this wizard - the
         * user cannot go back to choose a different data source.
         *
         * RC: Not sure why the cancel button is disabled.
         */
        JButton cancel = new JButton(NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.CANCEL_BUTTON.text"));
        cancel.setEnabled(false);
        settings.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, cancel});
        startDataSourceProcessing(settings);
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
        /*
         * The user has pushed the next button on this panel. Save the ingest
         * job settings for the add data source wizard context and try to start
         * the ingest job. It is ok to do this now because the back button is
         * disabled for this wizard - the user cannot go back to choose a
         * different data source. However, the job will not be started if either
         * the data source processor has not finished yet, or it finished but
         * did not produce any Content objects for the data source.
         */
        IngestJobSettings ingestJobSettings = this.ingestJobSettingsPanel.getSettings();
        ingestJobSettings.save();
        showIngestModuleConfigurationWarnings(ingestJobSettings);
        ingestModulesConfigured = true;
        tryStartIngestJob();
    }

    /**
     * Displays any warnings returned form operations on the ingest job settings
     * for the add data source wizard context.
     *
     * @param ingestJobSettings The ingest job settings.
     */
    private static void showIngestModuleConfigurationWarnings(IngestJobSettings ingestJobSettings) {
        List<String> warnings = ingestJobSettings.getWarnings();
        if (warnings.isEmpty() == false) {
            StringBuilder warningMessage = new StringBuilder();
            for (String warning : warnings) {
                warningMessage.append(warning).append("\n");
            }
            JOptionPane.showMessageDialog(null, warningMessage.toString());
        }
    }

    /**
     * Starts the data source processor selected by the user when the previous
     * panel was displayed.
     */
    private void startDataSourceProcessing(WizardDescriptor settings) {
        /*
         * Create a unique id to use to synch up the data source events that are
         * published for the data source being added.
         */
        final UUID addDataSourceEventId = UUID.randomUUID();

        /*
         * Register a clean up task with the action that invokes the add data
         * source wizard. This action takes responsibility for doing cleanup
         * after the wizard is closed (see the definition of the AddImageAction
         * class).
         */
        cleanupTask = CallableSystemAction.get(AddImageAction.class).new CleanupTask() {
            @Override
            void cleanup() throws Exception {
                new Thread(() -> {
                    /*
                     * Publish a failed adding data source event, using the
                     * event id associated with the adding data source event.
                     */
                    Case.getCurrentCase().notifyFailedAddingDataSource(addDataSourceEventId);
                }).start();
                dsProcessor.cancel();
            }
        };
        cleanupTask.enable();

        /*
         * Publish an adding data source event.
         */
        new Thread(() -> {
            Case.getCurrentCase().notifyAddingDataSource(addDataSourceEventId);
        }).start();

        /*
         * Notify the progress panel for this wizard that data source processing
         * is starting.
         */
        progressPanel.setStateStarted();

        /*
         * Get the data source processor the user selected and start it.
         */
        dsProcessor = selectDataSourcePanel.getComponent().getCurrentDSProcessor();
        DataSourceProcessorCallback callback = new DataSourceProcessorCallback() {
            @Override
            public void doneEDT(DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> contents) {
                dataSourceProcessorDone(addDataSourceEventId, result, errList, contents);
            }
        };
        dsProcessor.run(progressPanel.getDSPProgressMonitor(), callback);
    }

    /*
     * The callback for the data source processor to invoke when it finishes.
     */
    private void dataSourceProcessorDone(UUID addDataSourceEventId, DataSourceProcessorCallback.DataSourceProcessorResult result, List<String> errList, List<Content> contents) {
        /*
         * Disable the clean up task.
         */
        cleanupTask.disable();

        /*
         * Get the user's attention.
         *
         * RC: Is this really necessary?
         */
        java.awt.Toolkit.getDefaultToolkit().beep();
        AddImageWizardAddingProgressVisual panel = progressPanel.getComponent();
        if (panel != null) {
            Window w = SwingUtilities.getWindowAncestor(panel);
            if (w != null) {
                w.toFront();
            }
        }

        /*
         * Notify the progress panel that the data source processor has finished
         * its work and display the processing results on the progress panel.
         */
        progressPanel.setStateFinished();
        if (result == DataSourceProcessorCallback.DataSourceProcessorResult.NO_ERRORS) {
            progressPanel.getComponent().setProgressBarTextAndColor(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.dsProcDone.noErrs.text"), 100, Color.black);
        } else {
            progressPanel.getComponent().setProgressBarTextAndColor(
                    NbBundle.getMessage(this.getClass(), "AddImageWizardIngestConfigPanel.dsProcDone.errs.text"), 100, Color.red);
        }
        boolean critErr = false;
        if (result == DataSourceProcessorCallback.DataSourceProcessorResult.CRITICAL_ERRORS) {
            critErr = true;
        }
        for (String err : errList) {
            progressPanel.displayDataSourceProcessorError(err, critErr);
        }

        /*
         * Save the Content objects returned by the data source processor.
         */
        newContents.clear();
        newContents.addAll(contents);
        new Thread(() -> {
            if (!newContents.isEmpty()) {
                Case.getCurrentCase().notifyDataSourceAdded(newContents.get(0), addDataSourceEventId);
            } else {
                Case.getCurrentCase().notifyFailedAddingDataSource(addDataSourceEventId);
            }
        }).start();

        /*
         * Try starting the ingest job for this data source. If the data source
         * processor finished before the user finished configuring the ingest
         * modules, the job will not be started yet.
         */
        tryStartIngestJob();
    }

    /**
     * Starts an ingest job for the data source, but only if the data source
     * processor has been run and has produced content, and the ingest modules
     * are configured.
     */
    synchronized private void tryStartIngestJob() {
        if (!newContents.isEmpty() && ingestModulesConfigured && !ingestJobStarted) {
            ingestJobStarted = true; // See contructor for comment on this flag.
            IngestManager.getInstance().queueIngestJob(newContents, ingestJobSettingsPanel.getSettings());
        }
    }

}
