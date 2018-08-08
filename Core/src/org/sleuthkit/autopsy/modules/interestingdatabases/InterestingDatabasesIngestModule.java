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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.autopsy.sqlitereader.SQLiteReader;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dsmyda
 */
public class InterestingDatabasesIngestModule extends FileIngestModuleAdapter {

    private static final String SUPPORTED_MIME_TYPE = "application/x-sqlite3";
    private static final String MODULE_NAME = InterestingDatabasesIngestModuleFactory.getModuleName();
    
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(
            InterestingDatabasesIngestModuleFactory.getModuleName());
    private FileTypeDetector fileTypeDetector;
    private CellTypeDetector cellTypeDetector;
    
    private Blackboard blackboard;
    
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
            cellTypeDetector = new CellTypeDetector();
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
    @NbBundle.Messages({
        "InterestingDatabasesIngestModule.indexError.message=Failed to index interesting artifact hit for keyword search."
    })
    public ProcessResult process(AbstractFile file) {
        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();        
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        
        String dataSourceMimeType = fileTypeDetector.getMIMEType(file);
        if(SUPPORTED_MIME_TYPE.equals(dataSourceMimeType)) {
            
            String localDiskPath;
            try {
                localDiskPath = Case.getCurrentCaseThrows()
                        .getTempDirectory() + File.separator + file.getName();
            } catch (NoCurrentCaseException ex) {
                // TODO -- personal note log about current case being closed or
                 //current case not existing.
                Exceptions.printStackTrace(ex);
                return ProcessResult.ERROR;
            }
            
            
            try (SQLiteReader sqliteReader = 
                    new SQLiteReader(file, localDiskPath)){
                
                Set<CellType> aggregatedCellTypes = cellTypesInDatabase(sqliteReader);
                String cellTypesMessage = aggregatedCellTypes.toString()
                        .replace("]", "")
                        .replace("[", "");
                
                try {
                    BlackboardArtifact artifact = createArtifactGivenCellTypes(
                        file, cellTypesMessage);   
                    
                    try {
                        // index the artifact for keyword search
                        blackboard.indexArtifact(artifact);
                    } catch (Blackboard.BlackboardException ex) {
                        logger.log(Level.SEVERE, 
                                "Unable to index blackboard artifact " + //NON-NLS 
                                        artifact.getArtifactID(), ex);
                        MessageNotifyUtil.Notify.error(
                                Bundle.InterestingDatabasesIngestModule_indexError_message(), 
                                artifact.getDisplayName());
                    }
                    
                    services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, 
                        BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, 
                        Collections.singletonList(artifact)));
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error posting to the blackboard", ex); //NOI18N NON-NLS
                }
                
            } catch (SQLException ex) {
                //Could be thrown in init or close.. need to fix.
                Exceptions.printStackTrace(ex);
            } catch (ClassNotFoundException ex) {
                Exceptions.printStackTrace(ex);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } catch (NoCurrentCaseException ex) {
                Exceptions.printStackTrace(ex);
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
        return ProcessResult.OK;
    }
    
    /**
     * 
     * @param sqliteReader
     * @return
     * @throws SQLException 
     */
    private Set<CellType> cellTypesInDatabase(SQLiteReader sqliteReader) throws SQLException {
        Map<String, String> tables = sqliteReader.getTableSchemas();    
        Set<CellType> aggregateCellTypes = new TreeSet<>();

        //Aggregate all cell types from each table
        for(String tableName : tables.keySet()) {
            addCellTypesInTable(sqliteReader, tableName, aggregateCellTypes);
        }
        
        return aggregateCellTypes;
    }
    
    /**
     * 
     * @param sqliteReader
     * @param table
     * @return 
     */
    private void addCellTypesInTable(SQLiteReader sqliteReader, String tableName, 
            Set<CellType> aggregateCellTypes) throws SQLException {

        List<Map<String, Object>> tableValues = sqliteReader.getRowsFromTable(tableName);
        tableValues.forEach((row) -> {
            addCellTypeInRow(row, aggregateCellTypes);
        });
    }
    
    /**
     * 
     * @param row
     * @return 
     */
    private void addCellTypeInRow(Map<String, Object> row, 
            Set<CellType> aggregateCellTypes) {
        row.values().forEach((Object cell) -> {
            if(cell instanceof String) {
                aggregateCellTypes.add(cellTypeDetector.getType( (String) cell));
            }
        });
    }
    
    /**
     * 
     * @param type 
     */
    @NbBundle.Messages({
        "InterestingDatabasesIngestModule.FlagDatabases.setName=Selectors identified"
    })
    private BlackboardArtifact createArtifactGivenCellTypes(AbstractFile file, 
            String cellTypesMessage) throws TskCoreException {
        BlackboardArtifact artifact = file.newArtifact(
                BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        
        BlackboardAttribute setNameAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, 
                Bundle.InterestingDatabasesIngestModule_FlagDatabases_setName());
        artifact.addAttribute(setNameAttribute);

        BlackboardAttribute commentAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, 
                cellTypesMessage);
        artifact.addAttribute(commentAttribute);
        
        return artifact;
    }
    
    /**
     * 
     */
    @Override
    public void shutDown() {
        
    }
}
