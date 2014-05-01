/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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


package org.sleuthkit.autopsy.modules.externalresults;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.TskCoreException;


/**
 * Mechanism to import blackboard items, derived files, etc.
 * It is decoupled from the actual parsing/interfacing with external data.
 */
public class ExternalResultsUtility {
    private static final Logger logger = Logger.getLogger(ExternalResultsUtility.class.getName());
    private static final String EVENT_STRING = "External Results";

    /**
     * Tell the parser to get data, and then import that data into Autopsy.
     * @param parser An initialized instance of an ExternalResultsParser derivative
     * @param defaultDataSource Typically the current data source for the caller
     */
    public static void importResults(ExternalResultsParser parser, Content defaultDataSource) {
        // Create temporary data object
        ResultsData resultsData = parser.parse();
        
        // Use that data object to import the externally-generated information into the case
        generateDerivedFiles(resultsData, defaultDataSource);
        generateBlackboardItems(resultsData, defaultDataSource);
        generateReportRecords(resultsData, defaultDataSource);
    }

    /**
     * Add derived files. This should be called before generateBlackboardItems() in case 
     * any of the new blackboard artifacts refer to expected derived files.
     * @param resultsData
     * @param defaultDataSource 
     */
    private static void generateDerivedFiles(ResultsData resultsData, Content defaultDataSource) {
        try {    
            FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();            
            for (ResultsData.DerivedFileData derf : resultsData.getDerivedFiles()) {           
                String derp = derf.localPath;
                File fileObj = new File(derp);
                if (fileObj.exists()) {
                    String fileName = derp;
                    int charPos = derp.lastIndexOf(File.separator);
                    if (charPos > 0) {
                        fileName = derp.substring(charPos + 1);
                    }                   
                    
                    // Get a parent object for the new derived object
                    AbstractFile parentFile = null;
                    
                    if (!derf.parentPath.isEmpty()) {
                        parentFile = findFileInDatabase(derf.parentPath);                        
                    } else { //if no parent specified, try to use the root directory (//)                        
                        List<AbstractFile> files = Case.getCurrentCase().getSleuthkitCase().findFiles(defaultDataSource, "");
                        parentFile = files.get(0);
                    }
                    
                    if (parentFile != null) {
                        // Try to get a relative local path
                        String relPath = derp;                    
                        Path pathTo = Paths.get(derp);
                        if (pathTo.isAbsolute()) {
                            Path pathBase = Paths.get(Case.getCurrentCase().getCaseDirectory());
                            try {
                                Path pathRelative = pathBase.relativize(pathTo);
                                relPath = pathRelative.toString();
                            } catch(IllegalArgumentException ex) {
                                logger.log(Level.WARNING, "Derived file " + fileName + " path may be incorrect. The derived file object will still be added to the database.");
                            }
                        }
                        
                        // Make a new derived file object in the database
                        DerivedFile df = fileManager.addDerivedFile(fileName, relPath, fileObj.length(),
                                0, 0, 0, fileObj.lastModified(),
                                true, parentFile, "", EVENT_STRING, "", "");

                        if (df != null) {                 
                            IngestServices.getInstance().fireModuleContentEvent(new ModuleContentEvent(df));
                        }
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, ex.getLocalizedMessage());
        }            
    }
    
    /**
     * Create and add new blackboard artifacts, attributes, and types
     * @param resultsData
     * @param defaultDataSource 
     */
    private static void generateBlackboardItems(ResultsData resultsData, Content defaultDataSource) {
        for (ResultsData.ArtifactData art : resultsData.getArtifacts()) {           
            try {
                int bbArtTypeId;
                BlackboardArtifact.ARTIFACT_TYPE stdArtType = isStandardArtifactType(art.typeStr);
                if (stdArtType != null) {
                    bbArtTypeId = stdArtType.getTypeID();
                } else {
                    // assume it's user defined
                    bbArtTypeId = Case.getCurrentCase().getSleuthkitCase().addArtifactType(art.typeStr, art.typeStr);
                }                    

                Collection<BlackboardAttribute> bbAttributes = new ArrayList<>();
                for (ResultsData.AttributeData attr : art.attributes) {
                    int bbAttrTypeId;
                    BlackboardAttribute.ATTRIBUTE_TYPE stdAttrType = isStandardAttributeType(attr.typeStr);
                    if (stdAttrType != null) {
                        bbAttrTypeId = stdAttrType.getTypeID();
                    } else {
                        // assume it's user defined
                        bbAttrTypeId = Case.getCurrentCase().getSleuthkitCase().addAttrType(attr.typeStr, attr.typeStr);
                    }                    

                    // Add all attribute values
                    Set<String> valueTypes = attr.valueStr.keySet();
                    for (String valueType : valueTypes) {                        
                        String valueString = attr.valueStr.get(valueType);
                        BlackboardAttribute bbAttr = null;
                        switch (valueType) {
                            case "text": //NON-NLS
                                bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.source, attr.context, valueString);
                                break;
                            case "int32": //NON-NLS
                                int intValue = Integer.parseInt(valueString);
                                bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.source, attr.context, intValue);
                                break;
                            case "int64": //NON-NLS
                                long longValue = Long.parseLong(valueString);
                                bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.source, attr.context, longValue);
                                break;
                            case "double": //NON-NLS
                                double doubleValue = Double.parseDouble(valueString);
                                bbAttr = new BlackboardAttribute(bbAttrTypeId, attr.source, attr.context, doubleValue);
                                break;
                            default:
                                logger.log(Level.WARNING, "Ignoring invalid attribute value type " + valueType);
                                break;
                        }
                        if (bbAttr != null) {
                            bbAttributes.add(bbAttr);
                        }
                    }
                }        
                
                // Get associated file (if any) to use as the content obj to attach the artifact to
                Content currContent = null;
                if (art.files.size() > 0) {
                    currContent = findFileInDatabase(art.files.get(0).path);
                }

                // If no associated file, use current data source itself
                if (currContent == null) {
                    currContent = defaultDataSource;
                }

                BlackboardArtifact bbArt = currContent.newArtifact(bbArtTypeId);
                bbArt.addAttributes(bbAttributes);
                if (stdArtType != null) {
                    IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(EVENT_STRING, stdArtType)); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, ex.getLocalizedMessage());
            }            
        }
    }

    /**
     * Add report info to the database
     * @param resultsData
     * @param defaultDataSource 
     */
    private static void generateReportRecords(ResultsData resultsData, Content defaultDataSource) {
        try {   
            for (ResultsData.ReportData report : resultsData.getReports()) {
                String repp = report.localPath;
                File fileObj = new File(repp);
                if (fileObj.exists()) {
                    // Try to get a relative local path
                    String relPath = repp;                    
                    Path pathTo = Paths.get(repp);
                    if (pathTo.isAbsolute()) {
                        Path pathBase = Paths.get(Case.getCurrentCase().getCaseDirectory());
                        try {
                            Path pathRelative = pathBase.relativize(pathTo);
                            relPath = pathRelative.toString();
                        } catch(IllegalArgumentException ex) {
                            logger.log(Level.WARNING, "Report file " + repp + " path may be incorrect. The report record will still be added to the database.");
                        }
                    }                    

                    if (!relPath.isEmpty()) {
                        Case.getCurrentCase().getSleuthkitCase().addReport(relPath, report.displayName);
                    }
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, ex.getLocalizedMessage());
        }    
    }
    
    /**
     * 
     * @param artTypeStr
     * @return valid artifact type or null if the type is not a standard TSK one
     */
    private static BlackboardArtifact.ARTIFACT_TYPE isStandardArtifactType(String artTypeStr) {
        BlackboardArtifact.ARTIFACT_TYPE[] stdArts = BlackboardArtifact.ARTIFACT_TYPE.values();
        for (BlackboardArtifact.ARTIFACT_TYPE art : stdArts) {
            if (art.getLabel().equals(artTypeStr)) {
                return art;
            }
        }
        return null;
    }
    
   /**
     * 
     * @param attrTypeStr
     * @return valid attribute type or null if the type is not a standard TSK one
     */
    private static BlackboardAttribute.ATTRIBUTE_TYPE isStandardAttributeType(String attrTypeStr) {
        BlackboardAttribute.ATTRIBUTE_TYPE[] stdAttrs = BlackboardAttribute.ATTRIBUTE_TYPE.values();
        for (BlackboardAttribute.ATTRIBUTE_TYPE attr : stdAttrs) {
            if (attr.getLabel().equals(attrTypeStr)) {
                return attr;
            }
        }
        return null;
    }    

    /**
     * util function
     * @param filePath full path including file or dir name
     * @return AbstractFile
     * @throws TskCoreException 
     */
    private static AbstractFile findFileInDatabase(String filePath) throws TskCoreException {
        AbstractFile abstractFile = null;
        String fileName = filePath;
        String parentPath = "";              
        int charPos = filePath.lastIndexOf("/");
        if (charPos >= 0) {
            fileName = filePath.substring(charPos + 1);
            parentPath = filePath.substring(0, charPos + 1);
        }
        String whereQuery = "name='" + fileName + "' AND parent_path='" + parentPath + "'"; //NON-NLS
        List<AbstractFile> files = Case.getCurrentCase().getSleuthkitCase().findAllFilesWhere(whereQuery);
        if (files.size() > 0) {
            abstractFile = files.get(0);
            if (files.size() > 1) {
                logger.log(Level.WARNING, "Ignoring extra files found for path " + filePath);
            }                         
        }
        return abstractFile;
    }
}
