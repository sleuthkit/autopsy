/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.UserArtifacts;

import com.sun.media.jfxmedia.logging.Logger;
import java.util.List;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbIngestModule;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupModuleFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;

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
            FileManager manager = Case.getCurrentCase().getServices().getFileManager();
            List<AbstractFile> file1 = manager.findFiles("Sunset.jpg");
            List<AbstractFile> file2 = manager.findFiles("Winter.jpg");
            BlackboardArtifact art1;
            BlackboardArtifact art2;
            if (!file1.isEmpty()) {
                art1 = file1.get(0).newArtifact(type1ID);
            } else {
                art1 = dataSource.newArtifact(type1ID);
            }
            if (!file2.isEmpty()) {
                art2 = file2.get(0).newArtifact(type2ID);
            } else {
                art2 = dataSource.newArtifact(type2ID);
            }
            BlackboardAttribute.Type type = Case.getCurrentCase().getSleuthkitCase().addArtifactAttributeType("TEST1", TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER, "TEST2");
            art1.addAttribute(new BlackboardAttribute(type,
                    UserArtifactIngestModuleFactory.getModuleName(), -1));
            progressBar.progress(1);
            art2.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MIN_COUNT,
                    UserArtifactIngestModuleFactory.getModuleName(), 4));
            progressBar.progress(1);
            IngestServices.getInstance().postMessage(IngestMessage.createDataMessage(
                    "name",
                    UserArtifactIngestModuleFactory.getModuleName(),
                    "Test Results",
                    "Test",
                    art1));
            return ProcessResult.OK;
        } catch (TskCoreException ex) {
            return ProcessResult.ERROR;
        } catch (TskDataException ex) {
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
