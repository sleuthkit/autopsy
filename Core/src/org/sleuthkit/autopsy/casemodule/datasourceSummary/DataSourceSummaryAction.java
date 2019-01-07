/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.EnumSet;
import javax.swing.Action;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;

public class DataSourceSummaryAction extends CallableSystemAction {

    private static final long serialVersionUID = 1L;
    private static JDialog dataSourceSummaryDialog;
    private final Long selectDataSource;

    @Messages({"DataSourceSummaryAction.caseMenuName.text=Data Source Summary"})
    DataSourceSummaryAction() {
        this(false, null);
        putValue(Action.NAME, Bundle.DataSourceSummaryAction_caseMenuName_text());
    }

    public DataSourceSummaryAction(boolean enabled, Long selectedDataSource) {
        putValue(Action.NAME, NbBundle.getMessage(DataSourceSummaryAction.class, "CTL_DataSourceSummaryAction"));
        this.setEnabled(enabled);
        selectDataSource = selectedDataSource;
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            setEnabled(null != evt.getNewValue());
        });
    }

    @Messages({"DataSourceSummaryAction.window.title=Data Source Summary"})
    @Override
    public void performAction() {
        SwingUtilities.invokeLater(() -> {
            String title = NbBundle.getMessage(this.getClass(), "DataSourceSummaryAction.window.title");
            Frame mainWindow = WindowManager.getDefault().getMainWindow();
            dataSourceSummaryDialog = new JDialog(mainWindow, title, true);
            DataSourceSummaryPanel dataSourceSummaryPanel = new DataSourceSummaryPanel();
            dataSourceSummaryPanel.addCloseButtonAction((ActionEvent e) -> {
                dataSourceSummaryDialog.dispose();
            });
            dataSourceSummaryPanel.selectDataSource(selectDataSource);
            dataSourceSummaryDialog.add(dataSourceSummaryPanel);
            dataSourceSummaryDialog.setResizable(true);
            dataSourceSummaryDialog.pack();
            dataSourceSummaryDialog.setLocationRelativeTo(mainWindow);
            dataSourceSummaryDialog.setVisible(true);
            dataSourceSummaryDialog.toFront();
        });
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(DataSourceSummaryAction.class, "CTL_DataSourceSummaryAction");
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

}
