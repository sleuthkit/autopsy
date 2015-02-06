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
import org.sleuthkit.datamodel.OSInfo;
import org.sleuthkit.datamodel.OSUtility;

import java.util.List;
import java.util.ArrayList;

import org.mitre.cybox.objects.SystemObjectType;
import org.mitre.cybox.objects.WindowsSystem;

/**
 *
 */
class EvalSystemObj extends EvaluatableObject {

    private final SystemObjectType obj;

    public EvalSystemObj(SystemObjectType a_obj, String a_id, String a_spacing) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        // For displaying what we were looking for in the results
        String searchString = "";

        // Check which fields are present and record them 
        boolean haveHostname = false;
        // boolean haveDomain = false; 
        boolean haveProcArch = false;
        boolean haveTempDir = false;
        boolean haveProductName = false;
        boolean haveSystemRoot = false;
        boolean haveProductID = false;
        boolean haveOwner = false;
        boolean haveOrganization = false;

        if (obj.getHostname() != null) {
            haveHostname = true;
            searchString = "Hostname \"" + obj.getHostname().getValue().toString() + "\"";
        }
        if(obj.getProcessorArchitecture() != null){
            haveProcArch = true;
            if(! searchString.isEmpty()){
                searchString += " and ";
            }
            searchString += "Processor architecture \"" + obj.getProcessorArchitecture().getValue().toString() + "\"";
        }

        WindowsSystem winSysObj = null;
        if (obj instanceof WindowsSystem) {
            winSysObj = (WindowsSystem) obj;

            if (winSysObj.getProductID() != null) {
                haveProductID = true;
                if (!searchString.isEmpty()) {
                    searchString += " and ";
                }
                searchString += "Product ID \"" + winSysObj.getProductID().getValue().toString() + "\"";
            }
            if (winSysObj.getProductName() != null) {
                haveProductName = true;
                if (!searchString.isEmpty()) {
                    searchString += " and ";
                }
                searchString += "Product Name \"" + winSysObj.getProductName().getValue().toString() + "\"";
            }
            if (winSysObj.getRegisteredOrganization() != null) {
                haveOrganization = true;
                if (!searchString.isEmpty()) {
                    searchString += " and ";
                }
                searchString += "Registered Org \"" + winSysObj.getRegisteredOrganization().getValue().toString() + "\"";
            }
            if (winSysObj.getRegisteredOwner() != null) {
                haveOwner = true;
                if (!searchString.isEmpty()) {
                    searchString += " and ";
                }
                searchString += "Registered Owner \"" + winSysObj.getRegisteredOwner().getValue().toString() + "\"";
            }
            if (winSysObj.getWindowsSystemDirectory() != null) {
                haveSystemRoot = true;
                if (!searchString.isEmpty()) {
                    searchString += " and ";
                }
                searchString += "System root \"" + winSysObj.getWindowsSystemDirectory().getValue().toString() + "\"";
            }
            if (winSysObj.getWindowsTempDirectory() != null) {
                haveTempDir = true;
                if (!searchString.isEmpty()) {
                    searchString += " and ";
                }
                searchString += "Temp dir \"" + winSysObj.getWindowsTempDirectory().getValue().toString() + "\"";
            }
        }

