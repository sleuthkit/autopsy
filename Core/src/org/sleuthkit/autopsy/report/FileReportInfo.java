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
package org.sleuthkit.autopsy.report;

import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents Column Headers for FileList Reports.
 * 
 * Encapsulates functionality for getting column values from Files.
 * 
 * @author jwallace
 */
public enum FileReportInfo {
    
    NAME("Name") {
        @Override
        public String getValue(AbstractFile file) {
            return file.getName();
        }
    }, 
    FILE_EXT("File Extension") {
        @Override
        public String getValue(AbstractFile file) {
            String name = file.getName();
            int extIndex = name.lastIndexOf(".");
            return (extIndex == -1 ? "" : name.substring(extIndex));
        }
    },
    A_TIME("Last Accessed") {
        @Override
        public String getValue(AbstractFile file) {
            return file.getAtimeAsDate();
        }
    },
    CR_TIME("File Created") {
        @Override
        public String getValue(AbstractFile file) {
            return file.getCrtimeAsDate();
        }
    },
    M_TIME("Last Modified") {
        @Override
        public String getValue(AbstractFile file) {
            return file.getMtimeAsDate();
        }
    },
    SIZE("Size") {
        @Override
        public String getValue(AbstractFile file) {
            return String.valueOf(file.getSize());
        }
    },
    HASH_VALUE("Hash Value") {
        @Override
        public String getValue(AbstractFile file) {
            return file.getMd5Hash();
        }
    },
    FULL_PATH("Full Path") {
        @Override
        public String getValue(AbstractFile file) {
            try {
                return file.getUniquePath();
            } catch (TskCoreException ex) {
                return "";
            }
        }
    }, 
    PERMISSIONS("Permissions") {
        @Override
        public String getValue(AbstractFile file) {
            return file.getModesAsString();
        }
    },
    ADDRESS("Address") {
        @Override
        public String getValue(AbstractFile file) {
            return String.valueOf(file.getMetaAddr());
        }
    };
    
    private String name;
    
    FileReportInfo(String name) {
        this.name = name;
    }
    
    public String getName() {
        return this.name;
    }
    
    /**
     * Get the value of the column from the file.
     * 
     * @return 
     */
    public abstract String getValue(AbstractFile file);
}
