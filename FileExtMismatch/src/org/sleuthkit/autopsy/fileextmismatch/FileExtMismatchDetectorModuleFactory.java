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
import org.sleuthkit.autopsy.ingest.AbstractIngestModuleFactory;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;

/**
 * An factory that creates file ingest modules that do hash database lookups.
 */
@ServiceProvider(service=IngestModuleFactory.class)
public class FileExtMismatchDetectorModuleFactory extends AbstractIngestModuleFactory {
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
    public Serializable getDefaultIngestOptions() {
        return new FileExtMismatchIngestOptions();        
    }
    
    @Override
    public boolean providesIngestOptionsPanels() {
        return true;
    }
    
    @Override
    public JPanel getIngestOptionsPanel(Serializable ingestOptions) throws IngestModuleFactory.InvalidOptionsException {
        if (!ingestOptionsAreValid(ingestOptions)) {
            throw new IngestModuleFactory.InvalidOptionsException();
        }        
        FileExtMismatchSimpleConfigPanel ingestOptionsPanel = new FileExtMismatchSimpleConfigPanel(ingestOptions);  
        return ingestOptionsPanel;                
    }
    
    @Override
    public Serializable getIngestOptionsFromPanel(JPanel ingestOptionsPanel) {
        if (!(ingestOptionsPanel instanceof FileExtMismatchSimpleConfigPanel)) {
            throw new IllegalArgumentException("JPanel not a FileExtMismatchConfigPanel");
        }

        FileExtMismatchSimpleConfigPanel panel = (FileExtMismatchSimpleConfigPanel)ingestOptionsPanel;
//        panel.store(); RJCTODO
                
        return new FileExtMismatchIngestOptions(); // RJCTODO
    }
    
    @Override
    public boolean ingestOptionsAreValid(Serializable ingestOptions) {
        return (ingestOptions instanceof FileExtMismatchIngestOptions);
    }
    
    @Override
    public boolean providesGlobalOptionsPanels() {
        return true;    
    }
    
    @Override
    public JPanel getGlobalOptionsPanel() {
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
