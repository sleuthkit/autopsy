/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.io.FilenameUtils;

/**
 * Utility class for checking whether a data source/file should be processed
 * in an automated setting. The goal is to not spend time analyzing large 
 * files that Autopsy can not handle yet. 
 */
public final class SupportedDataSources {
    
    private static final List<String> UNSUPPORTED_EXTENSIONS = Arrays.asList("xry", "dar");
    
    /**
     * Check whether a file should be added to a case, either as a data source or part of a
     * logical file set.
     * 
     * @param fileName The name of the file.
     * 
     * @return true if the file is currently unsupported and should be skipped, false otherwise.
     */
    public static boolean shouldSkipFile(String fileName) {
        String ext = FilenameUtils.getExtension(fileName);
        if (ext == null) {
            return false;
        }
        return UNSUPPORTED_EXTENSIONS.contains(ext.toLowerCase());
    }
    
    private SupportedDataSources() {
        // Static class
    }
}
