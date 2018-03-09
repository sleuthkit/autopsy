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
import org.mitre.cybox.common_2.StringObjectPropertyType;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
abstract class EvaluatableObject {

    private String warnings;
    protected String id;
    protected String spacing;

    abstract public ObservableResult evaluate();

    /**
     * Set the warnings string to the given value.
     *
     * @param a_warnings
     */
    public void setWarnings(String a_warnings) {
        warnings = a_warnings;
    }

    /**
     * Get the warnings string. This should not be used to print the final
     * version of the warnings.
     *
     * @return
     */
    public String getWarnings() {
        return warnings;
    }

    /**
     * Add to the warnings string.
     *
     * @param a_newWarning
     */
    public void addWarning(String a_newWarning) {
        if ((warnings == null) || warnings.isEmpty()) {
            warnings = a_newWarning;
            return;
        }
        warnings = warnings + ", " + a_newWarning;
    }

    /**
     * Find a list of artifacts with the given attribute type that contain the
     * String Object. All comparisons will look for substrings of the Blackboard
     * artifacts that match the String Object.
     *
     * @param item
     * @param attrType
     *
     * @return
     *
     * @throws TskCoreException
     */
    public List<BlackboardArtifact> findArtifactsBySubstring(StringObjectPropertyType item,
            BlackboardAttribute.ATTRIBUTE_TYPE attrType) throws TskCoreException {

        if (item.getValue() == null) {
            throw new TskCoreException("Error: Value field is null"); //NON-NLS
        }

        if (item.getCondition() == null) {
            addWarning("Warning: No condition given for " + attrType.getDisplayName() + " field, using substring comparison"); //NON-NLS
        } else if (item.getCondition() != ConditionTypeEnum.CONTAINS) {
            addWarning("Warning: Ignoring condition " + item.getCondition() + " for " //NON-NLS
                    + attrType.getDisplayName() + " field and doing substring comparison"); //NON-NLS
        }

        List<BlackboardArtifact> hits = null;
        try {
            Case case1 = Case.getOpenCase();
            SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();

            String[] parts = item.getValue().toString().split("##comma##"); //NON-NLS

            if ((item.getApplyCondition() == null)
                    || (item.getApplyCondition() == ConditionApplicationEnum.ANY)) {

                for (String part : parts) {
                    if (hits == null) {
                        // Note that this searches for artifacts with "part" as a substring
                        hits = sleuthkitCase.getBlackboardArtifacts(
                                attrType,
                                part, false);
                    } else {
                        hits.addAll(sleuthkitCase.getBlackboardArtifacts(
                                attrType,
                                part, false));
                    }
                }
            } else if ((item.getApplyCondition() != null)
                    || (item.getApplyCondition() == ConditionApplicationEnum.ALL)) {

                boolean firstRound = true;
                for (String part : parts) {
                    if (firstRound) {
                        hits = sleuthkitCase.getBlackboardArtifacts(
                                attrType,
                                part, false);
                        firstRound = false;
                    } else if (hits != null) {
                        hits.retainAll(sleuthkitCase.getBlackboardArtifacts(
                                attrType,
                                part, false));
                    } else {
                        // After first round; hits is still null
                        // I don't think this should happen but if it does we're done
                        return new ArrayList<BlackboardArtifact>();
                    }
                }
            } else {
                throw new TskCoreException("Error: Can not apply NONE condition in search"); //NON-NLS
            }
        } catch (TskCoreException | NoCurrentCaseException ex) {
            addWarning(ex.getLocalizedMessage());
        }

        return hits;
    }

    /**
     * Compare a CybOX String Object with a given string.
     *
     * @param stringObj The CybOX String Object
     * @param strField  The string to compare against
     *
     * @return true if strField is a match for the CybOX object
     *
     * @throws TskCoreException
     */
    public static boolean compareStringObject(StringObjectPropertyType stringObj, String strField)
            throws TskCoreException {
        if (stringObj.getValue() == null) {
            throw new TskCoreException("Error: Value field is null"); //NON-NLS
        }

        String valueStr = stringObj.getValue().toString();
        ConditionTypeEnum condition = stringObj.getCondition();
        ConditionApplicationEnum applyCondition = stringObj.getApplyCondition();

        return compareStringObject(valueStr, condition, applyCondition, strField);
    }

    /**
     * Compare a string with CybOX conditions to a given string.
     *
     * @param valueStr       The CybOX string
     * @param condition      EQUALS, CONTAINS, STARTS_WITH, etc
     * @param applyCondition ANY, ALL, NONE
     * @param strField       The string to compare against
     *
     * @return true if strField is a match for the CybOX valueStr and conditions
     *
     * @throws TskCoreException
     */
    public static boolean compareStringObject(String valueStr, ConditionTypeEnum condition,
            ConditionApplicationEnum applyCondition, String strField)
            throws TskCoreException {

        if (valueStr == null) {
            throw new TskCoreException("Error: Value field is null"); //NON-NLS
        }

        String[] parts = valueStr.split("##comma##"); //NON-NLS
        String lowerFieldName = strField.toLowerCase();

        for (String value : parts) {
            boolean partialResult;
            if ((condition == null)
                    || (condition == ConditionTypeEnum.EQUALS)) {
                partialResult = value.equalsIgnoreCase(strField);
            } else if (condition == ConditionTypeEnum.DOES_NOT_EQUAL) {
                partialResult = !value.equalsIgnoreCase(strField);
            } else if (condition == ConditionTypeEnum.CONTAINS) {
                partialResult = lowerFieldName.contains(value.toLowerCase());
            } else if (condition == ConditionTypeEnum.DOES_NOT_CONTAIN) {
                partialResult = !lowerFieldName.contains(value.toLowerCase());
            } else if (condition == ConditionTypeEnum.STARTS_WITH) {
                partialResult = lowerFieldName.startsWith(value.toLowerCase());
            } else if (condition == ConditionTypeEnum.ENDS_WITH) {
                partialResult = lowerFieldName.endsWith(value.toLowerCase());
            } else {
                throw new TskCoreException("Could not process condition " + condition.value() + " on " + value); //NON-NLS
            }

            // Do all the short-circuiting
            if (applyCondition == ConditionApplicationEnum.NONE) {
                if (partialResult == true) {
                    // Failed
                    return false;
                }
            } else if (applyCondition == ConditionApplicationEnum.ALL) {
                if (partialResult == false) {
                    // Failed
                    return false;
                }
            } else {
                // Default is "any"
                if (partialResult == true) {
                    return true;
                }
            }
        }

        // At this point we're done and didn't short-circuit, so ALL or NONE conditions were true,
        // and ANY was false
        if ((applyCondition == ConditionApplicationEnum.NONE)
                || (applyCondition == ConditionApplicationEnum.ALL)) {
            return true;
        }
        return false;
    }

    /**
     * Format the warnings that will be printed. Basically, just put parentheses
     * around them if the string isn't empty.
     *
     * @return
     */
    public String getPrintableWarnings() {
        String warningsToPrint = "";
        if ((getWarnings() != null)
                && (!getWarnings().isEmpty())) {
            warningsToPrint = " (" + getWarnings() + ")";
        }
        return warningsToPrint;
    }
}
