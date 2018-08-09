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
package org.sleuthkit.autopsy.modules.fileselector;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import org.sleuthkit.autopsy.modules.fileselector.DataElementTypeDetector.DataElementType;
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
public class FileSelectorIngestModule extends FileIngestModuleAdapter {

    private static final String SUPPORTED_MIME_TYPE = "application/x-sqlite3";
    private static final String MODULE_NAME = FileSelectorIngestModuleFactory.getModuleName();
    
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(
            FileSelectorIngestModuleFactory.getModuleName());
    private FileTypeDetector fileTypeDetector;
    
    private Blackboard blackboard;
    
    
    @NbBundle.Messages({
        "FileSelectorIngestModule.CannotRunFileTypeDetection="
                + "Unable to initialize file type detection.",
        "FileSelectorIngestModule.CannotGetBlackboard="
                + "Exception while attempting to get Blackboard from current case."
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.FileSelectorIngestModule_CannotRunFileTypeDetection(), ex);
        }
        
        try {
            blackboard = Case.getCurrentCaseThrows().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.FileSelectorIngestModule_CannotGetBlackboard(), ex);
        }          
    }
    
    @Override
    public ProcessResult process(AbstractFile file) {
        //Qualify the MIMEType, only process sqlite files.
        String fileMimeType = fileTypeDetector.getMIMEType(file);
        if(!fileMimeType.equals(SUPPORTED_MIME_TYPE)) {
            return ProcessResult.OK;
        }

        try (SQLiteReader sqliteReader = new SQLiteReader(file, createLocalDiskPath(file))){
            
            Collection<DataElementType> dataElementTypesInFile = readFileAndFindTypes(file, sqliteReader);
            //No interesting types found, no artifact to create
            if(dataElementTypesInFile.isEmpty()) {
                return ProcessResult.OK;
            }
            
            try {
                BlackboardArtifact artifact = createArtifact(file, dataElementTypesInFile);   
                indexArtifactAndFireModuleDataEvent(artifact);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error creating blackboard artifact", ex); //NON-NLS
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
     * Creates and populates a collection of all data element types in the file
     * 
     * @param sqliteReader Reader instance currently connected to local file contents
     * @return A collection of data element types
     */
    private Collection<DataElementType> readFileAndFindTypes(AbstractFile file, SQLiteReader sqliteReader) {  
        Collection<DataElementType> currentTypesFound = new TreeSet<>();
        
        Map<String, String> tables;
        try {
            tables = sqliteReader.getTableSchemas();    
        } catch (SQLException ex) {
            logger.log(Level.WARNING, String.format("Error attempting to get tables from sqlite" //NON-NLS
                                + "file [%s].", //NON-NLS
                                file.getName()), ex);
            //Unable to read anything, return empty collection.
            return currentTypesFound;
        }
        
        //Aggregate cell types from all tables
        for(String tableName : tables.keySet()) {
            try {
                Collection<DataElementType> typesFoundInTable = readTableAndFindTypes(sqliteReader, tableName);
                currentTypesFound.addAll(typesFoundInTable);
            } catch (SQLException ex) {
                logger.log(Level.WARNING, 
                        String.format("Error attempting to read sqlite table [%s]" //NON-NLS
                                + " for file [%s].", //NON-NLS
                                tableName, file.getName()), ex);
            }
        }
        
        return currentTypesFound;
    }
    
    /**
     * Creates and populates a collection of all data element types in a sqlite
     * database table
     * 
     * @param sqliteReader Reader currently connected to local file contents
     * @param tableName database table to be opened and read
     * @return collection of all types in table
     * @throws SQLException Caught during attempting to read sqlite database
     */
    private Collection<DataElementType> readTableAndFindTypes(SQLiteReader sqliteReader, 
            String tableName) throws SQLException {
        
        Collection<DataElementType> typesFoundReadingTable = new TreeSet<>();
        List<Map<String, Object>> tableValues = sqliteReader.getRowsFromTable(tableName);
        
        //Aggregate cell types from all rows
        tableValues.forEach((row) -> {
            Collection<DataElementType> typesFoundInRow = readRowAndFindTypes(row);
            typesFoundReadingTable.addAll(typesFoundInRow);
        });
        return typesFoundReadingTable;
    }
    
    /**
     * Creates and populates a collection of all data element types in a table row
     * 
     * @param row Table row is represented as a column-value map
     * @return 
     */
    private Collection<DataElementType> readRowAndFindTypes(Map<String, Object> row) {
        Collection<DataElementType> typesFoundReadingRow = new TreeSet<>();
        
        //Aggregate cell types from a row
        row.values().forEach((Object dataElement) -> {
            DataElementType type = DataElementTypeDetector.getType(dataElement);
            if(isAnInterestingDataType(type)) {
                typesFoundReadingRow.add(type);
            }
        });
        return typesFoundReadingRow;
    }
    
    /**
     * Boolean function purely for readability. Statement below is read as:
     * if type is not not interesting -> isAnInterestingDataType.
     * 
     * @param type
     * @return 
     */
    private boolean isAnInterestingDataType(DataElementType type) {
        return !type.equals(DataElementType.NOT_INTERESTING);
    }
    
    /**
     * Initializes a new interesting file hit artifact and provides name and 
     * comment attributes
     * 
     * @param file The database abstract file
     * @param dataElementTypesInFile Collection of data types found during reading
     * file
     * @return Interesting file hit artifact
     * @throws TskCoreException Thrown if the abstract file cannot create a blackboard
     * artifact
     */
    @NbBundle.Messages({
        "FileSelectorIngestModule.setName=Selectors identified"
    })
    private BlackboardArtifact createArtifact(AbstractFile file, 
            Collection<DataElementType> dataElementTypesInFile) throws TskCoreException {
        BlackboardArtifact artifact = file.newArtifact(
                BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        
        BlackboardAttribute setNameAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, 
                Bundle.FileSelectorIngestModule_setName());
        artifact.addAttribute(setNameAttribute);

        String dateTypesComment = dataElementTypesToString(dataElementTypesInFile);
        BlackboardAttribute commentAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, 
                dateTypesComment);
        artifact.addAttribute(commentAttribute);
        
        return artifact;
    }
    
    /**
     * Creates a comma seperated string of all the cell types found in a database
     * file. Used as the comment string for the blackboard artifact. TreeSet is 
     * used to ensure that CellTypes appear in the same order as the enum.
     * 
     * @param dataElementTypesInFile Collection of data types found during reading
     * @return 
     */
    private String dataElementTypesToString(Collection<DataElementType> dataElementTypesInFile) {
        return dataElementTypesInFile.toString().replace("]", "").replace("[", ""); //NON-NLS
    }
    
    /**
     * Pass the artifact to blackboard for indexing and fire module data event
     * in the IngestServices
     * 
     * @param artifact Blackboard artifact created for the interesting file hit
     */
    @NbBundle.Messages({
        "FileSelectorIngestModule.indexError.message="
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
                    Bundle.FileSelectorIngestModule_indexError_message(), 
                    artifact.getDisplayName());
        }

        services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, 
            BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, 
            Collections.singletonList(artifact)));
    }
}