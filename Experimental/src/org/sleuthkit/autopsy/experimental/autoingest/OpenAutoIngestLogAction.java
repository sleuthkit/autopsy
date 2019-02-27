/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * An action that opens a case auto ingest log given the coordination service
 * node data for the case.
 */
final class OpenAutoIngestLogAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OpenAutoIngestLogAction.class.getName());
    private static final String CASE_AUTO_INGEST_LOG_FILE_NAME = "auto_ingest_log.txt";
    private final Path caseAutoIngestLogFilePath;

    /**
     * Constructs an action that opens a case auto ingest log given the
     * coordination service node data for the case.
     *
     * @param caseNodeData The coordination service node data for the case.
     */
    @NbBundle.Messages({
        "OpenAutoIngestLogAction.menuItemText=Open Auto Ingest Log File"
    })
    OpenAutoIngestLogAction(CaseNodeData caseNodeData) {
        super(Bundle.OpenAutoIngestLogAction_menuItemText());
        this.caseAutoIngestLogFilePath = Paths.get(caseNodeData.getDirectory().toString(), CASE_AUTO_INGEST_LOG_FILE_NAME);
        this.setEnabled(caseAutoIngestLogFilePath.toFile().exists());
    }

    @NbBundle.Messages({
        "OpenAutoIngestLogAction.deletedLogErrorMsg=The case auto ingest log has been deleted.",
        "OpenAutoIngestLogAction.logOpenFailedErrorMsg=Failed to open case auto ingest log. See application log for details."
    })
    @Override
    public void actionPerformed(ActionEvent event) {
        try {
            if (caseAutoIngestLogFilePath.toFile().exists()) {
                Desktop.getDesktop().edit(caseAutoIngestLogFilePath.toFile());
            } else {
                MessageNotifyUtil.Message.error(Bundle.OpenAutoIngestLogAction_deletedLogErrorMsg());
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error opening case auto ingest log file at %s", caseAutoIngestLogFilePath), ex); //NON-NLS
            MessageNotifyUtil.Message.error(Bundle.OpenAutoIngestLogAction_logOpenFailedErrorMsg());
        }
    }

    @Override
    public OpenAutoIngestLogAction clone() throws CloneNotSupportedException {
        super.clone();
        throw new CloneNotSupportedException();
    }

}
