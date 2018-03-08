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

import org.mitre.cybox.objects.AccountObjectType;
import org.mitre.cybox.objects.UserAccountObjectType;
import org.mitre.cybox.objects.WindowsUserAccount;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 *
 */
class EvalAccountObj extends EvaluatableObject {

    private AccountObjectType obj;

    public EvalAccountObj(AccountObjectType a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        // Fields we can search for:
        //   UserAccount: Home_Directory, Username
        //   WinUserAccount: SID
        if (!(obj instanceof UserAccountObjectType)) {
            return new ObservableResult(id, "AccountObject: Can not process \"Account\" - need a User_Account or Windows_User_Account", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // For displaying what we were looking for in the results
        String searchString = "";

        // Check which fields are present and record them    
        boolean haveHomeDir = false;
        boolean haveUsername = false;
        boolean haveSID = false;

        UserAccountObjectType userAccountObj = (UserAccountObjectType) obj;
        if (userAccountObj.getHomeDirectory() != null) {
            haveHomeDir = true;
            searchString = "HomeDir \"" + userAccountObj.getHomeDirectory().getValue().toString() + "\""; //NON-NLS
        }
        if (userAccountObj.getUsername() != null) {
            haveUsername = true;
            if (!searchString.isEmpty()) {
                searchString += " and "; //NON-NLS
            }
            searchString += "Username \"" + userAccountObj.getUsername().getValue().toString() + "\""; //NON-NLS
        }

        WindowsUserAccount winUserObj = null;
        if (obj instanceof WindowsUserAccount) {
            winUserObj = (WindowsUserAccount) obj;

            if (winUserObj.getSecurityID() != null) {
                haveSID = true;
                if (!searchString.isEmpty()) {
                    searchString += " and "; //NON-NLS
                }
                searchString += "SID \"" + winUserObj.getSecurityID().getValue().toString() + "\""; //NON-NLS
            }
        }

        if (!(haveHomeDir || haveUsername || haveSID)) {
            return new ObservableResult(id, "AccountObject: No evaluatable fields found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // Set warnings for any unsupported fields
        setUnsupportedFieldWarnings();

        // The assumption here is that there aren't going to be too many network shares, so we
        // can cycle through all of them.
        try {
            List<BlackboardArtifact> finalHits = new ArrayList<BlackboardArtifact>();

            Case case1 = Case.getOpenCase();
            SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
            List<BlackboardArtifact> artList
                    = sleuthkitCase.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_OS_ACCOUNT);

            for (BlackboardArtifact art : artList) {
                boolean foundHomeDirMatch = false;
                boolean foundUsernameMatch = false;
                boolean foundSIDMatch = false;

                for (BlackboardAttribute attr : art.getAttributes()) {
                    if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH.getTypeID())
                            && (haveHomeDir)) {
                        foundHomeDirMatch = compareStringObject(userAccountObj.getHomeDirectory(), attr.getValueString());
                    }
                    if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_NAME.getTypeID())
                            && (haveUsername)) {
                        foundUsernameMatch = compareStringObject(userAccountObj.getUsername(), attr.getValueString());
                    }
                    if ((attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID.getTypeID())
                            && (haveSID) && (winUserObj != null)) {
                        foundSIDMatch = compareStringObject(winUserObj.getSecurityID(), attr.getValueString());
                    }
                }

                if (((!haveHomeDir) || foundHomeDirMatch)
                        && ((!haveUsername) || foundUsernameMatch)
                        && ((!haveSID) || foundSIDMatch)) {
                    finalHits.add(art);
                }

            }

            // Check if we found any matches
            if (!finalHits.isEmpty()) {
                List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                for (BlackboardArtifact a : finalHits) {
                    artData.add(new StixArtifactData(a.getObjectID(), id, "Account")); //NON-NLS
                }
                return new ObservableResult(id, "AccountObject: Found a match for " + searchString, //NON-NLS
                        spacing, ObservableResult.ObservableState.TRUE, artData);
            }

            // Didn't find any matches
            return new ObservableResult(id, "AccountObject: No matches found for " + searchString, //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);
        } catch (TskCoreException | NoCurrentCaseException ex) {
            return new ObservableResult(id, "AccountObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

    }

    /**
     * Set up the warning for any fields in the object that aren't supported.
     */
    private void setUnsupportedFieldWarnings() {
        List<String> fieldNames = new ArrayList<String>();

        if (obj.getDescription() != null) {
            fieldNames.add("Description"); //NON-NLS
        }
        if (obj.getDomain() != null) {
            fieldNames.add("Domain"); //NON-NLS
        }
        if (obj.getAuthentications() != null) {
            fieldNames.add("Authentication"); //NON-NLS
        }
        if (obj.getCreationDate() != null) {
            fieldNames.add("Creation_Date"); //NON-NLS
        }
        if (obj.getModifiedDate() != null) {
            fieldNames.add("Modified_Date"); //NON-NLS
        }
        if (obj.getLastAccessedTime() != null) {
            fieldNames.add("Last_Accessed_Time"); //NON-NLS
        }

        if (obj instanceof UserAccountObjectType) {
            UserAccountObjectType userAccountObj = (UserAccountObjectType) obj;
            if (userAccountObj.getFullName() != null) {
                fieldNames.add("Full_Name"); //NON-NLS
            }
            if (userAccountObj.getGroupList() != null) {
                fieldNames.add("Group_List"); //NON-NLS
            }
            if (userAccountObj.getLastLogin() != null) {
                fieldNames.add("Last_Login"); //NON-NLS
            }
            if (userAccountObj.getPrivilegeList() != null) {
                fieldNames.add("Privilege_List"); //NON-NLS
            }
            if (userAccountObj.getScriptPath() != null) {
                fieldNames.add("Script_Path"); //NON-NLS
            }
            if (userAccountObj.getUserPasswordAge() != null) {
                fieldNames.add("User_Password_Age"); //NON-NLS
            }
        }

        if (obj instanceof WindowsUserAccount) {
            WindowsUserAccount winUserObj = (WindowsUserAccount) obj;

            if (winUserObj.getSecurityType() != null) {
                fieldNames.add("Security_Type"); //NON-NLS
            }
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
