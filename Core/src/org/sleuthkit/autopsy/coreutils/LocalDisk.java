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

/**
 * Representation of a PhysicalDisk or partition.
 */
public class LocalDisk {

    private String name;
    private String path;
    private long size;

    public LocalDisk(String name, String path, long size) {
        this.name = name;
        this.path = path;
        this.size = size;
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

}
