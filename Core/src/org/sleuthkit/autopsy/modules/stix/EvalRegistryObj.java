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
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;

import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.mitre.cybox.objects.WindowsRegistryKey;
import org.mitre.cybox.common_2.ConditionTypeEnum;
import com.williballenthin.rejistry.*;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 *
 */
class EvalRegistryObj extends EvaluatableObject {

    private final WindowsRegistryKey obj;
    private final List<RegistryFileInfo> regFiles = new ArrayList<RegistryFileInfo>();

    public EvalRegistryObj(WindowsRegistryKey a_obj, String a_id, String a_spacing, List<RegistryFileInfo> a_regFiles) {
        obj = a_obj;
        id = a_id;
        spacing = a_spacing;
        regFiles.addAll(a_regFiles);
    }

    private EvalRegistryObj() {
        obj = null;
        id = null;
        spacing = "";
    }

    @Override
    public synchronized ObservableResult evaluate() {

        setWarnings("");

        // Key name is required
        if (obj.getKey() == null) {
            return new ObservableResult(id, "RegistryObject: No key found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        // For now, only support a full string match
        if (!((obj.getKey().getCondition() == null)
                || (obj.getKey().getCondition() == ConditionTypeEnum.EQUALS))) {
            return new ObservableResult(id, "RegistryObject: Can not support condition " + obj.getKey().getCondition() //NON-NLS
                    + " on Key field", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        setUnsupportedFieldWarnings();

        // Make a list of hives to test
        List<RegistryFileInfo> hiveList = new ArrayList<RegistryFileInfo>();
        if (obj.getHive() == null) {
            // If the hive field is missing, add everything
            hiveList.addAll(regFiles);
        } else if (obj.getHive().getValue().toString().startsWith("HKEY")) { //NON-NLS
            // If the hive name is HKEY_LOCAL_MACHINE, add the ones from the config directory.
            // Otherwise, add the others
            for (RegistryFileInfo regFile : regFiles) {
                if (regFile.abstractFile.getParentPath() != null) {
                    Pattern pattern = Pattern.compile("system32", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(regFile.abstractFile.getParentPath());
                    if (matcher.find()) {
                        // Looking for system files and found one, so add it to the list
                        if (obj.getHive().getValue().toString().equalsIgnoreCase("HKEY_LOCAL_MACHINE")) { //NON-NLS
                            hiveList.add(regFile);
                        }
                    } else {
                        // Looking for non-system files and found one, so add it to the list
                        if (!obj.getHive().getValue().toString().equalsIgnoreCase("HKEY_LOCAL_MACHINE")) { //NON-NLS
                            hiveList.add(regFile);
                        }
                    }
                }
            }
        } else {
            // Otherwise, try to match the name
            String stixHiveName = obj.getHive().getValue().toString();

            // The temp files will end \Temp\STIX\(hive)_(number)            
            Pattern pattern = Pattern.compile("Temp.STIX." + stixHiveName, Pattern.CASE_INSENSITIVE);

            for (RegistryFileInfo hive : regFiles) {
                Matcher matcher = pattern.matcher(hive.tempFileName);
                if (matcher.find()) {
                    hiveList.add(hive);
                }
            }

            // If nothing matched, add all the files
            if (hiveList.isEmpty()) {
                hiveList.addAll(regFiles);
            }
        }

        // This is unlikely to happen unless we have no registry files to test against
        if (hiveList.isEmpty()) {
            return new ObservableResult(id, "RegistryObject: No matching registry hives found", //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        for (RegistryFileInfo hive : hiveList) {
            try {
                ObservableResult result = testRegistryFile(hive);
                if (result.isTrue()) {
                    return result;
                }
            } catch (Exception ex) {
                // The registry parser seems to throw lots of different types of exceptions,
                // so make sure to catch them all by this point. Malformed registry files
                // in particular cause problems.
                addWarning("Error processing registry file " + hive); //NON-NLS
            }
        }

        if (obj.getHive() == null) {
            return new ObservableResult(id, "RegistryObject: Could not find key " + obj.getKey().getValue(), //NON-NLS
                    spacing, ObservableResult.ObservableState.FALSE, null);
        }
        return new ObservableResult(id, "RegistryObject: Could not find key " + obj.getKey().getValue() //NON-NLS
                + " in hive " + obj.getHive().getValue(), //NON-NLS
                spacing, ObservableResult.ObservableState.FALSE, null);

    }

    /**
     * Test the Registry object against one registry file.
     *
     * @param a_regInfo The registry file
     *
     * @return Result of the test
     */
    private ObservableResult testRegistryFile(RegistryFileInfo a_regInfo) {
        try {
            RegistryKey root = openRegistry(a_regInfo.tempFileName);
            RegistryKey result = findKey(root, obj.getKey().getValue().toString());

            if (result == null) {

                // Take another shot looking for the key minus the first part of the path (sometimes the
                // hive file name is here). This should only happen if the hive name started
                // with "HKEY"
                if ((obj.getHive() != null)
                        && obj.getHive().getValue().toString().startsWith("HKEY")) { //NON-NLS
                    String[] parts = obj.getKey().getValue().toString().split("\\\\");
                    String newKey = "";
                    for (int i = 1; i < parts.length; i++) {
                        if (newKey.length() > 0) {
                            newKey += "\\";
                        }
                        newKey += parts[i];
                    }
                    result = findKey(root, newKey);
                }

                if (result == null) {
                    return new ObservableResult(id, "RegistryObject: Could not find key " + obj.getKey().getValue(), //NON-NLS
                            spacing, ObservableResult.ObservableState.FALSE, null);
                }
            }

            if ((obj.getValues() == null) || (obj.getValues().getValues().isEmpty())) {
                // No values to test
                List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                artData.add(new StixArtifactData(a_regInfo.abstractFile.getId(), id, "Registry")); //NON-NLS
                return new ObservableResult(id, "RegistryObject: Found key " + obj.getKey().getValue(), //NON-NLS
                        spacing, ObservableResult.ObservableState.TRUE, artData);
            }

            // Test all the values
            for (org.mitre.cybox.objects.RegistryValueType stixRegValue : obj.getValues().getValues()) {
                try {
                    for (RegistryValue valFromFile : result.getValueList()) {

                        // Test if the name field matches (if present)
                        boolean nameSuccess = true; // True if the name matches or isn't present
                        if (stixRegValue.getName() != null) {
                            try {
                                nameSuccess = compareStringObject(stixRegValue.getName(), valFromFile.getName());
                            } catch (UnsupportedEncodingException ex) {
                                nameSuccess = false;
                            }
                        }

                        boolean valueSuccess = true;
                        if (nameSuccess && (stixRegValue.getData() != null)) {
                            switch (valFromFile.getValueType()) {
                                case REG_SZ:
                                case REG_EXPAND_SZ:

                                    try {
                                        valueSuccess = compareStringObject(stixRegValue.getData(),
                                                valFromFile.getValue().getAsString());
                                    } catch (UnsupportedEncodingException ex) {
                                        valueSuccess = false;
                                    }
                                    break;
                                case REG_DWORD:
                                case REG_BIG_ENDIAN:
                                case REG_QWORD:

                                    // Only support "equals" for now.
                                    if ((stixRegValue.getData().getCondition() == null)
                                            || (stixRegValue.getData().getCondition() == ConditionTypeEnum.EQUALS)) {

                                        // Try to convert the STIX string to a long
                                        try {
                                            long stixValue = Long.decode(stixRegValue.getData().getValue().toString());

                                            try {
                                                valueSuccess = (stixValue == valFromFile.getValue().getAsNumber());
                                            } catch (UnsupportedEncodingException ex) {
                                                valueSuccess = false;
                                            }
                                        } catch (NumberFormatException ex) {
                                            // We probably weren't looking at a numeric field to begin with,
                                            // so getting this exception isn't really an error.
                                            valueSuccess = false;
                                        }
                                    } else {
                                        valueSuccess = false;
                                    }

                                    break;
                                default:
                                // Nothing to do here. These are the types we don't handle:
                                // REG_BIN, REG_FULL_RESOURCE_DESCRIPTOR, REG_LINK, REG_MULTI_SZ, REG_NONE,
                                // REG_RESOURCE_LIST, REG_RESOURCE_REQUIREMENTS_LIST
                            }
                        }

                        if (nameSuccess && valueSuccess) {
                            // Found a match for all values
                            List<StixArtifactData> artData = new ArrayList<StixArtifactData>();
                            artData.add(new StixArtifactData(a_regInfo.abstractFile.getId(), id, "Registry")); //NON-NLS
                            return new ObservableResult(id, "RegistryObject: Found key " + obj.getKey().getValue() //NON-NLS
                                    + " and value " + stixRegValue.getName().getValue().toString() //NON-NLS
                                    + " = " + stixRegValue.getData().getValue().toString(),
                                    spacing, ObservableResult.ObservableState.TRUE, artData);
                        }
                    }
                } catch (Exception ex) {
                    // Broad catch here becase the registry parser can create all kinds of exceptions beyond what it reports.
                    return new ObservableResult(id, "RegistryObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                            spacing, ObservableResult.ObservableState.INDETERMINATE, null);
                }
            }
        } catch (TskCoreException ex) {
            return new ObservableResult(id, "RegistryObject: Exception during evaluation: " + ex.getLocalizedMessage(), //NON-NLS
                    spacing, ObservableResult.ObservableState.INDETERMINATE, null);
        }

        return new ObservableResult(id, "RegistryObject: Not done", //NON-NLS
                spacing, ObservableResult.ObservableState.INDETERMINATE, null);
    }

    public RegistryKey openRegistry(String hive) throws TskCoreException {

        try {
            RegistryHiveFile regFile = new RegistryHiveFile(new File(hive));
            RegistryKey root = regFile.getRoot();
            return root;
        } catch (IOException ex) {
            throw new TskCoreException("Error opening registry file - " + ex.getLocalizedMessage()); //NON-NLS
        } catch (RegistryParseException ex) {
            throw new TskCoreException("Error opening root node of registry - " + ex.getLocalizedMessage()); //NON-NLS
        }
    }

    /**
     * Go down the registry tree to find a key with the given name.
     *
     * @param root Root of the registry hive
     * @param name Name of the subkey to seach for
     *
     * @return The matching subkey or null if not found
     */
    public RegistryKey findKey(RegistryKey root, String name) {

        RegistryKey currentKey = root;

        // Split the key name into parts
        String[] parts = name.split("\\\\");
        for (String part : parts) {

            if (part.length() > 0) {
                try {
                    currentKey = currentKey.getSubkey(part);
                } catch (Exception ex) {
                    // We get an exception if the value wasn't found (not a RegistryParseException). 
                    // There doesn't seem to be a cleaner way to test for existance without cycling though
                    // everything ourselves. (Broad catch because things other than RegistryParseException 
                    // can happen)
                    return null;
                }
            }
        }

        // If we make it this far, we've found it
        return currentKey;
    }

    /**
     * Copy all registry hives to the temp directory and return the list of
     * created files.
     *
     * @return Paths to copied registry files.
     */
    public static List<RegistryFileInfo> copyRegistryFiles() throws TskCoreException {

        // First get all the abstract files
        List<AbstractFile> regFilesAbstract = findRegistryFiles();

        // List to hold all the extracted file names plus their abstract file
        List<RegistryFileInfo> regFilesLocal = new ArrayList<RegistryFileInfo>();

        // Make the temp directory
        String tmpDir;
        try {
           tmpDir = Case.getOpenCase().getTempDirectory() + File.separator + "STIX"; //NON-NLS
        } catch (NoCurrentCaseException ex) { 
            throw new TskCoreException(ex.getLocalizedMessage());
        }
        File dir = new File(tmpDir);
        if (dir.exists() == false) {
            dir.mkdirs();
        }

        long index = 1;
        for (AbstractFile regFile : regFilesAbstract) {
            String regFileName = regFile.getName();
            String regFileNameLocal = tmpDir + File.separator + regFileName + "_" + index;
            File regFileNameLocalFile = new File(regFileNameLocal);
            try {
                // Don't save any unallocated versions
                if (regFile.getMetaFlagsAsString().contains("Allocated")) { //NON-NLS
                    ContentUtils.writeToFile(regFile, regFileNameLocalFile);
                    regFilesLocal.add(new EvalRegistryObj().new RegistryFileInfo(regFile, regFileNameLocal));
                }
            } catch (IOException ex) {
                throw new TskCoreException(ex.getLocalizedMessage());
            }
            index++;
        }

        return regFilesLocal;
    }

    /**
     * Search for the registry hives on the system. Mostly copied from
     * RecentActivity
     */
    private static List<AbstractFile> findRegistryFiles() throws TskCoreException {
        List<AbstractFile> registryFiles = new ArrayList<AbstractFile>();
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) { 
            throw new TskCoreException(ex.getLocalizedMessage());
        }
        org.sleuthkit.autopsy.casemodule.services.FileManager fileManager = openCase.getServices().getFileManager();

        for (Content ds : openCase.getDataSources()) {

            // find the user-specific ntuser-dat files
            registryFiles.addAll(fileManager.findFiles(ds, "ntuser.dat")); //NON-NLS

            // find the system hives
            String[] regFileNames = new String[]{"system", "software", "security", "sam"}; //NON-NLS
            for (String regFileName : regFileNames) {
                List<AbstractFile> allRegistryFiles = fileManager.findFiles(ds, regFileName, "/system32/config"); //NON-NLS
                for (AbstractFile regFile : allRegistryFiles) {
                    // Don't want anything from regback
                    if (!regFile.getParentPath().contains("RegBack")) { //NON-NLS
                        registryFiles.add(regFile);
                    }
                }
            }
        }

        return registryFiles;
    }

    private void setUnsupportedFieldWarnings() {
        List<String> fieldNames = new ArrayList<String>();

        if (obj.getNumberValues() != null) {
            fieldNames.add("Number_Values"); //NON-NLS
        }
        if (obj.getModifiedTime() != null) {
            fieldNames.add("Modified_Time"); //NON-NLS
        }
        if (obj.getCreatorUsername() != null) {
            fieldNames.add("Creator_Username"); //NON-NLS
        }
        if (obj.getHandleList() != null) {
            fieldNames.add("Handle_List"); //NON-NLS
        }
        if (obj.getNumberSubkeys() != null) {
            fieldNames.add("Number_Subkeys"); //NON-NLS
        }
        if (obj.getSubkeys() != null) {
            fieldNames.add("Subkeys"); //NON-NLS
        }
        if (obj.getByteRuns() != null) {
            fieldNames.add("Byte_Runs"); //NON-NLS
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

    /**
     * Class to keep track of the abstract file and temp file that goes with
     * each registry hive.
     */
    public class RegistryFileInfo {

        private final AbstractFile abstractFile;
        private final String tempFileName;

        public RegistryFileInfo(AbstractFile a_abstractFile, String a_tempFileName) {
            abstractFile = a_abstractFile;
            tempFileName = a_tempFileName;
        }

    }
}
