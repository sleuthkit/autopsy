/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.UserArtifacts;

import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author oliver
 */
public class UserArtifactIngestModule implements DataSourceIngestModule {

    private IngestJobContext context = null;
    private BlackboardArtifact.Type type1, type2;

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        try {
            dataSource.newArtifact(type1.getTypeID());
            dataSource.newArtifact(type2.getTypeID());
            return ProcessResult.OK;
        }
        catch (TskCoreException ex) {
            return ProcessResult.ERROR;
        }
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        type1 = new BlackboardArtifact.Type(40, "TSK_TEST1", "Test 1");
        type2 = new BlackboardArtifact.Type(41, "TSK_TEST2", "Test 2");
    }

}
