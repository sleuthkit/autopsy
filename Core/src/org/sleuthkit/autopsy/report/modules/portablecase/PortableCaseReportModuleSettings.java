/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.portablecase;

import org.sleuthkit.autopsy.report.ReportModuleSettings;
import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.datamodel.TagName;

/**
 * Settings for the HTML report module.
 */
public class PortableCaseReportModuleSettings implements ReportModuleSettings {

    private static final long serialVersionUID = 1L;

    private final List<TagName> tagNames = new ArrayList<>();
    private final List<String> setNames = new ArrayList<>();
    private boolean compress;
    private ChunkSize chunkSize;
    private boolean allTagsSelected;
    private boolean allSetsSelected;
    private boolean shouldIncludeApplication;
    
        /**
     * Enum for storing the display name for each chunk type and the
     * parameter needed for 7-Zip.
     */
    public enum ChunkSize {
        
        NONE("Do not split", ""), // NON-NLS
        ONE_HUNDRED_MB("Split into 100 MB chunks", "100m"),
        CD("Split into 700 MB chunks (CD)", "700m"),
        ONE_GB("Split into 1 GB chunks", "1000m"),
        DVD("Split into 4.5 GB chunks (DVD)", "4500m"); // NON-NLS
        
        private final String displayName;
        private final String sevenZipParam;

        /**
         * Create a chunk size object.
         * 
         * @param displayName
         * @param sevenZipParam 
         */
        private ChunkSize(String displayName, String sevenZipParam) {
            this.displayName = displayName;
            this.sevenZipParam = sevenZipParam;
        }
        
        String getDisplayName() {
            return displayName;
        }
        
        String getSevenZipParam() {
            return sevenZipParam;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }

    public PortableCaseReportModuleSettings() {
        this.compress = false;
        this.chunkSize = ChunkSize.NONE;
        this.allTagsSelected = true;
        this.allSetsSelected = true;
        this.shouldIncludeApplication = false;
    }

    PortableCaseReportModuleSettings(List<String> setNames, List<TagName> tagNames,
            boolean compress, ChunkSize chunkSize, boolean allTagsSelected, boolean allSetsSelected) {
        this.setNames.addAll(setNames);
        this.tagNames.addAll(tagNames);
        this.compress = compress;
        this.chunkSize = chunkSize;
        this.allTagsSelected = allTagsSelected;
        this.allSetsSelected = allSetsSelected;
        this.shouldIncludeApplication = false;
    }

    @Override
    public long getVersionNumber() {
        return serialVersionUID;
    }

    public void updateSetNames(List<String> setNames) {
        this.setNames.clear();
        this.setNames.addAll(setNames);
    }

    public void updateTagNames(List<TagName> tagNames) {
        this.tagNames.clear();
        this.tagNames.addAll(tagNames);
    }

    public void updateCompression(boolean compress, ChunkSize chunkSize) {
        this.compress = compress;
        this.chunkSize = chunkSize;
    }

    public boolean isValid() {
        return ((allTagsSelected || allSetsSelected || (!tagNames.isEmpty()) || (!setNames.isEmpty())));
    }

    public List<String> getSelectedSetNames() {
        return new ArrayList<>(setNames);
    }

    public List<TagName> getSelectedTagNames() {
        return new ArrayList<>(tagNames);
    }

    public boolean shouldCompress() {
        return compress;
    }

    public ChunkSize getChunkSize() {
        return chunkSize;
    }

    public boolean areAllTagsSelected() {
        return allTagsSelected;
    }

    public boolean areAllSetsSelected() {
        return allSetsSelected;
    }
    
    public boolean includeApplication() {
        return shouldIncludeApplication;
    }

    /**
     * @param allTagsSelected the allTagsSelected to set
     */
    public void setAllTagsSelected(boolean allTagsSelected) {
        this.allTagsSelected = allTagsSelected;
    }

    /**
     * @param allSetsSelected the allSetsSelected to set
     */
    public void setAllSetsSelected(boolean allSetsSelected) {
        this.allSetsSelected = allSetsSelected;
    }
    
    public void setIncludeApplication(boolean includeApplication) {
        this.shouldIncludeApplication = includeApplication;
    }

}
