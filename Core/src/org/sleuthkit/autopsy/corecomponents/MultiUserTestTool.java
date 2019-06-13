/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.CaseActionException;
import org.sleuthkit.autopsy.casemodule.CaseDetails;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeStampUtils;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 * Test tool that creates a multi user case, database, KWS index, runs ingest,
 * etc. If errors are encountered during this process, provides a message
 * regarding the problem and possible causes.
 */
class MultiUserTestTool {
    
    private static final String CASE_NAME = "Test_MU_Settings";
    private static final Logger LOGGER = Logger.getLogger(MultiUserTestTool.class.getName());
    
    private void MultiUserTestToo() {
    }
    
    static String runTest(String rootOutputDirectory) {

        // 1 (MH) Creates a case in the output folder. Could be hard coded name/time stamp thing.
        Case caseForJob;
        try {
            caseForJob = createCase(CASE_NAME, rootOutputDirectory);
        } catch (CaseActionException ex) {
            LOGGER.log(Level.SEVERE, "Unable to create case", ex);
            return "Unable to create case";
        }
        
        if (caseForJob == null) {
            LOGGER.log(Level.SEVERE, "Error creating multi user case");
            return "Error creating multi user case";
        }

        // 2 (MH) Verifies that Solr was able to create the core. If any of those steps fail, it gives an error message.
        /*KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, "Fake Keyword Search", "Fake Keyword Preview Text"));
        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH, "Output Path", rootOutputDirectory));
        BlackboardArtifact bba = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        bba.addAttributes(attributes);*/

        // Verifies that DB was created. etc
        //SELECT datname FROM pg_catalog.pg_database WHERE lower(datname) = lower('dbname');
        String databaseName = Case.getSleuthkitCase().getDatabaseName();
        
        // 3 (NTH) Makes a text file in a temp folder with just the text "Test" in it. 
        
        
        // 4 (NTH) Adds it as a logical file set data source.
        
        
        // 5 (NTH) Runs ingest on that data source and reports errors if the modules could not start.
        
        
        // 6 (MH) Deletes the case.
        
        return "";
    }
    
    private static Case createCase(String baseCaseName, String rootOutputDirectory) throws CaseActionException {

        String caseDirectoryPath = rootOutputDirectory + File.pathSeparator + baseCaseName + "_" + TimeStampUtils.createTimeStamp();
        
        // Create the case directory
        Case.createCaseDirectory(caseDirectoryPath, Case.CaseType.MULTI_USER_CASE);

        CaseDetails caseDetails = new CaseDetails(baseCaseName);
        Case.createAsCurrentCase(Case.CaseType.MULTI_USER_CASE, caseDirectoryPath, caseDetails);

        Case caseForJob = Case.getCurrentCase();
        return caseForJob;
    }
    
}
