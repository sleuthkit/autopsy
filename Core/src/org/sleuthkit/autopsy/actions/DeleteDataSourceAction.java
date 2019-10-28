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
 * Instances of this Action allow users to delete data sources.
 */
public final class DeleteDataSourceAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DeleteDataSourceAction.class.getName());
    private long dataSourceID;

    /**
     * Constructs an Action that allows a user to delete a data source.
     *
     * @param dataSourceID The object ID of the data source to be deleted.
     */
    @NbBundle.Messages({
        "DeleteDataSourceAction.name.text=Delete Data Source"
    })
    public DeleteDataSourceAction(Long dataSourceID) {
        super(Bundle.DeleteDataSourceAction_name_text());
        this.dataSourceID = dataSourceID;
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        try {
            Case.getCurrentCaseThrows().getSleuthkitCase().deleteDataSource(dataSourceID);
            KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            kwsService.deleteDataSource(dataSourceID);
        } catch (NoCurrentCaseException | TskCoreException | KeywordSearchServiceException e) {
            logger.log(Level.WARNING, String.format("Error Deleting data source (obj_id=%d)", dataSourceID), e);
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Object clonedObject = super.clone();
        ((DeleteDataSourceAction) clonedObject).setDataSourceID(this.dataSourceID);
        return clonedObject;
    }

    private void setDataSourceID(long dataSourceID) {
        this.dataSourceID = dataSourceID;
    }

}
