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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to delete the specified data source.
 */
public final class DeleteDataSourceAction extends AbstractAction {
    private static final Logger logger = Logger.getLogger(DeleteDataSourceAction.class.getName());
    private final Long dataSourceId;
    
    @NbBundle.Messages({"DeleteDataSourceAction.name.text=Delete Data Source"})
    public DeleteDataSourceAction(Long dataSourceId) {
        super(Bundle.DeleteDataSourceAction_name_text());
        this.dataSourceId = dataSourceId;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        try {
            Case.getCurrentCaseThrows().getSleuthkitCase().deleteDataSource(dataSourceId);
            deleteDataSource(dataSourceId);
        } catch (NoCurrentCaseException | TskCoreException | KeywordSearchServiceException e) {
            logger.log(Level.WARNING, "Error Deleting Data source " + dataSourceId, e);
        }
    }
    private static void deleteDataSource(Long dataSourceId) throws KeywordSearchServiceException {
        try {
            KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            kwsService.deleteDataSource(dataSourceId);
        } catch (KeywordSearchServiceException e) {
            logger.log(Level.WARNING, "KWS Error", e);
        }
        
    }
}
