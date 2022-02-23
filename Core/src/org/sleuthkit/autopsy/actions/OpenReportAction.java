/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import java.io.File;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;

/**
 * Action to open report.
 */
public class OpenReportAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final String reportPath;

    /**
     * Constructor.
     *
     * @param reportPath Path to report.s
     */
    public OpenReportAction(String reportPath) {
        super(NbBundle.getMessage(OpenReportAction.class, "OpenReportAction.actionDisplayName"));
        this.reportPath = reportPath;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (reportPath.toLowerCase().startsWith("http")) {
            ExternalViewerAction.openURL(reportPath);
        } else {
            String extension = "";
            int extPosition = reportPath.lastIndexOf('.');
            if (extPosition != -1) {
                extension = reportPath.substring(extPosition, reportPath.length()).toLowerCase();
            }

            ExternalViewerAction.openFile("", extension, new File(reportPath));
        }
    }
}
