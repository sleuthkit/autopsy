/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.event.ChangeListener;
import org.openide.WizardDescriptor;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.guiutils.CheckBoxListPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Selection panel for modules that can report on a subset of data sources in a
 * case.
 */
public class ReportWizardDataSourceSelectionPanel implements WizardDescriptor.FinishablePanel<WizardDescriptor> {

    private static final Logger logger = Logger.getLogger(ReportWizardDataSourceSelectionPanel.class.getName());

    private CheckBoxListPanel<Long> dataSourcesSelectionPanel;
    private WizardDescriptor wiz;

    private final JButton finishButton;
    private final JButton nextButton;

    @NbBundle.Messages({
        "ReportWizardDataSourceSelectionPanel.nextButton.text=Next",
        "ReportWizardDataSourceSelectionPanel.finishButton.text=Finish"
    })
    public ReportWizardDataSourceSelectionPanel() {
        nextButton = new JButton(Bundle.ReportWizardDataSourceSelectionPanel_nextButton_text());
        finishButton = new JButton(Bundle.ReportWizardDataSourceSelectionPanel_finishButton_text());
        finishButton.setEnabled(false);
        nextButton.setEnabled(false);

        finishButton.addActionListener(createFinishButtonActionListener());
        nextButton.addActionListener(createNextButtonActionListener());
    }
    
    @NbBundle.Messages({
        "ReportWizardDataSourceSelectionPanel.confirmEmptySelection=Are you sure you want to proceed with no selections?"
    })
    private ActionListener createNextButtonActionListener() {
        return (ActionEvent e) -> {
            if(!dataSourcesSelectionPanel.getSelectedElements().isEmpty()) {
                wiz.doNextClick();
            } else if(MessageNotifyUtil.Message.confirm(Bundle.ReportWizardDataSourceSelectionPanel_confirmEmptySelection())) {
                wiz.doNextClick();
            }
        };
    }
    
    private ActionListener createFinishButtonActionListener() {
        return (ActionEvent e) -> {
            if(!dataSourcesSelectionPanel.getSelectedElements().isEmpty()) {
                wiz.doFinishClick();
            } else if(MessageNotifyUtil.Message.confirm(Bundle.ReportWizardDataSourceSelectionPanel_confirmEmptySelection())) {
                wiz.doFinishClick();
            }
        };
    }

    @NbBundle.Messages({
        "ReportWizardDataSourceSelectionPanel.title=Select which data source(s) to include"
    })
    @Override
    public CheckBoxListPanel<Long> getComponent() {
        if (dataSourcesSelectionPanel == null) {
            dataSourcesSelectionPanel = new CheckBoxListPanel<>();
            dataSourcesSelectionPanel.setName(Bundle.ReportWizardDataSourceSelectionPanel_title());
            try {
                List<Content> dataSources = Case.getCurrentCase().getDataSources();
                for (Content dataSource : dataSources) {
                    String dataSourceName = dataSource.getName();
                    long dataSourceId = dataSource.getId();
                    dataSourcesSelectionPanel.addElement(dataSourceName, null, dataSourceId);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to get list of data sources from the case", ex);
            }
        }
        return dataSourcesSelectionPanel;
    }

    @Override
    public HelpCtx getHelp() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public void readSettings(WizardDescriptor data) {
        this.wiz = data;
        wiz.setOptions(new Object[]{WizardDescriptor.PREVIOUS_OPTION, nextButton, finishButton, WizardDescriptor.CANCEL_OPTION});

        boolean generalModule = NbPreferences.forModule(ReportWizardPanel1.class).getBoolean("generalModule", true); //NON-NLS
        nextButton.setEnabled(!generalModule);
        finishButton.setEnabled(generalModule);
    }

    @Override
    public void storeSettings(WizardDescriptor data) {
        List<Long> selectedDataSources = getComponent().getSelectedElements();
        data.putProperty("dataSourceSelections", selectedDataSources);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addChangeListener(ChangeListener cl) {
        // Nothing to do
    }

    @Override
    public void removeChangeListener(ChangeListener cl) {
        // Nothing to do
    }

    @Override
    public boolean isFinishPanel() {
        return true;
    }
}
