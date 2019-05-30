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

import java.util.HashSet;
import java.util.Set;

/**
 *
 * The class definition for the Logical Imager Rule.
 */
public class LogicalImagerRule {

    private final Boolean shouldAlert;
    private final Boolean shouldSave;
    private final String description;
    private Set<String> extensions = new HashSet<>();
    private Set<String> filenames = new HashSet<>();
    private Set<String> paths = new HashSet<>();
    private Set<String> fullPaths = new HashSet<>();
    private Integer minFileSize = 0;
    private Integer maxFileSize = 0;
    private Integer minDays = 0;
    private String minDate;
    private String maxDate;
    
    LogicalImagerRule(Boolean shouldAlert, Boolean shouldSave, String description,
            Set<String> extensions,
            Set<String> filenames,
            Set<String> paths,
            Set<String> fullPaths,
            Integer minFileSize,
            Integer maxFileSize,
            Integer minDays,
            String minDate,
            String maxDate
    ) {
        this.shouldAlert = shouldAlert;
        this.shouldSave = shouldSave;
        this.description = description;
        this.extensions = extensions;
        this.filenames = filenames;
        this.paths = paths;
        this.fullPaths = fullPaths;
        this.minFileSize = minFileSize;
        this.maxFileSize = maxFileSize;
        this.minDays = minDays;
        this.minDate = minDate;
        this.maxDate = maxDate;
    }

    public LogicalImagerRule() {
        this.shouldAlert = true;
        this.shouldSave = false;
        this.description = null;
    }
    
    public Boolean isShouldAlert() {
        return shouldAlert;
    }

    public Boolean isShouldSave() {
        return shouldSave;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getExtensions() {
        return extensions;
    }

    public Set<String> getFilenames() {
        return filenames;
    }

    public Set<String> getPaths() {
        return paths;
    }

    public Set<String> getFullPaths() {
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

    public String getMinDate() {
        return minDate;
    }

    public String getMaxDate() {
        return maxDate;
    }

    public boolean validatePath(String path) {
        return !path.contains("\\");
    }

    public static class Builder {
        private Boolean shouldAlert = null;
        private Boolean shouldSave = null;
        private String description = null;
        private Set<String> extensions = null;
        private Set<String> filenames = null;        
        private Set<String> paths = null;        
        private Set<String> fullPaths = null;        
        private Integer minFileSize = null;
        private Integer maxFileSize = null;
        private Integer minDays = null;
        private String minDate = null;
        private String maxDate = null;

        public Builder() {}
        
        public Builder shouldAlert(boolean shouldAlert) {
            this.shouldAlert = shouldAlert;
            return this;
        }
        
        public Builder shouldSave(boolean shouldSave) {
            this.shouldSave = shouldSave;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder extensions(Set<String> extensions) {
            this.extensions = extensions;
            return this;
        }
        
        public Builder filenames(Set<String> filenames) {
            this.filenames = filenames;
            return this;
        }
        
        public Builder paths(Set<String> paths) {
            this.paths = paths;
            return this;
        }
        
        public Builder fullPaths(Set<String> fullPaths) {
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
        
        public Builder minDate(String minDate) {
            this.minDate = minDate;
            return this;
        }
        
        public Builder maxDate(String maxDate) {
            this.maxDate = maxDate;
            return this;
        }
        
        public LogicalImagerRule build() {
            return new LogicalImagerRule(shouldAlert, shouldSave, description, 
                    extensions, filenames, paths, fullPaths,
                    minFileSize, maxFileSize,
                    minDays, minDate, maxDate
            );
        }
    }
}
