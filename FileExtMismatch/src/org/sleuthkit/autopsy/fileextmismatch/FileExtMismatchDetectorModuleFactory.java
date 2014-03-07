/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.fileextmismatch;

import java.io.Serializable;
import java.util.ArrayList;
import javax.swing.JPanel;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleOptions;
import org.sleuthkit.autopsy.ingest.IngestModuleOptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleResourcesConfigPanel;

/**
 * An factory that creates file ingest modules that do hash database lookups.
 */
@ServiceProvider(service=IngestModuleFactory.class)
public class FileExtMismatchDetectorModuleFactory extends IngestModuleFactoryAdapter {
    public static final String MODULE_NAME = "Extension Mismatch Detector";
    public static final String MODULE_DESCRIPTION = "Flags files that have a non-standard extension based on their file type.";
    
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }
    
    static String getModuleName() {
        return MODULE_NAME;        
    }
    
    @Override
    public String getModuleDescription() {
        return MODULE_DESCRIPTION;        
    }
    
    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();        
    }
    
    @Override
    public IngestModuleOptions getDefaultPerIngestJobOptions() {
        return new FileExtMismatchDetectorOptions();        
    }
    
    @Override
    public boolean providesIngestOptionsPanels() {
        return true;
    }
    
    @Override
    public IngestModuleOptionsPanel getIngestOptionsPanel(IngestModuleOptions ingestOptions) throws IngestModuleFactory.InvalidOptionsException {
        if (ingestOptions instanceof FileExtMismatchDetectorOptions) {
            throw new IngestModuleFactory.InvalidOptionsException("Ingest options must be of type " + FileExtMismatchDetectorOptions.class.getCanonicalName());            
        }
        
        if (!ingestOptions.areValid()) {
            throw new IngestModuleFactory.InvalidOptionsException("");
        }        
        
        FileExtMismatchSimpleConfigPanel ingestOptionsPanel = new FileExtMismatchSimpleConfigPanel((FileExtMismatchDetectorOptions)ingestOptions);  
        return ingestOptionsPanel;                
    }
        
    @Override
    public boolean providesResourcesConfigPanels() {
        return true;    
    }
    
    @Override
    public IngestModuleResourcesConfigPanel getResourcesConfigPanel() {
        FileExtMismatchConfigPanel globalOptionsPanel = new FileExtMismatchConfigPanel();        
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }    
    
    @Override
    public void saveGlobalOptionsFromPanel(JPanel globalOptionsPanel) throws IngestModuleFactory.InvalidOptionsException {
        if (!(globalOptionsPanel instanceof FileExtMismatchConfigPanel)) {
            throw new IllegalArgumentException("JPanel not a FileExtMismatchConfigPanel");
        }
        
        FileExtMismatchConfigPanel panel = (FileExtMismatchConfigPanel)globalOptionsPanel;
        panel.store();
    }
    
    @Override
    public boolean isFileIngestModuleFactory() {
        return true;            
    }
    
    @Override
    public FileIngestModule createFileIngestModule(Serializable ingestOptions) throws IngestModuleFactory.InvalidOptionsException {
        return new FileExtMismatchIngestModule();
    }
}
