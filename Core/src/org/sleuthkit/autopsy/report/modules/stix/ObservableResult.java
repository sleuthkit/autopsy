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
package org.sleuthkit.autopsy.report.modules.stix;

import java.util.List;
import java.util.ArrayList;

import org.mitre.cybox.cybox_2.OperatorTypeEnum;

/**
 *
 */
class ObservableResult {

    public enum ObservableState {

        TRUE("true         "), //NON-NLS
        FALSE("false        "), //NON-NLS
        INDETERMINATE("indeterminate"); //NON-NLS

        private final String label;

        private ObservableState(String s) {
            label = s;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private ObservableState state = null;
    private String description = "";
    private List<StixArtifactData> artifacts;

    public ObservableResult(String a_id, String a_desc, String a_spacing,
            ObservableState a_state, List<StixArtifactData> a_artifacts) {
        state = a_state;
        description = a_spacing + a_id + "\t" + a_state + "\t" + a_desc + "\r\n";
        artifacts = a_artifacts;
    }

    public ObservableResult(OperatorTypeEnum a_operator, String a_spacing) {
        state = ObservableState.INDETERMINATE;
        description = a_spacing + a_operator + "\r\n";
        artifacts = new ArrayList<StixArtifactData>();
    }

    public ObservableState getState() {
        return state;
    }

    /**
     * Returns true if the ObservableResult is currently true. Note: A false
     * result here does not mean the state is false; it could also be
     * indeterminate.
     *
     * @return true if the ObservableResult is true, false if it is false or
     *         indeterminate
     */
    public boolean isTrue() {
        return (state == ObservableState.TRUE);
    }

    /**
     * Returns true if the ObservableResult is currently false. Note: A false
     * result here does not mean the state is true; it could also be
     * indeterminate.
     *
     * @return true if the ObservableResult is false, false if it is true or
     *         indeterminate
     */
    public boolean isFalse() {
        return (state == ObservableState.FALSE);
    }

    public String getDescription() {
        return description;
    }

    public List<StixArtifactData> getArtifacts() {
        return artifacts;
    }

    /**
     * Add a new result to the current state
     *
     * @param a_result   The new result to add
     * @param a_operator AND or OR
     */
    public void addResult(ObservableResult a_result, OperatorTypeEnum a_operator) {
        addResult(a_result.getDescription(), a_result.getState(),
                a_result.getArtifacts(), a_operator);
    }

    /**
     * Add a new result to the current state.
     *
     * @param a_description Description of the observable and testing done
     * @param a_state       State of what we're adding (true, false, or
     *                      indeterminate)
     * @param a_operator    AND or OR
     */
    private void addResult(String a_description, ObservableState a_state,
            List<StixArtifactData> a_artifacts, OperatorTypeEnum a_operator) {

        addToDesc(a_description);

        if (a_operator == OperatorTypeEnum.AND) {

            if (a_state == ObservableState.FALSE) {
                // If we now have a false, the whole thing is false regardless of previous state.
                // Clear out any existing artifacts.
                state = ObservableState.FALSE;
                artifacts.clear();
            } else if (a_state == ObservableState.INDETERMINATE) {
                // Don't change the current state, and don't save the new artifacts
                // (though there probably wouldn't be any)
            } else {
                if (state == ObservableState.FALSE) {
                    // Previous state false + new state true => stay false
                } else if (state == ObservableState.TRUE) {
                    // Previous state true + new state true => stay true and add artifacts
                    if ((artifacts == null) && (a_artifacts != null)) {
                        artifacts = new ArrayList<StixArtifactData>();
                    }
                    if (a_artifacts != null) {
                        artifacts.addAll(a_artifacts);
                    }
                } else {
                    // If the previous state was indeterminate, change it to true and add artifacts
                    state = ObservableState.TRUE;
                    if ((artifacts == null) && (a_artifacts != null)) {
                        artifacts = new ArrayList<StixArtifactData>();
                    }
                    if (a_artifacts != null) {
                        artifacts.addAll(a_artifacts);
                    }
                }
            }
        } else {
            if (a_state == ObservableState.TRUE) {
                // If we now have a true, the whole thing is true regardless of previous state.
                // Add the new artifacts.
                state = ObservableState.TRUE;
                if ((artifacts == null) && (a_artifacts != null)) {
                    artifacts = new ArrayList<StixArtifactData>();
                }
                if (a_artifacts != null) {
                    artifacts.addAll(a_artifacts);
                }
            } else if (a_state == ObservableState.INDETERMINATE) {
                // Don't change the current state and don't record it to the
                // description string (later we should save these in some way)
            } else {
                if (state == ObservableState.FALSE) {
                    // Previous state false + new state false => stay false
                } else if (state == ObservableState.TRUE) {
                    // Previous state true + new state false => stay true
                } else {
                    // Previous state indeterminate + new state false => change to false
                    state = ObservableState.FALSE;
                }
            }
        }

    }

    /**
     * Add to the description string. Mostly just to make things cleaner by not
     * testing for null all over the place.
     *
     * @param a_desc New part of the description to add
     */
    private void addToDesc(String a_desc) {
        if (description == null) {
            description = a_desc;
        } else {
            description += a_desc;
        }
    }
}
