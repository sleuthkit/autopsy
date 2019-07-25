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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;

/**
 * Instances of this Action allow users to delete the specified data source.
 */
public final class DeleteDataSourceAction extends AbstractAction {
    private static final Logger logger = Logger.getLogger(DeleteDataSourceAction.class.getName());
    private final Long selectedDataSource;
    
    @NbBundle.Messages({"DeleteDataSourceAction.name.text=Delete Data Source"})
    public DeleteDataSourceAction(Long selectedDataSource) {
        super(Bundle.DeleteDataSourceAction_name_text());
        this.selectedDataSource = selectedDataSource;
    }
    
    @NbBundle.Messages({"ErrorDeletingDataSource.name.text=Error Deleting Data Source",
                        "DeleteDataSourceConfirmationDialog_message=Are you sure you want to delete the data source?",
                        "DeleteDataSourceConfirmationDialog_title=Delete Data Source?"})
    @Override
    public void actionPerformed(ActionEvent event) {
        Object response = DialogDisplayer.getDefault().notify(new NotifyDescriptor(
                Bundle.DeleteDataSourceConfirmationDialog_message(),
                Bundle.DeleteDataSourceConfirmationDialog_title(),
                NotifyDescriptor.YES_NO_OPTION,
                NotifyDescriptor.WARNING_MESSAGE,
                null,
                NotifyDescriptor.NO_OPTION));
        if (null != response && DialogDescriptor.YES_OPTION == response) {

            new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        Case.deleteDataSourceFromCurrentCase(selectedDataSource);
                        deleteDataSource(selectedDataSource);
                    } catch (CaseActionException | KeywordSearchServiceException ex) {
                        logger.log(Level.WARNING, Bundle.ErrorDeletingDataSource_name_text(), ex);
                        MessageNotifyUtil.Message.info(Bundle.ErrorDeletingDataSource_name_text());
                    }
                    return null;
                }

                @Override
                protected void done() {
                }
            }.execute();        
        }
    } 
    
    private static void deleteDataSource(Long dataSourceId) throws KeywordSearchServiceException {
        try {
            KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            kwsService.deleteDataSource(dataSourceId);
        } catch (KeywordSearchServiceException ex) {
            logger.log(Level.WARNING, "KWS Error", ex);
        } 
    }
}
