/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;

/**
 * ViewSummaryInformationAction action for opening a Data Sources Summary Dialog
 * with the specified data source selected if it is present.
 */
public final class ViewSummaryInformationAction extends AbstractAction {

    private static DataSourceSummaryDialog dataSourceSummaryDialog;
    private static Long selectDataSource;
    private static final long serialVersionUID = 1L;

    /**
     * Create a ViewSummaryInformationAction for the selected datasource.
     *
     * @param selectedDataSource - the data source which is currently selected
     *                           and will be selected initially when the
     *                           DataSourceSummaryDialog opens.
     */
    @Messages({"ViewSummaryInformationAction.name.text=View Summary Information"})
    public ViewSummaryInformationAction(Long selectedDataSource) {
        super(Bundle.ViewSummaryInformationAction_name_text());
        selectDataSource = selectedDataSource;
    }

    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        SwingUtilities.invokeLater(() -> {
            Frame mainWindow = WindowManager.getDefault().getMainWindow();
            dataSourceSummaryDialog = new DataSourceSummaryDialog(mainWindow);
            dataSourceSummaryDialog.populatePanel(selectDataSource);
            dataSourceSummaryDialog.setResizable(true);
            dataSourceSummaryDialog.setLocationRelativeTo(mainWindow);
            dataSourceSummaryDialog.setVisible(true);
            dataSourceSummaryDialog.toFront();
        });
    }

}
