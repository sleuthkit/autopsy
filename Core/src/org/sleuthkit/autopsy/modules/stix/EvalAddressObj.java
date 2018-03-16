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

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

import java.util.List;
import java.util.ArrayList;
import org.mitre.cybox.common_2.ConditionApplicationEnum;
import org.mitre.cybox.common_2.ConditionTypeEnum;

import org.mitre.cybox.objects.Address;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 *
 */
class EvalAddressObj extends EvaluatableObject {

    private final Address obj;

    public EvalAddressObj(Address a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        if (obj.getAddressValue() == null) {
            return new ObservableResult(id, "AddressObject: No address value field found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        Case case1;
        try {
            case1 = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            return new ObservableResult(id, "Exception while getting open case.", //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);
        }
        
        String origAddressStr = obj.getAddressValue().getValue().toString();

        // For now, we don't support "NONE" because it honestly doesn't seem like it
        // would ever appear in practice. 
        if (((obj.getAddressValue().getApplyCondition() != null)
                && (obj.getAddressValue().getApplyCondition() == ConditionApplicationEnum.NONE))) {
            return new ObservableResult(id, "AddressObject: Can not process apply condition " + obj.getAddressValue().getApplyCondition().toString() //NON-NLS
                    + " on Address object", spacing, ObservableResult.ObservableState.INDETERMINATE, null); //NON-NLS
        }

        // Set warnings for any unsupported fields
        setUnsupportedFieldWarnings();

        SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();

        try {
            // Need to check that every part of the string had at least one match
            // in the AND case
            boolean everyPartMatched = true;
            List<BlackboardArtifact> combinedArts = new ArrayList<BlackboardArtifact>();
            String searchString = "";
            String[] parts = origAddressStr.split("##comma##"); //NON-NLS

            for (String addressStr : parts) {

                // Update the string to show in the results
                if (!searchString.isEmpty()) {

                    if ((obj.getAddressValue().getApplyCondition() != null)
                            && (obj.getAddressValue().getApplyCondition() == ConditionApplicationEnum.ALL)) {
                        searchString += " AND "; //NON-NLS
                    } else {
                        searchString += " OR "; //NON-NLS
                    }
                }
                searchString += addressStr;

                if ((obj.getAddressValue().getCondition() == null)
                        || (obj.getAddressValue().getCondition() == ConditionTypeEnum.EQUALS)) {
                    List<BlackboardArtifact> arts = sleuthkitCase.getBlackboardArtifacts(
                            BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT,
                            BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD,
                            addressStr);

                    if (arts.isEmpty()) {
                        everyPartMatched = false;
                    } else {
                        combinedArts.addAll(arts);
                    }

                } else {
                    // This is inefficient, but the easiest way to do it.

                    List<BlackboardArtifact> finalHits = new ArrayList<BlackboardArtifact>();

                    // Get all the URL artifacts
                    List<BlackboardArtifact> artList
                            = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);

                    for (BlackboardArtifact art : artList) {

                        for (BlackboardAttribute attr : art.getAttributes()) {
                            if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {
                                if (compareStringObject(addressStr, obj.getAddressValue().getCondition(),
                                        obj.getAddressValue().getApplyCondition(), attr.getValueString())) {
                                    finalHits.add(art);
                                }
                            }
                        }
                    }

                    if (finalHits.isEmpty()) {
                        everyPartMatched = false;
                    } else {
                        combinedArts.addAll(finalHits);
                    }
                }
            }

            // If we're in the ALL case, make sure every piece matched
            if ((obj.getAddressValue().getApplyCondition() != null)
                    && (obj.getAddressValue().getApplyCondition() == ConditionApplicationEnum.ALL)
                    && (!everyPartMatched)) {
                return new ObservableResult(id, "AddressObject: No matches for " + searchString, //NON-NLS
                        spacing, ObservableResult.ObservableState.FALSE, null);
            }

            if (!combinedArts.isEmpty()) {
                List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                for (BlackboardArtifact a : combinedArts) {
                    artData.add(new StixArtifactData(a.getObjectID(), id, "AddressObject")); //NON-NLS
                }
                return new ObservableResult(id, "AddressObject: Found a match for " + searchString, //NON-NLS
                        spacing, ObservableResult.ObservableState.TRUE, artData);
            }

            return new ObservableResult(id, "AddressObject: Found no matches for " + searchString, //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);

        } catch (TskCoreException ex) {
            return new ObservableResult(id, "AddressObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }
    }

    /**
     * Set up the warning for any fields in the object that aren't supported.
     */
    private void setUnsupportedFieldWarnings() {
        List<String> fieldNames = new ArrayList<String>();

        if (obj.getVLANName() != null) {
            fieldNames.add("VLAN_Name"); //NON-NLS
        }
        if (obj.getVLANName() != null) {
            fieldNames.add("VLAN_Num"); //NON-NLS
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
