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
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;


/**
 *
 */
public class ExternalResultsUtility {
    private static final Logger logger = Logger.getLogger(ExternalResultsUtility.class.getName());

    static public void importResults(ExternalResultsParser parser, Content defaultDataSource) {
        // Create temporary data object
        ResultsData resultsData = parser.parse();
        
        // Use that data object to import the externally-generated information into the case
        generateBlackboardItems(resultsData, defaultDataSource);
    }
    
    static private void generateBlackboardItems(ResultsData resultsData, Content defaultDataSource) {
        for (ResultsData.ArtifactData art : resultsData.getArtifacts()) {
            Content currContent = defaultDataSource;
            ///@todo get associated file (if any) to use as the content
            
            BlackboardArtifact.ARTIFACT_TYPE bbArtType = BlackboardArtifact.ARTIFACT_TYPE.fromLabel(art.typeStr);
            try {
                Collection<BlackboardAttribute> bbAttributes = new ArrayList<>();
                for (ResultsData.AttributeData attr : art.attributes) {
                    BlackboardAttribute.ATTRIBUTE_TYPE bbAttrType = BlackboardAttribute.ATTRIBUTE_TYPE.fromLabel(attr.typeStr);
                    BlackboardAttribute bbAttr = null;
                    switch (attr.valueType) {
                        case "text":
                            //NON-NLS
                            bbAttr = new BlackboardAttribute(bbAttrType.getTypeID(), attr.source, attr.context, attr.valueStr);
                            break;
                        case "int32":
                            //NON-NLS
                            int intValue = Integer.parseInt(attr.valueStr);
                            bbAttr = new BlackboardAttribute(bbAttrType.getTypeID(), attr.source, attr.context, intValue);
                            break;
                        case "int64":
                            //NON-NLS
                            long longValue = Long.parseLong(attr.valueStr);
                            bbAttr = new BlackboardAttribute(bbAttrType.getTypeID(), attr.source, attr.context, longValue);
                            break;
                        case "double":
                            //NON-NLS
                            double doubleValue = Double.parseDouble(attr.valueStr);
                            bbAttr = new BlackboardAttribute(bbAttrType.getTypeID(), attr.source, attr.context, doubleValue);
                            break;
                    }
                    if (bbAttr != null) {
                        bbAttributes.add(bbAttr);
                    }
                }        
                BlackboardArtifact bbArt = currContent.newArtifact(bbArtType);
                bbArt.addAttributes(bbAttributes);
                IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent("External Results Importer", bbArtType));
            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }            
        }

    }

}
