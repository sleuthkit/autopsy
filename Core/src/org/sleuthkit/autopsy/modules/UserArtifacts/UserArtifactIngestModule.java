/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.UserArtifacts;

import com.sun.media.jfxmedia.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author oliver
 */
public class UserArtifactIngestModule implements DataSourceIngestModule {

    private IngestJobContext context = null;
    private BlackboardArtifact.Type type1, type2;
    int type1ID, type2ID;

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
        progressBar.switchToDeterminate(2);
        try {
            BlackboardArtifact art1 = dataSource.newArtifact(type1ID);
            art1.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNT, 
            UserArtifactIngestModuleFactory.getModuleName(), 5));
            progressBar.progress(1);
            BlackboardArtifact art2 = dataSource.newArtifact(type2ID);
            art2.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MIN_COUNT, 
            UserArtifactIngestModuleFactory.getModuleName(), 4));
            progressBar.progress(1);
            return ProcessResult.OK;
        }
        catch (TskCoreException ex) {
            return ProcessResult.ERROR;
        }
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        type1 = new BlackboardArtifact.Type(39, "TSK_TEST1", "Test 1");
        type2 = new BlackboardArtifact.Type(40, "TSK_TEST2", "Test 2");
        try {
            type1ID = Case.getCurrentCase().getSleuthkitCase().addArtifactType(type1.getTypeName(), type1.getDisplayName());
            type2ID = Case.getCurrentCase().getSleuthkitCase().addArtifactType(type2.getTypeName(), type2.getDisplayName());
        }
        catch (TskCoreException ex) {
            Logger.logMsg(Logger.ERROR, "Startup failed");
        } 
    }
}
