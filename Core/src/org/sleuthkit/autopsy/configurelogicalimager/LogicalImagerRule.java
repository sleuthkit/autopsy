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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * The class definition for the Logical Imager Rule.
 */
public class LogicalImagerRule {

    private final Boolean shouldAlert;
    private final Boolean shouldSave;
    private final String description;
    private List<String> extensions = new ArrayList<>();
    private List<String> filenames = new ArrayList<>();
    private List<String> paths = new ArrayList<>();
    private List<String> fullPaths = new ArrayList<>();
    private Integer minFileSize = 0;
    private Integer maxFileSize = 0;
    private Integer minDays = 0;
    private String minDate;
    private String maxDate;
    
    LogicalImagerRule(Boolean shouldAlert, Boolean shouldSave, String description,
            List<String> extensions,
            List<String> filenames,
            List<String> paths,
            List<String> fullPaths,
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
        private List<String> extensions = null;
        private List<String> filenames = null;        
        private List<String> paths = null;        
        private List<String> fullPaths = null;        
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
