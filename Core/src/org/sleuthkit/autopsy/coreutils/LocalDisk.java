/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Representation of a PhysicalDisk or partition.
 */
public class LocalDisk {

    private String name;
    private String path;
    private long size;
    private String mountPoint;
    private static final Logger logger = Logger.getLogger(LocalDisk.class.getName());

    public LocalDisk(String name, String path, long size) {
        this.name = name;
        this.path = path;
        this.size = size;
        mountPoint = "";
        if (PlatformUtil.isLinuxOS() ) {
            findLinuxMointPoint(this.path);
        }
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }
   
    /**
     * Returns details about the mount point 
     * of the drive as well as if it is an Autopsy 
     * config or iso.
     * 
     * NOTE: Currently only works for linux.
     */
    public String getDetail() {
        if(isConfigDrive()) {
            return mountPoint + ", " + "Autopsy Config";
        }
        return mountPoint;
    }

    public String getReadableSize() {
        int unit = 1024;
        if (size < unit) {
            return size + " B"; //NON-NLS
        }
        int exp = (int) (Math.log(size) / Math.log(unit));
        String pre = "KMGTPE".charAt(exp - 1) + ""; //NON-NLS
        return String.format("%.1f %sB", size / Math.pow(unit, exp), pre); //NON-NLS
    }

    @Override
    public String toString() {
        return name + ": " + getReadableSize();
    }

    private void findLinuxMointPoint(String path) {
        try {
            List<String> commandLine = new ArrayList<>();
            commandLine.add("/bin/bash");
            commandLine.add("-c");
            commandLine.add("df -h | grep " + path + " | awk '{print $6}'");

            ProcessBuilder pb = new ProcessBuilder(commandLine);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            this.mountPoint = builder.toString();
            process.destroy();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to find the mount point for the device", ex);
        }
    }
    
    /**
     * Does this drive contain an AutopsyConfig folder?
     * requires the mount point to be known
     */
    private boolean isConfigDrive() {        
        Path path = Paths.get(this.mountPoint, "AutopsyConfig");
        File configFile = new File(path.toString());
        return configFile.exists();
    }
}
