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

import org.mitre.cybox.common_2.AnyURIObjectPropertyType;
import org.mitre.cybox.objects.URLHistory;
import org.mitre.cybox.objects.URLHistoryEntryType;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 *
 */
class EvalURLHistoryObj extends EvaluatableObject {

    private final URLHistory obj;

    public EvalURLHistoryObj(URLHistory a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        if ((obj.getBrowserInformation() == null) && (obj.getURLHistoryEntries() == null)) {
            return new ObservableResult(id, "URLHistoryObject: No browser info or history entries found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // For displaying what we were looking for in the results
        String baseSearchString = "";
        String finalResultsStr = "";

        // The browser info is the same for each entry
        boolean haveBrowserName = false;
        if (obj.getBrowserInformation() != null) {
            if (obj.getBrowserInformation().getName() != null) {
                haveBrowserName = true;
            }
            baseSearchString = "Browser \"" + obj.getBrowserInformation().getName() + "\""; //NON-NLS
        }

        // Matching artifacts
        List<BlackboardArtifact> finalHits = new ArrayList<BlackboardArtifact>();

        if (obj.getURLHistoryEntries() != null) {

            for (URLHistoryEntryType entry : obj.getURLHistoryEntries()) {

                boolean haveURL = false;
                boolean haveHostname = false;
                boolean haveReferrer = false;
                boolean havePageTitle = false;
                boolean haveUserProfile = false;

                setUnsupportedEntryFieldWarnings(entry);

                // At present, the search string doesn't get reported (because there could be different ones
                // for multiple URL History Entries) but it's good for debugging.
                String searchString = baseSearchString;

                if ((entry.getURL() != null) && (entry.getURL().getValue() != null)) {
                    haveURL = true;
                    if (!searchString.isEmpty()) {
                        searchString += " and "; //NON-NLS
                    }
                    searchString += "URL \"" + entry.getURL().getValue().getValue() + "\""; //NON-NLS
                }

                if ((entry.getReferrerURL() != null) && (entry.getReferrerURL().getValue() != null)) {
                    haveReferrer = true;
                    if (!searchString.isEmpty()) {
                        searchString += " and "; //NON-NLS
                    }
                    searchString += "Referrer \"" + entry.getReferrerURL().getValue().getValue() + "\""; //NON-NLS
                }

                if (entry.getUserProfileName() != null) {
                    haveUserProfile = true;
                    if (!searchString.isEmpty()) {
                        searchString += " and "; //NON-NLS
                    }
                    searchString += "UserProfile \"" + entry.getUserProfileName().getValue() + "\""; //NON-NLS
                }

                if (entry.getPageTitle() != null) {
                    havePageTitle = true;
                    if (!searchString.isEmpty()) {
                        searchString += " and "; //NON-NLS
                    }
                    searchString += "Page title \"" + entry.getPageTitle().getValue() + "\""; //NON-NLS
                }

                if ((entry.getHostname() != null) && (entry.getHostname().getHostnameValue() != null)) {
                    haveHostname = true;
                    if (!searchString.isEmpty()) {
                        searchString += " and "; //NON-NLS
                    }
                    searchString += "Hostname \"" + entry.getHostname().getHostnameValue().getValue() + "\""; //NON-NLS
                }

                if (!finalResultsStr.isEmpty()) {
                    finalResultsStr += ", ";
                }
                finalResultsStr += searchString;

                if (!(haveURL || haveHostname || haveReferrer
                        || havePageTitle || haveUserProfile || haveBrowserName)) {
                    return new ObservableResult(id, "URLHistoryObject: No evaluatable fields found", //NON-NLS
                            spacing, ObservableResult.ObservableState.INDETERMINATE, null);
                }

                try {
                    Case case1 = Case.getOpenCase();
                    SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
                    List<BlackboardArtifact> artList
                            = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY);

                    for (BlackboardArtifact art : artList) {
                        boolean foundURLMatch = false;
                        boolean foundHostnameMatch = false;
                        boolean foundReferrerMatch = false;
                        boolean foundPageTitleMatch = false;
                        boolean foundUserProfileMatch = false;
                        boolean foundBrowserNameMatch = false;

                        for (BlackboardAttribute attr : art.getAttributes()) {
                            if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL.getTypeID())
                                    && (haveURL)) {
                                if (entry.getURL().getValue() instanceof AnyURIObjectPropertyType) {
                                    foundURLMatch = compareStringObject(entry.getURL().getValue().getValue().toString(),
                                            entry.getURL().getValue().getCondition(),
                                            entry.getURL().getValue().getApplyCondition(),
                                            attr.getValueString());
                                } else {
                                    addWarning("Non-AnyURIObjectPropertyType found in URL value field"); //NON-NLS
                                }
                            }
                            if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DOMAIN.getTypeID())
                                    && (haveHostname)) {
                                foundHostnameMatch = compareStringObject(entry.getHostname().getHostnameValue(),
                                        attr.getValueString());
                            }
                            if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_REFERRER.getTypeID())
                                    && (haveReferrer)) {
                                if (entry.getReferrerURL().getValue() instanceof AnyURIObjectPropertyType) {
                                    foundReferrerMatch = compareStringObject(entry.getReferrerURL().getValue().getValue().toString(),
                                            entry.getURL().getValue().getCondition(),
                                            entry.getURL().getValue().getApplyCondition(),
                                            attr.getValueString());
                                } else {
                                    addWarning("Non-AnyURIObjectPropertyType found in URL value field"); //NON-NLS
                                }
                            }
                            if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TITLE.getTypeID())
                                    && (havePageTitle)) {
                                foundPageTitleMatch = compareStringObject(entry.getPageTitle(),
                                        attr.getValueString());
                            }
                            if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME.getTypeID())
                                    && (haveUserProfile)) {
                                foundUserProfileMatch = compareStringObject(entry.getUserProfileName(),
                                        attr.getValueString());
                            }
                            if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())
                                    && (haveBrowserName)) {
                                foundBrowserNameMatch = compareStringObject(obj.getBrowserInformation().getName(),
                                        null, null, attr.getValueString());
                            }
                        }

                        if (((!haveURL) || foundURLMatch)
                                && ((!haveHostname) || foundHostnameMatch)
                                && ((!haveReferrer) || foundReferrerMatch)
                                && ((!havePageTitle) || foundPageTitleMatch)
                                && ((!haveUserProfile) || foundUserProfileMatch)
                                && ((!haveBrowserName) || foundBrowserNameMatch)) {
                            finalHits.add(art);
                        }
                    }

                } catch (TskCoreException | NoCurrentCaseException ex) {
                    return new ObservableResult(id, "URLHistoryObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                            spacing, ObservableResult.ObservableState.INDETERMINATE, null);
                }

            }

            if (!finalHits.isEmpty()) {
                List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                for (BlackboardArtifact a : finalHits) {
                    artData.add(new StixArtifactData(a.getObjectID(), id, "URLHistory")); //NON-NLS
                }
                return new ObservableResult(id, "URLHistoryObject: Found at least one match for " + finalResultsStr, //NON-NLS
                        spacing, ObservableResult.ObservableState.TRUE, artData);
            }

            // Didn't find any matches
            return new ObservableResult(id, "URLHistoryObject: No matches found for " + finalResultsStr, //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);

        } else if (haveBrowserName) {

            // It doesn't seem too useful, but we can just search for the browser name
            // if there aren't any URL entries
            try {
                Case case1 = Case.getOpenCase();
                SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
                List<BlackboardArtifact> artList
                        = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_WEB_HISTORY);

                for (BlackboardArtifact art : artList) {
                    boolean foundBrowserNameMatch = false;

                    for (BlackboardAttribute attr : art.getAttributes()) {
                        if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME.getTypeID())
                                && (haveBrowserName)) {
                            foundBrowserNameMatch = compareStringObject(obj.getBrowserInformation().getName(),
                                    null, null, attr.getValueString());
                        }
                    }

                    if (foundBrowserNameMatch) {
                        finalHits.add(art);
                    }
                }

                if (!finalHits.isEmpty()) {
                    List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                    for (BlackboardArtifact a : finalHits) {
                        artData.add(new StixArtifactData(a.getObjectID(), id, "URLHistory")); //NON-NLS
                    }
                    return new ObservableResult(id, "URLHistoryObject: Found at least one match", //NON-NLS
                            spacing, ObservableResult.ObservableState.TRUE, artData);
                }

                // Didn't find any matches
                return new ObservableResult(id, "URLHistoryObject: No matches found for " + baseSearchString, //NON-NLS
                        spacing, ObservableResult.ObservableState.FALSE, null);
            } catch (TskCoreException | NoCurrentCaseException ex) {
                return new ObservableResult(id, "URLHistoryObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                        spacing, ObservableResult.ObservableState.INDETERMINATE, null);
            }

        } else {
            // Nothing to search for
            return new ObservableResult(id, "URLHistoryObject: No evaluatable fields found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

    }

    /**
     * Set up the warning for any fields in the URL_History_Entry object that
     * aren't supported.
     */
    private void setUnsupportedEntryFieldWarnings(URLHistoryEntryType entry) {
        List<String> fieldNames = new ArrayList<String>();

        if (entry.getUserProfileName() != null) {
            fieldNames.add("User_Profile_Name"); //NON-NLS
        }
        if (entry.getVisitCount() != null) {
            fieldNames.add("Visit_Count"); //NON-NLS
        }
        if (entry.getManuallyEnteredCount() != null) {
            fieldNames.add("Manually_Entered_Count"); //NON-NLS
        }
        if (entry.getModificationDateTime() != null) {
            fieldNames.add("Modification_DateTime"); //NON-NLS
        }
        if (entry.getExpirationDateTime() != null) {
            fieldNames.add("Expiration_DateTime"); //NON-NLS
        }
        if (entry.getFirstVisitDateTime() != null) {
            fieldNames.add("First_Visit_DateTime"); //NON-NLS
        }
        if (entry.getLastVisitDateTime() != null) {
            fieldNames.add("Last_Visit_DateTime"); //NON-NLS
        }

        String warningStr = "";
        for (String name : fieldNames) {
            if (!warningStr.isEmpty()) {
                warningStr += ", ";
            }
            warningStr += name;
        }

        addWarning("Unsupported URL_History_Entry field(s): " + warningStr); //NON-NLS
    }
}