        // Return if we have nothing to search for
        if (!(haveHostname || haveProcArch
                || haveTempDir || haveProductName || haveSystemRoot || haveProductID
                || haveOwner || haveOrganization)) {
            return new ObservableResult(id, "SystemObject: No evaluatable fields found",
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        setUnsupportedFieldWarnings();

        try {
            Case case1 = Case.getCurrentCase();
            SleuthkitCase sleuthkitCase = case1.getSleuthkitCase();
            List<OSInfo> osInfoList = OSUtility.getOSInfo(sleuthkitCase);

            List<BlackboardArtifact> finalHits = new ArrayList<BlackboardArtifact>();

            if (!osInfoList.isEmpty()) {
                for (OSInfo info : osInfoList) {

                    boolean foundHostnameMatch = false;
                    //boolean foundDomainMatch = false;
                    boolean foundProcArchMatch = false;
                    boolean foundTempDirMatch = false;
                    boolean foundProductNameMatch = false;
                    boolean foundSystemRootMatch = false;
                    boolean foundProductIDMatch = false;
                    boolean foundOwnerMatch = false;
                    boolean foundOrganizationMatch = false;

                    if (haveHostname) {
                        foundHostnameMatch = compareStringObject(obj.getHostname(), info.getCompName());
                    }
                    if (haveProcArch) {
                        foundProcArchMatch = compareStringObject(obj.getProcessorArchitecture().getValue().toString(),
                                obj.getProcessorArchitecture().getCondition(), 
                                obj.getProcessorArchitecture().getApplyCondition(),
                                info.getAttributeValue(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROCESSOR_ARCHITECTURE));
                    }
                    if (haveTempDir && (winSysObj != null)) {
                        foundTempDirMatch = compareStringObject(winSysObj.getWindowsTempDirectory(),
                                info.getAttributeValue(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEMP_DIR));
                    }
                    if (haveProductName && (winSysObj != null)) {
                        foundProductNameMatch = compareStringObject(winSysObj.getProductName(),
                                info.getAttributeValue(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME));
                    }
                    if (haveSystemRoot && (winSysObj != null)) {
                        foundSystemRootMatch = compareStringObject(winSysObj.getWindowsSystemDirectory(),
                                info.getAttributeValue(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH));
                    }
                    if (haveProductID && (winSysObj != null)) {
                        foundProductIDMatch = compareStringObject(winSysObj.getProductID(),
                                info.getAttributeValue(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PRODUCT_ID));
                    }
                    if (haveOwner && (winSysObj != null)) {
                        foundOwnerMatch = compareStringObject(winSysObj.getRegisteredOwner(),
                                info.getAttributeValue(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_OWNER));
                    }
                    if (haveOrganization && (winSysObj != null)) {
                        foundOrganizationMatch = compareStringObject(winSysObj.getRegisteredOrganization(),
                                info.getAttributeValue(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ORGANIZATION));
                    }

                    if (((!haveHostname) || foundHostnameMatch)
                            && ((!haveProcArch) || foundProcArchMatch)
                            && ((!haveTempDir) || foundTempDirMatch)
                            && ((!haveProductName) || foundProductNameMatch)
                            && ((!haveSystemRoot) || foundSystemRootMatch)
                            && ((!haveProductID) || foundProductIDMatch)
                            && ((!haveOwner) || foundOwnerMatch)
                            && ((!haveOrganization) || foundOrganizationMatch)) {

                        finalHits.addAll(info.getArtifacts());
                    }
                }

                if (!finalHits.isEmpty()) {
                    List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                    for (BlackboardArtifact a : finalHits) {
                        artData.add(new StixArtifactData(a.getObjectID(), id, "System"));
                    }
                    return new ObservableResult(id, "SystemObject: Found a match for " + searchString,
                            spacing, ObservableResult.ObservableState.TRUE, artData);
                }

                // Didn't find any matches
                return new ObservableResult(id, "SystemObject: No matches found for " + searchString,
                        spacing, ObservableResult.ObservableState.FALSE, null);
            } else {
                return new ObservableResult(id, "SystemObject: No OS artifacts found",
                        spacing, ObservableResult.ObservableState.INDETERMINATE, null);
            }
        } catch (TskCoreException ex) {
            return new ObservableResult(id, "SystemObject: Exception during evaluation: " + ex.getLocalizedMessage(),
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }
    }

    /**
     * Set up the warning for any fields in the object that aren't supported.
     */
    private void setUnsupportedFieldWarnings() {
        List<String> fieldNames = new ArrayList<String>();

        if (obj.getAvailablePhysicalMemory() != null) {
            fieldNames.add("Available_Physical_Memory");
        }
        if (obj.getBIOSInfo() != null) {
            fieldNames.add("BIOS_Info");
        }
        if (obj.getDate() != null) {
            fieldNames.add("Date");
        }
        if (obj.getLocalTime() != null) {
            fieldNames.add("Local_Time");
        }
        if (obj.getNetworkInterfaceList() != null) {
            fieldNames.add("Network_Interface_List");
        }
        if (obj.getOS() != null) {
            fieldNames.add("OS");
        }
        if(obj.getProcessor() != null){
            fieldNames.add("Processor");
        }
        if (obj.getSystemTime() != null) {
            fieldNames.add("System_Time");
        }
        if (obj.getTimezoneDST() != null) {
            fieldNames.add("Timezone_DST");
        }
        if (obj.getTimezoneStandard() != null) {
            fieldNames.add("Timezone_Standard");
        }
        if (obj.getTotalPhysicalMemory() != null) {
            fieldNames.add("Total_Physical_Memory");
        }
        if (obj.getUptime() != null) {
            fieldNames.add("Uptime");
        }
        if (obj.getUsername() != null) {
            fieldNames.add("Username");
        }

        if (obj instanceof WindowsSystem) {
            WindowsSystem winSysObj = (WindowsSystem) obj;

            if (winSysObj.getDomains() != null) {
                fieldNames.add("Domain");
            }
            if (winSysObj.getGlobalFlagList() != null) {
                fieldNames.add("Global_Flag_List");
            }
            if (winSysObj.getNetBIOSName() != null) {
                fieldNames.add("NetBIOS_Name");
            }
            if (winSysObj.getOpenHandleList() != null) {
                fieldNames.add("Open_Handle_List");
            }
            if (winSysObj.getWindowsDirectory() != null) {
                fieldNames.add("Windows_Directory");
            }
        }

        String warningStr = "";
        for (String name : fieldNames) {
            if (!warningStr.isEmpty()) {
                warningStr += ", ";
            }
            warningStr += name;
        }

        addWarning("Unsupported field(s): " + warningStr);
    }
}
