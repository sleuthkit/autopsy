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

import java.awt.Color;
import java.awt.EventQueue;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.ChangeSupport;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;

/**
 * The third and final panel of the add data source wizard. The visual component
 * of this panel displays a progress bar that is managed by the previous panel
 * and the data source processor.
 */
// All the real work is kicked off in the previous panel:
// {@link AddImageWizardIngestConfigPanel} (which is a bit weird if you ask m
// -jm) 
final class AddImageWizardAddingProgressPanel implements WizardDescriptor.FinishablePanel<WizardDescriptor> {

    private final ChangeSupport changeSupport;
    private final DSPProgressMonitorImpl dspProgressMonitor = new DSPProgressMonitorImpl();
    private AddImageWizardAddingProgressVisual component;
    private boolean dataSourceAdded = false;

    /**
     * Constructs an instance of the third and final panel of the add data
     * source wizard.
     */
    AddImageWizardAddingProgressPanel() {
        changeSupport = new ChangeSupport(this);
    }

    /**
     * Gets the data source progress monitor. Allows the previous panel to hand
     * off the progress monitor to the data source processor.
     */
    DSPProgressMonitorImpl getDSPProgressMonitor() {
        return dspProgressMonitor;
    }

    /**
     * A data source processor progress monitor that acts as a bridge between
     * the data source processor started by the previous panel and the progress
     * bar displayed by the visual component of this panel.
     */
    private class DSPProgressMonitorImpl implements DataSourceProcessorProgressMonitor {

        @Override
        public void setIndeterminate(final boolean indeterminate) {
            EventQueue.invokeLater(() -> {
                getComponent().getProgressBar().setIndeterminate(indeterminate);
            });
        }

        @Override
        public void setProgress(final int progress) {
            // update the progress bar asynchronously
            EventQueue.invokeLater(() -> {
                getComponent().getProgressBar().setValue(progress);
            });
        }

        @Override
        public void setProgressText(final String text) {
            // update the progress UI asynchronously
            EventQueue.invokeLater(() -> {
                getComponent().setProgressMsgText(text);
            });
        }

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
    public AddImageWizardAddingProgressVisual getComponent() {
        if (null == component) {
            component = new AddImageWizardAddingProgressVisual();
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
     * Tests whether the panel is finished and it is safe to proceed to the next
     * one. If the panel is valid, the "Next" button will be enabled.
     *
     * @return boolean true if can proceed to the next one, false otherwise
     */
    @Override
    public boolean isValid() {
        return dataSourceAdded;
    }

    /**
     * Makes the progress bar of the visual component of this panel indicate
     * that the data source processor is running.
     */
    void setStateStarted() {
        component.getProgressBar().setIndeterminate(true);
        component.setProgressBarTextAndColor(
                NbBundle.getMessage(this.getClass(), "AddImageWizardAddingProgressPanel.stateStarted.progressBarText"), 0, Color.black);
    }

    /**
     * Makes the progress bar of the visual component of this panel indicate
     * that the data source processor is not running.
     */
    void setStateFinished() {
        dataSourceAdded = true;
        getComponent().setStateFinished();
        fireChangeEvent();
    }

    /**
     * @inheritDoc5
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
        settings.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, WizardDescriptor.NEXT_OPTION, WizardDescriptor.FINISH_OPTION, WizardDescriptor.CANCEL_OPTION});
        if (dataSourceAdded) {
            getComponent().setStateFinished();
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
    }

    /**
     * Displays an error message from the data source processor for the data
     * source being added.
     *
     * @param errorString The error message to be displayed
     * @param critical    True if this is a critical error
     */
    // Should this be modified to handle a list of errors? -jm
    void displayDataSourceProcessorError(String errorString, boolean critical) {
        getComponent().showErrors(errorString, critical);
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isFinishPanel() {
        return true;
    }

}
