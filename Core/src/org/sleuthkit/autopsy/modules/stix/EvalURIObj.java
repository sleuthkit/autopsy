/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

import org.mitre.cybox.objects.URIObjectType;

/**
 *
 */
class EvalURIObj extends EvaluatableObject {

    private final URIObjectType obj;

    public EvalURIObj(URIObjectType a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        if (obj.getValue() == null) {
            return new ObservableResult(id, "URIObject: No URI value field found",
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }
        String addressStr = obj.getValue().getValue().toString();

        // Strip off http:// or https://
        String modifiedAddressStr = addressStr.toLowerCase();
        modifiedAddressStr = modifiedAddressStr.replaceAll("http(s)?://", "");

        // Since we have single URL artifacts, ALL and NONE conditions probably don't make sense to test
        if (!((obj.getValue().getApplyCondition() == null)
                || (obj.getValue().getApplyCondition() == ConditionApplicationEnum.ANY))) {
            return new ObservableResult(id, "URIObject: Can not process apply condition " + obj.getValue().getApplyCondition().toString()
                    + " on URI object", spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        Case case1 = Case.getCurrentCase();
        SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();

        try {
            /*
             if ((obj.getValue().getCondition() == null)
             || (obj.getValue().getCondition() == ConditionTypeEnum.EQUALS)) {

             // Old version - uses a database query but only works on full strings.
             // It will be faster to use this in the "equals" case
             String[] parts = addressStr.split("##comma##");
             List<BlackboardArtifact> arts = new ArrayList<BlackboardArtifact>();
             for (String part : parts) {
             arts.addAll(sleuthkitCase.getBlackboardArtifacts(
             BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT,
             BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD,
             part));
             }

             if (!arts.isEmpty()) {

             List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
             for (BlackboardArtifact a : arts) {
             artData.add(new StixArtifactData(a.getObjectID(), id, "URIObject"));
             }

             return new ObservableResult(id, "URIObject: Found " + arts.size() + " matches for address = \"" + addressStr + "\"",
             spacing, ObservableResult.ObservableState.TRUE, artData);

             } else {
             return new ObservableResult(id, "URIObject: Found no matches for address = \"" + addressStr + "\"",
             spacing, ObservableResult.ObservableState.FALSE, null);
             }
             } else {*/

            // This is inefficient, but the easiest way to do it.
            List<BlackboardArtifact> finalHits = new ArrayList<BlackboardArtifact>();

            // Get all the URL artifacts
            List<BlackboardArtifact> artList
                    = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);

            for (BlackboardArtifact art : artList) {

                for (BlackboardAttribute attr : art.getAttributes()) {
                    if (attr.getAttributeTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {

                        String modifiedAttrString = attr.getValueString();
                        if (modifiedAttrString != null) {
                            modifiedAttrString = modifiedAttrString.toLowerCase();
                            modifiedAttrString = modifiedAttrString.replaceAll("http(s)?://", "");
                        }

                        if (compareStringObject(modifiedAddressStr, obj.getValue().getCondition(),
                                obj.getValue().getApplyCondition(), modifiedAttrString)) {
                            finalHits.add(art);
                        }
                    }
                }
            }

            if (!finalHits.isEmpty()) {
                List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                for (BlackboardArtifact a : finalHits) {
                    artData.add(new StixArtifactData(a.getObjectID(), id, "UriObject"));
                }
                return new ObservableResult(id, "UriObject: Found a match for " + addressStr,
                        spacing, ObservableResult.ObservableState.TRUE, artData);
            }

            return new ObservableResult(id, "URIObject: Found no matches for " + addressStr,
                    spacing, ObservableResult.ObservableState.FALSE, null);
            /*}*/

        } catch (TskCoreException ex) {
            return new ObservableResult(id, "URIObject: Exception during evaluation: " + ex.getLocalizedMessage(),
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

    }

}
