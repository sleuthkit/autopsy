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
package org.sleuthkit.autopsy.modules.databaseselector;

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
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector.FileTypeDetectorInitException;
import org.sleuthkit.autopsy.sqlitereader.SQLiteReader;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Parses database files and marks them as having interesting records (emails,
 * phone numbers, mac addresses, gps coordinates).
 */
public class DatabaseSelectorIngestModule extends FileIngestModuleAdapter {

    private static final String SUPPORTED_MIME_TYPE = "application/x-sqlite3";
    private static final String MODULE_NAME = DatabaseSelectorIngestModuleFactory.getModuleName();
    
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(
            DatabaseSelectorIngestModuleFactory.getModuleName());
    private FileTypeDetector fileTypeDetector;
    private CellTypeDetector cellTypeDetector;
    
    private Blackboard blackboard;
    private String localDiskPath;
    
    
    @NbBundle.Messages({
        "DatabaseSelectorIngestModule.CannotRunFileTypeDetection=Unable to initialize file type detection.",
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        try {
            fileTypeDetector = new FileTypeDetector();
            cellTypeDetector = new CellTypeDetector();
        } catch (FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.DatabaseSelectorIngestModule_CannotRunFileTypeDetection(), ex);
        }
    }
    
    @Override
    public ProcessResult process(AbstractFile file) {
        if(getBlackboardInstanceFromServices().equals(ProcessResult.ERROR)) {
            return ProcessResult.ERROR;
        }
        
        //Qualify the MIMEType
        String dataSourceMimeType = fileTypeDetector.getMIMEType(file);
        if(SUPPORTED_MIME_TYPE.equals(dataSourceMimeType)) {
            if(createLocalDiskPathFromCurrentCase(file).equals(ProcessResult.ERROR)) {
                return ProcessResult.ERROR;
            }
            
            try (SQLiteReader sqliteReader = new SQLiteReader(file, localDiskPath)){
                try {
                    Set<CellType> databaseCellTypes = getCellTypesInDatabase(sqliteReader);
                    //No interesting hits, don't flag this database, skip artifact creation.
                    if(!databaseCellTypes.isEmpty()) {
                        String cellTypesComment = createCellTypeCommentString(databaseCellTypes);
                        try {
                            BlackboardArtifact artifact = createArtifactGivenCellTypes(
                                file, cellTypesComment);   
                            indexArtifactAndFireModuleDataEvent(artifact);
                        } catch (TskCoreException ex) {
                            logger.log(Level.SEVERE, "Error creating blackboard artifact", ex); //NON-NLS
                        } 
                    }
                } catch(SQLException ex) {
                    logger.log(Level.WARNING, "Error attempting to read sqlite "
                            + "file in DatabaseSelectorIngestModule", ex);
                }    
            } catch (ClassNotFoundException | SQLException | IOException | 
                    NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Cannot initialize sqliteReader class "
                        + "in DatabaseSelectorIngestModule", ex);
                return ProcessResult.ERROR;
            }
        }
        
