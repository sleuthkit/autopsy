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
import org.sleuthkit.autopsy.modules.fileselector.CellTypeDetector.CellType;
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

    private static final String SUPPORTED_MIME_TYPE = "application/x-sqlite3"; //NON-NLS
    private static final String MODULE_NAME = FileSelectorIngestModuleFactory.getModuleName();
    
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(FileSelectorIngestModule.class.getName());
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
            Collection<CellType> notableCellTypes = selectInterestingCellTypesFromFile(file, sqliteReader);
            //No interesting types found, no artifact to create
            if(notableCellTypes.isEmpty()) {
                return ProcessResult.OK;
            }
            
            try {
                BlackboardArtifact artifact = createArtifact(file, notableCellTypes);   
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
     * Creates and populates a collection of all the interesting cell types found in 
     * the database.
     * 
     * @param sqliteReader Reader instance currently connected to local file contents
     * @return A collection of interesting cell types 
     */
    private Collection<CellType> selectInterestingCellTypesFromFile(AbstractFile file, SQLiteReader sqliteReader) {  
        Collection<CellType> interestingCellTypes = new TreeSet<>();
        
        Map<String, String> tables;
        try {
            //Table name to table schema mapping
            tables = sqliteReader.getTableSchemas();    
        } catch (SQLException ex) {
            logger.log(Level.WARNING, String.format("Error attempting to get tables from sqlite" //NON-NLS
                                + "file [%s].", //NON-NLS
                                file.getName()), ex);
            //Unable to read anything, return empty collection.
            return interestingCellTypes;
        }
        
        //Parse every table and collect all interesting cell types
        for(String tableName : tables.keySet()) {
            try {
                getNotableCellTypesFromTable(sqliteReader, tableName, interestingCellTypes);
            } catch (SQLException ex) {
                logger.log(Level.WARNING, 
                        String.format("Error attempting to read sqlite table [%s]" //NON-NLS
                                + " for file [%s].", //NON-NLS
                                tableName, file.getName()), ex);
            }
        }
        
        return interestingCellTypes;
    }
    
    /**
     * Reads every cell in the table and adds any interesting hits to the collection
     * of cell types.
     * 
     * @param sqliteReader Reader instance currently connected to local file contents
     * @param tableName Table name to be read from
     * @param interestingCellTypes Collection that this function will add interesting hits to
     * @throws SQLException An error attempting to get rows from table
     */
    private void getNotableCellTypesFromTable(SQLiteReader sqliteReader, 
            String tableName, Collection<CellType> interestingCellTypes) throws SQLException {
        //row is represented as a column name to value mapping
        List<Map<String, Object>> rowsInTable = sqliteReader.getRowsFromTable(tableName);
        for(Map<String, Object> row : rowsInTable) {
            //Only interested in row values, not the column name
            row.values().forEach(cell -> {
                CellType type = CellTypeDetector.getType(cell);
                if(isAnInterestingCellType(type)) {
                    interestingCellTypes.add(type);
                }
            });
        }
    }
    
    /**
     * Boolean function purely for readability. Statement below is read as:
     * if type is not not interesting -> isAnInterestingCellType.
     * 
     * @param type
     * @return 
     */
    private boolean isAnInterestingCellType(CellType type) {
        return !type.equals(CellType.NOT_INTERESTING);
    }
    
    /**
     * Initializes a new interesting file hit artifact and provides name and 
     * comment attributes
     * 
     * @param file The database abstract file
     * @param cellTypesInFile Collection of notable cell types found during reading
     * file
     * @return Interesting file hit artifact
     * @throws TskCoreException Thrown if the abstract file cannot create a blackboard
     * artifact
     */
    @NbBundle.Messages({
        "FileSelectorIngestModule.setName=Selectors identified"
    })
    private BlackboardArtifact createArtifact(AbstractFile file, 
            Collection<CellType> cellTypesInFile) throws TskCoreException {
        BlackboardArtifact artifact = file.newArtifact(
                BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        
        BlackboardAttribute setNameAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, 
                Bundle.FileSelectorIngestModule_setName());
        artifact.addAttribute(setNameAttribute);

        String cellTypesComment = cellTypesToString(cellTypesInFile);
        BlackboardAttribute commentAttribute = new BlackboardAttribute(
                BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, 
                cellTypesComment);
        artifact.addAttribute(commentAttribute);
        
        return artifact;
    }
    
    /**
     * Creates a comma seperated string of all the cell types found in a database
     * file. Used as the comment string for the blackboard artifact. TreeSet is 
     * used to ensure that CellTypes appear in the same order as the enum.
     * 
     * @param cellTypesInFile Collection of interesting cell types found during reading
     * @return 
     */
    private String cellTypesToString(Collection<CellType> cellTypesInFile) {
        return cellTypesInFile.toString().replace("]", "").replace("[", ""); //NON-NLS
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