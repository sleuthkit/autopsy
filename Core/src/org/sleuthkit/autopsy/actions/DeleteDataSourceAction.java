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
package org.sleuthkit.autopsy.actions;

import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author markm
 */
public final class DeleteDataSourceAction extends AbstractAction {
    private static final Logger logger = Logger.getLogger(DeleteDataSourceAction.class.getName());
    private final Long selectDataSource;
    
    @NbBundle.Messages({"DeleteDataSourceAction.name.text=Delete Data Source"})
    public DeleteDataSourceAction(Long selectedDataSource) {
        super(Bundle.DeleteDataSourceAction_name_text());
        selectDataSource = selectedDataSource;

    }
    @NbBundle.Messages({"ErrorDeletingDataSource.name.text=Error Deleting Data Source"})
    @Override
    public void actionPerformed(ActionEvent event) {
        try {
            //VersionNumber checkVersionNumber = Case.getCurrentCaseThrows().getSleuthkitCase().getDBSchemaVersion();
            Case.getCurrentCaseThrows().getSleuthkitCase().deleteDataSource(selectDataSource);
        } catch (NoCurrentCaseException | TskCoreException e) {
	    String msg = MessageFormat.format(Bundle.ErrorDeletingDataSource_name_text(), selectDataSource);
            logger.log(Level.WARNING, msg, e);
            //throw new TskCoreException(msg, e);
        }
    }

}
