/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.stix;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
class StixArtifactData {

    private static final String MODULE_NAME = "Stix";

    private AbstractFile file;
    private final String observableId;
    private final String objType;
    private static final Logger logger = Logger.getLogger(StixArtifactData.class.getName());

    StixArtifactData(AbstractFile a_file, String a_observableId, String a_objType) {
        file = a_file;
        observableId = a_observableId;
        objType = a_objType;
    }

    StixArtifactData(long a_objId, String a_observableId, String a_objType) {
        try {
            Case case1 = Case.getCurrentCaseThrows();
            SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
            file = sleuthkitCase.getAbstractFileById(a_objId);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            file = null;
        }
        observableId = a_observableId;
        objType = a_objType;
    }

    @Messages({"StixArtifactData.indexError.message=Failed to index STIX interesting file hit artifact for keyword search.",
        "StixArtifactData.noOpenCase.errMsg=No open case available."})
    void createArtifact(String a_title) throws TskCoreException {
        Blackboard blackboard;
        try {
            blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return;
        }

        String setName = "STIX Indicator - " + StringUtils.defaultIfBlank(a_title, "(no title)"); //NON-NLS

        Collection<BlackboardAttribute> attributes = Arrays.asList(
                new BlackboardAttribute(TSK_SET_NAME, MODULE_NAME, setName),
                new BlackboardAttribute(TSK_TITLE, MODULE_NAME, observableId),
                new BlackboardAttribute(TSK_CATEGORY, MODULE_NAME, objType));

        // Create artifact if it doesn't already exist.
        if (!blackboard.artifactExists(file, TSK_INTERESTING_FILE_HIT, attributes)) {
            BlackboardArtifact bba = file.newArtifact(TSK_INTERESTING_FILE_HIT);
            bba.addAttributes(attributes);

            try {
                /*
                 * post the artifact which will index the artifact for keyword
                 * search, and fire an event to notify UI of this new artifact
                 */
                blackboard.postArtifact(bba, MODULE_NAME);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
            }
        }
    }
}
