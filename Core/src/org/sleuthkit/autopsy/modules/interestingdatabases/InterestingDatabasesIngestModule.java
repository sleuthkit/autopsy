/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.interestingdatabases;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.sqlitereader.SQLiteReader;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dsmyda
 */
public class InterestingDatabasesIngestModule extends FileIngestModuleAdapter {

    private static final String SUPPORTED_MIME_TYPE = "application/x-sqlite3";
    
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(
            InterestingDatabasesIngestModuleFactory.getModuleName());
    private FileTypeDetector fileTypeDetector;
    
    private String localDiskPath;
    private SQLiteReader sqliteReader;
    
    /**
     * 
     * @param context
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException 
     */
    @NbBundle.Messages({
        "CannotRunFileTypeDetection=Unable to initialize file type detection.",
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }
    }
    
    /**
     * 
     * @param file
     * @return 
     */
    @Override
    public ProcessResult process(AbstractFile file) {
        String dataSourceMimeType = fileTypeDetector.getMIMEType(file);
        if(SUPPORTED_MIME_TYPE.equals(dataSourceMimeType)) {
            
            if(successfulDependencyInitialization(file)) {
                
            } else {
                return ProcessResult.ERROR;
            }
        }
        
        return ProcessResult.OK;
    }
    
    private boolean successfulDependencyInitialization(AbstractFile file) {
        return createLocalDiskPath(file) == ProcessResult.OK && 
                initalizeSQLiteReader(file) == ProcessResult.OK;
    }
    
    private ProcessResult createLocalDiskPath(AbstractFile file) {
        try {
            localDiskPath = Case.getCurrentCaseThrows()
                    .getTempDirectory() + File.separator + file.getName();
        } catch (NoCurrentCaseException ex) {
            // TODO -- personal note log about current case being closed or
             //current case not existing.
             return ProcessResult.ERROR;
        }
        
        return ProcessResult.OK;
    }
    
    private ProcessResult initalizeSQLiteReader(AbstractFile file) {
        try {
            sqliteReader = new SQLiteReader(file, localDiskPath);
            return ProcessResult.OK;
        } catch (ClassNotFoundException ex) {
            //TODO add logging messages for this.
        } catch (SQLException ex) {
            //TODO add logging messages for this.
        } catch (IOException ex) {
            //TODO add logging messages for this.
        } catch (NoCurrentCaseException ex) {
            //TODO add logging messages for this.
        } catch (TskCoreException ex) {
            //TODO add logging messages for this.
        }
        return ProcessResult.ERROR;
    }
    
    /**
     * 
     */
    @Override
    public void shutDown() {
        
    }

    private void createArtifact() {
        
    }
}
