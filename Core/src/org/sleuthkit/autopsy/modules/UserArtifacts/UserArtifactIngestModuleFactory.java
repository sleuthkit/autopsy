/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.UserArtifacts;

import org.openide.util.NbBundle;
import org.python.apache.xmlcommons.Version;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 *
 * @author oliver
 */
public class UserArtifactIngestModuleFactory extends IngestModuleFactoryAdapter {

    static String getModuleName() {
        return NbBundle.getMessage(UserArtifactIngestModuleFactory.class, "UserArtifactIngestModuleFactory.moduleName");
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(UserArtifactIngestModuleFactory.class, "AndroidModuleFactory.moduleDescription");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }
    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        return new UserArtifactIngestModule();
    }
    
    
}
