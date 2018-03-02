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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

import java.util.List;
import org.mitre.cybox.common_2.ConditionApplicationEnum;

import org.mitre.cybox.objects.WindowsNetworkShare;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 *
 */
class EvalNetworkShareObj extends EvaluatableObject {

    private final WindowsNetworkShare obj;

    public EvalNetworkShareObj(WindowsNetworkShare a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        if ((obj.getNetname() == null) && (obj.getLocalPath() == null)) {
            return new ObservableResult(id, "NetworkShareObjet: No remote name or local path found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // For displaying what we were looking for in the results
        String searchString = "";
        if (obj.getNetname() != null) {
            searchString += "Netname \"" + obj.getNetname().getValue() + "\""; //NON-NLS

            // The apply conditions ALL or NONE probably won't work correctly. Neither seems
            // all that likely to come up in practice, so just give a warning.
            if ((obj.getNetname().getApplyCondition() != null)
                    && (obj.getNetname().getApplyCondition() != ConditionApplicationEnum.ANY)) {
                addWarning("Apply condition " + obj.getNetname().getApplyCondition().value() //NON-NLS
                        + " may not work correctly"); //NON-NLS
            }
        }
        if (obj.getLocalPath() != null) {
            if (!searchString.isEmpty()) {
                searchString += " and "; //NON-NLS
            }
            searchString += "LocalPath \"" + obj.getLocalPath().getValue() + "\""; //NON-NLS

            // Same as above - the apply conditions ALL or NONE probably won't work correctly. Neither seems
            // all that likely to come up in practice, so just give a warning.
            if ((obj.getLocalPath().getApplyCondition() != null)
                    && (obj.getLocalPath().getApplyCondition() != ConditionApplicationEnum.ANY)) {
                addWarning("Apply condition " + obj.getLocalPath().getApplyCondition().value() //NON-NLS
                        + " may not work correctly"); //NON-NLS
            }
        }

        setUnsupportedFieldWarnings();

        // The assumption here is that there aren't going to be too many network shares, so we
        // can cycle through all of them.
        try {
            List<BlackboardArtifact> finalHits = new ArrayList<BlackboardArtifact>();

            Case case1 = Case.getOpenCase();
            SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
            List<BlackboardArtifact> artList
                    = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_REMOTE_DRIVE);

            for (BlackboardArtifact art : artList) {
                boolean foundRemotePathMatch = false;
                boolean foundLocalPathMatch = false;

                for (BlackboardAttribute attr : art.getAttributes()) {
                    if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REMOTE_PATH.getTypeID())
                            && (obj.getNetname() != null)) {
                        foundRemotePathMatch = compareStringObject(obj.getNetname(), attr.getValueString());
                    }
                    if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LOCAL_PATH.getTypeID())
                            && (obj.getLocalPath() != null)) {
                        foundLocalPathMatch = compareStringObject(obj.getLocalPath(), attr.getValueString());
                    }
                }

                // Check whether we found everything we were looking for
                if (((foundRemotePathMatch) || (obj.getNetname() == null))
                        && ((foundLocalPathMatch) || (obj.getLocalPath() == null))) {
                    finalHits.add(art);
                }
            }

            // Check if we found any matches
            if (!finalHits.isEmpty()) {
                List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                for (BlackboardArtifact a : finalHits) {
                    artData.add(new StixArtifactData(a.getObjectID(), id, "NetworkShare")); //NON-NLS
                }
                return new ObservableResult(id, "NetworkShareObject: Found a match for " + searchString, //NON-NLS
                        spacing, ObservableResult.ObservableState.TRUE, artData);
            }

            // Didn't find any matches
            return new ObservableResult(id, "NetworkObject: No matches found for " + searchString, //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            return new ObservableResult(id, "NetworkObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }
    }

    private void setUnsupportedFieldWarnings() {
        List<String> fieldNames = new ArrayList<String>();

        if (obj.getCurrentUses() != null) {
            fieldNames.add("Current_Uses"); //NON-NLS
        }
        if (obj.getMaxUses() != null) {
            fieldNames.add("Max_Uses"); //NON-NLS
        }
        if (obj.getType() != null) {
            fieldNames.add("Type"); //NON-NLS
        }

        String warningStr = "";
        for (String name : fieldNames) {
            if (!warningStr.isEmpty()) {
                warningStr += ", ";
            }
            warningStr += name;
        }

        addWarning("Unsupported field(s): " + warningStr); //NON-NLS
    }

}
