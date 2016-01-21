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
import org.sleuthkit.autopsy.casemodule.services.Blackboard.BlackboardException;
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
                art1 = file1.get(0).newArtifact(type1.getTypeID());
            } else {
                art1 = dataSource.newArtifact(type1.getTypeID());
            }
            if (!file2.isEmpty()) {
                art2 = file2.get(0).newArtifact(type2.getTypeID());
            } else {
                art2 = dataSource.newArtifact(type2.getTypeID());
            }
            BlackboardAttribute.Type attributeType = Case.getCurrentCase().getServices().getBlackboard().addAttributeType("Test", TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG, "2");
            BlackboardAttribute.Type attributeType2 = Case.getCurrentCase().getServices().getBlackboard().addAttributeType("Test2", TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE, "3");
            art1.addAttribute(new BlackboardAttribute(attributeType,
                    UserArtifactIngestModuleFactory.getModuleName(), -1L));
            progressBar.progress(1);
            art2.addAttribute(new BlackboardAttribute(attributeType2,
                    UserArtifactIngestModuleFactory.getModuleName(), new byte[7]));
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
        } catch (BlackboardException ex) {
            return ProcessResult.ERROR;
        } catch (TskDataException ex) {
            return ProcessResult.ERROR;
        }
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        try {
            type1 = Case.getCurrentCase().getServices().getBlackboard().addArtifactType("This is", "a test");
            type2 = Case.getCurrentCase().getServices().getBlackboard().addArtifactType("Another", "kinda test");
        } catch (BlackboardException ex) {
            Logger.logMsg(Logger.ERROR, "Startup failed");
        }
    }
}
