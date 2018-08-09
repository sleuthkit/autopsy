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
    
    
    @NbBundle.Messages({
        "DatabaseSelectorIngestModule.CannotRunFileTypeDetection="
                + "Unable to initialize file type detection.",
        "DatabaseSelectorIngestModule.CannotGetBlackboard="
                + "Exception while attempting to get Blackboard from current case."
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.DatabaseSelectorIngestModule_CannotRunFileTypeDetection(), ex);
        }
        
        cellTypeDetector = new CellTypeDetector();
        
        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.DatabaseSelectorIngestModule_CannotGetBlackboard(), ex);
        }          
    }
    
    @Override
    public ProcessResult process(AbstractFile file) {
        //Qualify the MIMEType, only process sqlite files.
        String dataSourceMimeType = fileTypeDetector.getMIMEType(file);
        if(!dataSourceMimeType.equals(SUPPORTED_MIME_TYPE)) {
            return ProcessResult.OK;
        }

        try (SQLiteReader sqliteReader = new SQLiteReader(file, createLocalDiskPath(file))){
            Set<CellType> databaseCellTypes = getCellTypesInDatabase(file, sqliteReader);
            
            //No interesting hits, don't flag this database, skip artifact creation.
            if(!databaseCellTypes.isEmpty()) {
                try {
                    BlackboardArtifact artifact = createArtifact(file, databaseCellTypes);   
                    indexArtifactAndFireModuleDataEvent(artifact);
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error creating blackboard artifact", ex); //NON-NLS
                } 
            } 
        } catch (ClassNotFoundException | SQLException | IOException | 
                NoCurrentCaseException | TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Cannot initialize sqliteReader class " //NON-NLS
                    + "for file [%s].", file.getName()), ex); //NON-NLS
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
     * @return Valid local path for copying
     * @throws NoCurrentCaseException if the current case has been closed.
     */
    private String createLocalDiskPath(AbstractFile file) throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getTempDirectory() + 
                File.separator + file.getName();
    }
    
    /**
     * Creates and populates a set of all CellTypes in the sqlite database
     * 
     * @param sqliteReader Reader instance currently connected to database file
     * @return A Set of distinct CellTypes
     */
    private Set<CellType> getCellTypesInDatabase(AbstractFile file, SQLiteReader sqliteReader) {  
        Set<CellType> aggregateCellTypes = new TreeSet<>();
        
        Map<String, String> tables;
        try {
            tables = sqliteReader.getTableSchemas();    
        } catch (SQLException ex) {
            logger.log(Level.WARNING, String.format("Error attempting to get tables from sqlite" //NON-NLS
                                + "file [%s].", //NON-NLS
                                file.getName()), ex);
            //Unable to get any cellTypes, return empty set to be ignored.
            return aggregateCellTypes;
        }
        
        //Aggregate cell types from all tables
        for(String tableName : tables.keySet()) {
            try {
                aggregateCellTypes.addAll(getCellTypesInTable(sqliteReader, tableName));
            } catch (SQLException ex) {
                logger.log(Level.WARNING, 
                        String.format("Error attempting to read sqlite table [%s]" //NON-NLS
                                + " for file [%s].", //NON-NLS
                                tableName, file.getName()), ex);
            }
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
            CellType type = cellTypeDetector.getType(cell);
            if(!type.equals(CellType.NOT_INTERESTING)) {
                rowCellTypes.add(type);
            }
        });
        return rowCellTypes;
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
    private BlackboardArtifact createArtifact(AbstractFile file, 
            Set<CellType> databaseCellTypes) throws TskCoreException {
        BlackboardArtifact artifact = file.newArtifact(
                BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        
        BlackboardAttribute setNameAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, 
                Bundle.DatabaseSelectorIngestModule_FlagDatabases_setName());
        artifact.addAttribute(setNameAttribute);

        String cellTypesComment = createCellTypeCommentString(databaseCellTypes);
        BlackboardAttribute commentAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, 
                cellTypesComment);
        artifact.addAttribute(commentAttribute);
        
        return artifact;
    }
    
    /**
     * Creates a comma seperated string of all the cell types found in a database
     * file. Used as the comment string for the blackboard artifact.
     * 
     * @param databaseCellTypes The set of all database cell types detected
     * @return 
     */
    private String createCellTypeCommentString(Set<CellType> databaseCellTypes) {
        return databaseCellTypes.toString().replace("]", "").replace("[", ""); //NON-NLS
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