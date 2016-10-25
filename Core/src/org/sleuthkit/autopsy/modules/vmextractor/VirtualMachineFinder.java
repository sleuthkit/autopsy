/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.vmextractor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import javax.swing.filechooser.FileFilter;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Virtual machine file finder 
 */
public final class VirtualMachineFinder {

    private static final Logger logger = Logger.getLogger(VirtualMachineFinder.class.getName());

    private static final int MAX_VMDK_DESCRIPTOR_FILE_SIZE_BYTES = 10000;
    private static final int MIN_VMDK_EXTENT_DESCRIPTOR_FIELDS = 4; // See readExtentFilesFromVmdkDescriptorFile() for details
    private static final int FILE_NAME_FIELD_INDX = 3; // See readExtentFilesFromVmdkDescriptorFile() for details

    private static final GeneralFilter virtualMachineFilter = new GeneralFilter(GeneralFilter.VIRTUAL_MACHINE_EXTS, GeneralFilter.VIRTUAL_MACHINE_DESC);
    private static final List<FileFilter> vmFiltersList = new ArrayList<>();

    static {
        vmFiltersList.add(virtualMachineFilter);
    }

    private static final List<String> VMDK_EXTS = Arrays.asList(new String[]{".vmdk"}); //NON-NLS
    private static final GeneralFilter vmdkFilter = new GeneralFilter(VMDK_EXTS, "");
    private static final List<FileFilter> vmdkFiltersList = new ArrayList<>();

    static {
        vmdkFiltersList.add(vmdkFilter);
    }

    public static final boolean isVirtualMachine(String fileName) {
        return isAcceptedByFiler(new File(fileName), vmFiltersList);
    }

    /**
     * Identifies virtual machine files for ingest.
     *
     * @param imageFolderPath Absolute path to the folder to be analyzed
     *
     * @return List of VM files to be ingested
     */
    public static List<String> identifyVirtualMachines(Path imageFolderPath) {

        // get a list of all files in the folder
        List<String> files = getAllFilesInFolder(imageFolderPath.toString());
        if (files.isEmpty()) {
            return Collections.emptyList();
        }

        // remove all non-vm files
        for (Iterator<String> iterator = files.iterator(); iterator.hasNext();) {
            String file = iterator.next();
            if (!isVirtualMachine(file)) {
                iterator.remove();
            }
        }

        // identify VMDK descriptor files - VMDK files with size less than 10KB
        List<String> extentFiles = new ArrayList<>();
        for (String fileName : files) {
            File file = imageFolderPath.resolve(fileName).toFile();
            if (isAcceptedByFiler(new File(fileName), vmdkFiltersList) && file.exists() && file.length() < MAX_VMDK_DESCRIPTOR_FILE_SIZE_BYTES) {
                // this is likely a VMDK descriptor file - read vmdk extent files listed in it
                extentFiles.addAll(readExtentFilesFromVmdkDescriptorFile(file));
            }
        }
        // remove VMDK extent files from list of vm files to proces
        files.removeAll(extentFiles);

        // what remains on the list is either a vmdk descriptor file or a VMDK file that doesn't have a descriptor file or different type of VM (e.g. VHD)
        return files;
    }

    /**
     * Opens VMDK descriptor file, finds and returns a list of all VMDK extent
     * files listed in the descriptor file.
     *
     * @param file VMDK descriptor file to read
     *
     * @return List of VMDK extent file names listed in the descriptor file
     */
    private static List<String> readExtentFilesFromVmdkDescriptorFile(File file) {

        List<String> extentFiles = new ArrayList<>();

        // remove from the list all VMDK files that are listed in the descriptor file
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            while (null != line) {
                // The extent descriptions provide the following key information:
                // Access – may be RW, RDONLY, or NOACCESS
                // Size in sectors – a sector is 512 bytes
                // Type of extent – may be FLAT, SPARSE, ZERO, VMFS, VMFSSPARSE, VMFSRDM, or VMFSRAW.
                // Filename
                // Offset – the offset value is specified only for flat extents and corresponds to the offset in the file or device
                // where the guest operating system’s data is located.
                // Example: RW 4192256 SPARSE "win7-ult-vm-0-s001.vmdk"

                String[] splited = line.split(" ");
                if (splited.length < MIN_VMDK_EXTENT_DESCRIPTOR_FIELDS) {
                    // line doesn't have enough fields, can't be an extent descriptor
                    continue;
                }
                if (splited[0].equals("RW") || splited[0].equals("RDONLY") || splited[0].equals("NOACCESS")) { //NON-NLS
                    // found an extent descriptor
                    // remove quotation marks around the file name
                    String extentFileName = splited[FILE_NAME_FIELD_INDX].replace("\"", "");

                    // add extent file to list of extent files
                    extentFiles.add(extentFileName);
                }
                line = br.readLine();
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, String.format("Error while parsing vmdk descriptor file %s", file.toString()), ex); //NON-NLS
        }
        return extentFiles;
    }

    private static boolean isAcceptedByFiler(File file, List<FileFilter> filters) {

        for (FileFilter filter : filters) {
            if (filter.accept(file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a list of all file names in the folder of interest. Sub-folders
     * are excluded.
     *
     * @param path Absolute path of the folder of interest
     *
     * @return List of all file names in the folder of interest
     */
    private static List<String> getAllFilesInFolder(String path) {
        // only returns files, skips folders
        File file = new File(path);
        String[] files = file.list((File current, String name) -> new File(current, name).isFile());
        if (files == null) {
            // null is returned when folder doesn't exist. need to check this condition, otherwise there is NullPointerException when converting to List
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(files));
    }

    /**
     * Prevent instantiation of this utility class.
     */
    private VirtualMachineFinder() {
    }
    
}
