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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;


/**
 *
 */
public class ExternalResultsUtility {
    private static final Logger logger = Logger.getLogger(ExternalResultsUtility.class.getName());

    public static void importResults(ExternalResultsParser parser, Content defaultDataSource) {
        // Create temporary data object
        ResultsData resultsData = parser.parse();
        
        // Use that data object to import the externally-generated information into the case
        generateBlackboardItems(resultsData, defaultDataSource);
    }
    
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
                    String filePath = art.files.get(0).path;
                    String fileName = filePath;
                    String parentPath = "";              
                    int charPos = filePath.lastIndexOf("/");
                    if (charPos > 0) {
                        fileName = filePath.substring(charPos + 1);
                        parentPath = filePath.substring(0, charPos + 1);
                    }
                    String whereQuery = "name='" + fileName + "' AND parent_path='" + parentPath + "'"; //NON-NLS
                    List<AbstractFile> files = Case.getCurrentCase().getSleuthkitCase().findAllFilesWhere(whereQuery);
                    if (files.size() > 0) {
                        currContent = files.get(0);
                        if (files.size() > 1) {
                            logger.log(Level.WARNING, "Ignoring extra files found for path " + filePath);
                        }                         
                    }
                }

                // If no associated file, use current data source itself
                if (currContent == null) {
                    currContent = defaultDataSource;
                }

                BlackboardArtifact bbArt = currContent.newArtifact(bbArtTypeId);
                bbArt.addAttributes(bbAttributes);
                if (stdArtType != null) {
                    IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent("External Results", stdArtType)); //NON-NLS
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, ex.getLocalizedMessage());
            }            
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
    
}
