/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.stix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
class StixArtifactData {

    private AbstractFile file;
    private final String observableId;
    private final String objType;
    private static final Logger logger = Logger.getLogger(StixArtifactData.class.getName());

    public StixArtifactData(AbstractFile a_file, String a_observableId, String a_objType) {
        file = a_file;
        observableId = a_observableId;
        objType = a_objType;
    }

    public StixArtifactData(long a_objId, String a_observableId, String a_objType) {
        try {
            Case case1 = Case.getOpenCase();
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
    public void createArtifact(String a_title) throws TskCoreException {
        Blackboard blackboard;
        try {
            blackboard = Case.getOpenCase().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.StixArtifactData_noOpenCase_errMsg(), ex.getLocalizedMessage());
            return;
        }

        String setName;
        if (a_title != null) {
            setName = "STIX Indicator - " + a_title; //NON-NLS
        } else {
            setName = "STIX Indicator - (no title)"; //NON-NLS
        }

        BlackboardArtifact bba = file.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, "Stix", setName)); //NON-NLS
        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE, "Stix", observableId)); //NON-NLS
        attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, "Stix", objType)); //NON-NLS

        bba.addAttributes(attributes);
        try {
            // index the artifact for keyword search
            blackboard.indexArtifact(bba);
        } catch (Blackboard.BlackboardException ex) {
            logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.StixArtifactData_indexError_message(), bba.getDisplayName());
        }
    }

    public void print() {
        System.out.println("  " + observableId + " " + file.getName());
    }
}
