/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2015 Basis Technology Corp.
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
 * Virtual machine file finder utility
 */
public class VirtualMachineFinderUtility {

    private static final Logger logger = Logger.getLogger(VirtualMachineFinderUtility.class.getName());

    private static final int MAX_VMDK_DESCRIPTOR_FILE_SIZE_BYTES = 10000;
    private static final int MIN_VMDK_EXTENT_DESCRIPTOR_FIELDS_NUM = 4; // See readExtentFilesFromVmdkDescriptorFile() for details
    private static final int FILE_NAME_FIELD_INDX_IN_EXTENT_DESCRIPTOR = 3; // See readExtentFilesFromVmdkDescriptorFile() for details

    private static final GeneralFilter virtualMachineFilter = new GeneralFilter(GeneralFilter.VIRTUAL_MACHINE_EXTS, GeneralFilter.VIRTUAL_MACHINE_DESC);
    private static final List<FileFilter> vmFiltersList = new ArrayList<>();

    static {
        vmFiltersList.add(virtualMachineFilter);
    }

    private static final List<String> VMDK_EXTS = Arrays.asList(new String[]{".vmdk"});
    private static final GeneralFilter vmdkFilter = new GeneralFilter(VMDK_EXTS, "");
    private static final List<FileFilter> vmdkFiltersList = new ArrayList<>();

    static {
        vmdkFiltersList.add(vmdkFilter);
    }

    private static boolean isVirtualMachine(String fileName) {
        // is file a virtual machine
        if (!isAcceptedByFiler(new File(fileName), vmFiltersList)) {
            return false;
        }
        return true;
    }

    /**
     * Identifies virtual machine files for ingest given a list of files in a
     * folder.
     *
     * @param imageFolderPath Absolute path to the folder containing the files
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
            String line;
            while ((line = br.readLine()) != null) {
                // The extent descriptions provide the following key information:
                // Access – may be RW, RDONLY, or NOACCESS
                // Size in sectors – a sector is 512 bytes
                // Type of extent – may be FLAT, SPARSE, ZERO, VMFS, VMFSSPARSE, VMFSRDM, or VMFSRAW.
                // Filename
                // Offset – the offset value is specified only for flat extents and corresponds to the offset in the file or device
                // where the guest operating system’s data is located.
                // Example: RW 4192256 SPARSE "win7-ult-vm-0-s001.vmdk"

                String[] splited = line.split(" ");
                if (splited.length < MIN_VMDK_EXTENT_DESCRIPTOR_FIELDS_NUM) {
                    // line doesn't have enough fields, can't be an extent descriptor
                    continue;
                }
                if (splited[0].equals("RW") || splited[0].equals("RDONLY") || splited[0].equals("NOACCESS")) {
                    // found an extent descriptor
                    // remove quotation marks around the file name
                    String extentFileName = splited[FILE_NAME_FIELD_INDX_IN_EXTENT_DESCRIPTOR].replace("\"", "");

                    // add extent file to list of extent files
                    extentFiles.add(extentFileName);
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, String.format("Error while parsing vmdk descriptor file %s", file.toString()), ex);
        }
        return extentFiles;
    }

    /**
     * Identifies whether a vmdk file is part of split vmdk image
     *
     * @param fileName Name of the vmdk file
     *
     * @return True if the file is part of split vmdk image, false if not
     */
    private static boolean isPartOfSplitVMDKImage(String fileName) {

        // only need to worry about ".vmdk" images
        if (!isAcceptedByFiler(new File(fileName), vmdkFiltersList)) {
            return false;
        }

        // this needs to identify and handle different VMDK scenarios:
        //  i  single image in a single file
        // ii. Single image split over multiple files - just need to pass the first to TSK and it will combine the split image files.
        //       Note there may be more than  than one split images in a single dir, 
        //       e.g. icrd-te-google.vmdk, icrd-te-google-s001.vmdk, icrd-te-google-s002.vmdk... (split sparse vmdk format)
        //       e.g. win7-ult-vm.vmdk, win7-ult-vm-f001.vmdk, win7-ult-vm-f002.vmdk... (split flat vmdk format)
        String fName = fileName.toLowerCase();
        int lastPeriod = fName.lastIndexOf('.');
        if (-1 == lastPeriod) {
            return false;
        }
        String fNameNoExt = fName.substring(0, lastPeriod);
        return fNameNoExt.matches(".*-[fs]\\d+$");  // anything followed by "-" then either "f" or "s" and followed by digits at the end of the string 
    }

    private static boolean isAcceptedByFiler(File file, List<FileFilter> filters) {

        for (FileFilter filter : filters) {
            if (filter.accept(file)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getAllFilesInFolder(String path) {
        // only returns files, skips folders
        File file = new File(path);
        String[] files = file.list((File current, String name) -> new File(current, name).isFile());
        return new ArrayList<>(Arrays.asList(files));
    }
}
