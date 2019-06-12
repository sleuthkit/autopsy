/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.configurelogicalimager;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * The class definition for the Logical Imager Rule.
 */
public class LogicalImagerRule {

    @Expose(serialize = true) 
    private final Boolean shouldAlert;
    @Expose(serialize = true) 
    private final Boolean shouldSave;
    @Expose(serialize = true) 
    private final String name;
    @Expose(serialize = true) 
    private final String description;
    @Expose(serialize = true) 
    private List<String> extensions = new ArrayList<>();
    @SerializedName("file-names")
    @Expose(serialize = true) 
    private List<String> filenames = new ArrayList<>();
    @SerializedName("folder-names")
    @Expose(serialize = true) 
    private List<String> paths = new ArrayList<>();
    @SerializedName("full-paths")
    @Expose(serialize = true) 
    private List<String> fullPaths = new ArrayList<>();
    @SerializedName("size-range")    
    @Expose(serialize = true) 
    final private Map<String, Integer> sizeRange = new HashMap<>();
    @SerializedName("date-range")    
    @Expose(serialize = true) 
    final private Map<String, Integer> dateRange = new HashMap<>();
    
    // The following fields should not be serialized, internal use only
    @Expose(serialize = false) 
    private Integer minFileSize;
    @Expose(serialize = false) 
    private Integer maxFileSize;
    @Expose(serialize = false) 
    private Integer minDays;
    @Expose(serialize = false) 
    private Integer minDate;
    @Expose(serialize = false) 
    private Integer maxDate;
    
    private LogicalImagerRule(Boolean shouldAlert, Boolean shouldSave, String name, String description,
            List<String> extensions,
            List<String> filenames,
            List<String> paths,
            List<String> fullPaths,
            Integer minFileSize,
            Integer maxFileSize,
            Integer minDays,
            Integer minDate,
            Integer maxDate
    ) {
        this.shouldAlert = shouldAlert;
        this.shouldSave = shouldSave;
        this.name = name;
        this.description = description;
        this.extensions = extensions;
        this.filenames = filenames;
        this.paths = paths;
        this.fullPaths = fullPaths;
        
        this.sizeRange.put("min", minFileSize); // NON-NLS
        this.minFileSize = minFileSize;
        this.sizeRange.put("max", maxFileSize); // NON-NLS
        this.maxFileSize = maxFileSize;
        this.dateRange.put("min-days", minDays); // NON-NLS
        this.minDays = minDays;
        this.dateRange.put("min-date", minDate); // NON-NLS
        this.minDate = minDate;
        this.dateRange.put("max-date", maxDate); // NON-NLS
        this.maxDate = maxDate;
    }

    public LogicalImagerRule() {
        this.shouldAlert = false; // default
        this.shouldSave = true; // default
        this.description = null;
        this.name = null;
    }
    
    public Boolean isShouldAlert() {
        return shouldAlert;
    }

    public Boolean isShouldSave() {
        return shouldSave;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public List<String> getPaths() {
        return paths;
    }

    public List<String> getFullPaths() {
        return fullPaths;
    }

    public Integer getMinFileSize() {
        return minFileSize;
    }

    public Integer getMaxFileSize() {
        return maxFileSize;
    }

    public Integer getMinDays() {
        return minDays;
    }

    public Integer getMinDate() {
        return minDate;
    }

    public Integer getMaxDate() {
        return maxDate;
    }

    /**
     * Builder class
     */
    public static class Builder {
        private Boolean shouldAlert = null;
        private Boolean shouldSave = null;
        private String name = null;
        private String description = null;
        private List<String> extensions = null;
        private List<String> filenames = null;        
        private List<String> paths = null;        
        private List<String> fullPaths = null;        
        private Integer minFileSize = null;
        private Integer maxFileSize = null;
        private Integer minDays = null;
        private Integer minDate = null;
        private Integer maxDate = null;
        
        public Builder shouldAlert(boolean shouldAlert) {
            this.shouldAlert = shouldAlert;
            return this;
        }
        
        public Builder shouldSave(boolean shouldSave) {
            this.shouldSave = shouldSave;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder extensions(List<String> extensions) {
            this.extensions = extensions;
            return this;
        }
        
        public Builder filenames(List<String> filenames) {
            this.filenames = filenames;
            return this;
        }
        
        public Builder paths(List<String> paths) {
            this.paths = paths;
            return this;
        }
        
        public Builder fullPaths(List<String> fullPaths) {
            this.fullPaths = fullPaths;
            return this;
        }
        
        public Builder minFileSize(Integer minFileSize) {
            this.minFileSize = minFileSize;
            return this;
        }
        
        public Builder maxFileSize(Integer maxFileSize) {
            this.maxFileSize = maxFileSize;
            return this;
        }
        
        public Builder minDays(Integer minDays) {
            this.minDays = minDays;
            return this;
        }
        
        public Builder minDate(Integer minDate) {
            this.minDate = minDate;
            return this;
        }
        
        public Builder maxDate(Integer maxDate) {
            this.maxDate = maxDate;
            return this;
        }
        
        public LogicalImagerRule build() {
            return new LogicalImagerRule(shouldAlert, shouldSave, name, description, 
                    extensions, filenames, paths, fullPaths,
                    minFileSize, maxFileSize,
                    minDays, minDate, maxDate
            );
        }
    }
}
