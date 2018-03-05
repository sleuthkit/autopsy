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
import java.util.List;
import org.mitre.cybox.common_2.ConditionApplicationEnum;
import org.mitre.cybox.common_2.ConditionTypeEnum;
import org.mitre.cybox.objects.DomainName;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 */
class EvalDomainObj extends EvaluatableObject {

    private final DomainName obj;

    public EvalDomainObj(DomainName a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        if (obj.getValue() == null) {
            return new ObservableResult(id, "DomainObject: No domain value field found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        Case case1;
        try {
            case1 = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            return new ObservableResult(id, "Exception while getting open case.", //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);
        }
         // Since we have single URL artifacts, ALL and NONE conditions probably don't make sense to test
        if (!((obj.getValue().getApplyCondition() == null)
                || (obj.getValue().getApplyCondition() == ConditionApplicationEnum.ANY))) {
            return new ObservableResult(id, "DomainObject: Can not process apply condition " + obj.getValue().getApplyCondition().toString() //NON-NLS
                    + " on Domain object", spacing, ObservableResult.ObservableState.INDETERMINATE, null); //NON-NLS
        }

        // If the condition is not "CONTAINS", add a warning that it's being ignored
        if ((obj.getValue().getCondition() != null)
                && (obj.getValue().getCondition() != ConditionTypeEnum.CONTAINS)) {
            addWarning("Warning: Ignoring condition " + obj.getValue().getCondition().toString() //NON-NLS
                    + " on DomainName - using substring comparison"); //NON-NLS
        }

        SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();

        try {
            // Set up the list of matching artifacts
            List<BlackboardArtifact> finalHits = new ArrayList<BlackboardArtifact>();

            // Get all the URL artifacts
            List<BlackboardArtifact> artList
                    = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);

            for (BlackboardArtifact art : artList) {

                for (BlackboardAttribute attr : art.getAttributes()) {
                    if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID()) {
                        String url = attr.getValueString();

                        // Check whether the domain name is a substring of the URL (regardless 
                        // of the condition on the domain name object)
                        if (compareStringObject(obj.getValue().getValue().toString(), ConditionTypeEnum.CONTAINS,
                                obj.getValue().getApplyCondition(), url)) {
                            finalHits.add(art);
                        }
                    }
                }
            }

            if (!finalHits.isEmpty()) {
                List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                for (BlackboardArtifact a : finalHits) {
                    artData.add(new StixArtifactData(a.getObjectID(), id, "DomainNameObject")); //NON-NLS
                }
                return new ObservableResult(id, "DomainNameObject: Found a match for " + obj.getValue().getValue().toString() //NON-NLS
                        + " " + getPrintableWarnings(),
                        spacing, ObservableResult.ObservableState.TRUE, artData);
            }

            return new ObservableResult(id, "DomainNameObject: Found no matches for " + obj.getValue().getValue().toString() //NON-NLS
                    + " " + getPrintableWarnings(),
                    spacing, ObservableResult.ObservableState.FALSE, null);
        } catch (TskCoreException ex) {
            return new ObservableResult(id, "DomainNameObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

    }

}
