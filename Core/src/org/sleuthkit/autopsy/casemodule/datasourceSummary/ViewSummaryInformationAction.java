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
import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;

/**
 *
 * @author wschaefer
 */
public final class ViewSummaryInformationAction extends AbstractAction {

    private static JDialog dataSourceSummaryDialog;
    private static Long selectDataSource;
    private static final long serialVersionUID = 1L;

    @NbBundle.Messages({"ViewSummaryInformationAction.name.text=View Summary Information"})
    public ViewSummaryInformationAction(Long selectedDataSource) {
        super(Bundle.ViewSummaryInformationAction_name_text());
        this.setEnabled(true);
        selectDataSource = selectedDataSource;
    }
    
    @NbBundle.Messages({"ViewSummaryInformationAction.window.title=Data Source Summary"})
    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        SwingUtilities.invokeLater(() -> {
            String title = Bundle.ViewSummaryInformationAction_window_title();
            Frame mainWindow = WindowManager.getDefault().getMainWindow();
            dataSourceSummaryDialog = new JDialog(mainWindow, title, true);
            DataSourceSummaryPanel dataSourceSummaryPanel = new DataSourceSummaryPanel();
            dataSourceSummaryPanel.addCloseButtonAction((ActionEvent event) -> {
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

}
