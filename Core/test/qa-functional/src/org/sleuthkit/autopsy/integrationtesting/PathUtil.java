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
package org.sleuthkit.autopsy.integrationtesting;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

/**
 * Retrieves absolute paths relative to a working directory.
 */
final class PathUtil {

    static String getAbsolutePath(String workingDirectory, String relPath) {
        if (StringUtils.isBlank(workingDirectory)) {
            return relPath;
        } else {
            if (Paths.get(relPath).isAbsolute()) {
                return relPath;
            } else {
                return Paths.get(workingDirectory, relPath).toString();   
            }
        }
    }

    static List<String> getAbsolutePaths(String workingDirectory, List<String> relPaths) {
        if (relPaths == null) {
            return null;
        }
        
        return relPaths.stream()
                .map((relPath) -> getAbsolutePath(workingDirectory, relPath))
                .collect(Collectors.toList());
    }

    private PathUtil() {
    }
}