        //Whether we successfully read the sqlite database or determined the mime
        //type is not supported, the process is OK.
        return ProcessResult.OK;
    }
    
    /**
     * Get a pointer to the current case blackboard for indexing of artifacts
     * 
     * @return ProcessResult indicating a success or failure in getting current case
     */
    private ProcessResult getBlackboardInstanceFromServices() {
        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }
        
        return ProcessResult.OK;      
    }
    
    /**
     * Generates a local disk path for abstract file contents to be copied.
     * All database sources must be copied to local disk to be opened by 
     * SQLiteReader
     * 
     * @param file The database abstract file
     * @return ProcessResult indicating a success or failure in creating a disk path
     */
    private ProcessResult createLocalDiskPathFromCurrentCase(AbstractFile file) {
        try {
            localDiskPath = Case.getCurrentCaseThrows()
                    .getTempDirectory() + File.separator + file.getName();
        } catch (NoCurrentCaseException ex) {
            // TODO -- personal note log about current case being closed or
             //current case not existing.
            Exceptions.printStackTrace(ex);
            return ProcessResult.ERROR;
        }
        
        return ProcessResult.OK;
    }
    
    /**
     * Creates and populates a set of all CellTypes in the sqlite database
     * 
     * @param sqliteReader Reader currently connected to database file
     * @return A Set of distinct CellTypes
     * @throws SQLException Caught during attempting to read sqlite database
     */
    private Set<CellType> getCellTypesInDatabase(SQLiteReader sqliteReader) throws SQLException {
        Map<String, String> tables = sqliteReader.getTableSchemas();    
        Set<CellType> aggregateCellTypes = new TreeSet<>();

        //Aggregate cell types from all tables
        for(String tableName : tables.keySet()) {
            aggregateCellTypes.addAll(getCellTypesInTable(sqliteReader, tableName));
        }
        
        return aggregateCellTypes;
    }
    
    /**
     * Creates and populates a set of all CellTypes in a sqlite table
     * 
     * @param sqliteReader Reader currently connected to database file
     * @param tableName database table to be opened and read
     * @return Set of all unique cell types in table
     * @throws SQLException Caught during attempting to read sqlite database
     */
    private Set<CellType> getCellTypesInTable(SQLiteReader sqliteReader, 
            String tableName) throws SQLException {
        
        Set<CellType> tableCellTypes = new TreeSet<>();
        List<Map<String, Object>> tableValues = sqliteReader.getRowsFromTable(tableName);
        
        //Aggregate cell types from all table rows
        tableValues.forEach((row) -> {
            tableCellTypes.addAll(getCellTypesInRow(row));
        });
        return tableCellTypes;
    }
    
    /**
     * Creates and populates a set of all CellTypes in a table row
     * 
     * @param row Table row is represented as a column-value map
     * @return 
     */
    private Set<CellType> getCellTypesInRow(Map<String, Object> row) {
        Set<CellType> rowCellTypes = new TreeSet<>();
        
        //Aggregate cell types from a row
        row.values().forEach((Object cell) -> {
            if(cell instanceof String) {
                CellType type = cellTypeDetector.getType((String) cell);
                if(!type.equals(CellType.NOT_INTERESTING)) {
                    rowCellTypes.add(type);
                }
            }
        });
        return rowCellTypes;
    }
    
    /**
     * Creates a comma seperated string of all the cell types found in a database
     * file. Used as the comment string for the blackboard artifact.
     * 
     * @param databaseCellTypes The set of all database cell types detected
     * @return 
     */
    private String createCellTypeCommentString(Set<CellType> databaseCellTypes) {
        return databaseCellTypes.toString().replace("]", "").replace("[", "");
    }
    
    /**
     * Initializes a new interesting file hit artifact and provides name and 
     * comment attributes
     * 
     * @param file The database abstract file
     * @param cellTypesComment String of all the cell types found in a database
     * file
     * @return Interesting file hit artifact
     * @throws TskCoreException Thrown if the abstract file cannot create a blackboard
     * artifact
     */
    @NbBundle.Messages({
        "DatabaseSelectorIngestModule.FlagDatabases.setName=Selectors identified"
    })
    private BlackboardArtifact createArtifactGivenCellTypes(AbstractFile file, 
            String cellTypesComment) throws TskCoreException {
        BlackboardArtifact artifact = file.newArtifact(
                BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        
        BlackboardAttribute setNameAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, 
                Bundle.DatabaseSelectorIngestModule_FlagDatabases_setName());
        artifact.addAttribute(setNameAttribute);

        BlackboardAttribute commentAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, 
                cellTypesComment);
        artifact.addAttribute(commentAttribute);
        
        return artifact;
    }
    
    /**
     * Pass the artifact to blackboard for indexing and fire module data event
     * in the IngestServices
     * 
     * @param artifact Blackboard artifact created for the interesting file hit
     */
    @NbBundle.Messages({
        "DatabaseSelectorIngestModule.indexError.message="
                + "Failed to index interesting file hit artifact for keyword search."
    })
    private void indexArtifactAndFireModuleDataEvent(BlackboardArtifact artifact) {
        try {
            // index the artifact for keyword search
            blackboard.indexArtifact(artifact);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, 
                    "Unable to index blackboard artifact " + //NON-NLS 
                            artifact.getArtifactID(), ex);
            MessageNotifyUtil.Notify.error(
                    Bundle.DatabaseSelectorIngestModule_indexError_message(), 
                    artifact.getDisplayName());
        }

        services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, 
            BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, 
            Collections.singletonList(artifact)));
    }
}