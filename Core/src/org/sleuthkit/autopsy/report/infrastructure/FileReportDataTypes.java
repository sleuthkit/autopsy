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
package org.sleuthkit.autopsy.report.infrastructure;

import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Represents Column Headers for FileList Reports.
 *
 * Encapsulates functionality for getting column values from Files.
 *
 * @author jwallace
 */
public enum FileReportDataTypes {

    NAME(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.filename.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getName();
                }
            },
    FILE_EXT(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.fileExt.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    String name = file.getName();
                    int extIndex = name.lastIndexOf(".");
                    return (extIndex == -1 ? "" : name.substring(extIndex));
                }
            },
    FILE_TYPE(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.fileType.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getMetaTypeAsString();
                }
            },
    DELETED(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.isDel.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    if (file.getMetaFlagsAsString().equals(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC.toString())) {
                        return "yes"; //NON-NLS
                    }
                    return "";
                }
            },
    A_TIME(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.aTime.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getAtimeAsDate();
                }
            },
    CR_TIME(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.crTime.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getCrtimeAsDate();
                }
            },
    M_TIME(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.mTime.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getMtimeAsDate();
                }
            },
    SIZE(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.size.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return String.valueOf(file.getSize());
                }
            },
    ADDRESS(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.address.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return String.valueOf(file.getMetaAddr());
                }
            },
    HASH_VALUE(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.hash.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getMd5Hash();
                }
            },
    KNOWN_STATUS(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.knownStatus.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getKnown().getName();
                }
            },
    PERMISSIONS(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.perms.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    return file.getModesAsString();
                }
            },
    FULL_PATH(NbBundle.getMessage(FileReportDataTypes.class, "FileReportDataTypes.path.text")) {
                @Override
                public String getValue(AbstractFile file) {
                    try {
                        return file.getUniquePath();
                    } catch (TskCoreException ex) {
                        return "";
                    }
                }
            };

    private String name;

    FileReportDataTypes(String name) {
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
