/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import java.io.Serializable;
import java.util.HashMap;

class FileExtMismatchSettings implements Serializable {
    private static final long serialVersionUID = 1L;
    private final HashMap<String, String[]> sigTypeToExtMap;

    FileExtMismatchSettings(HashMap<String, String[]> sigTypeToExtMap) {
        this.sigTypeToExtMap = sigTypeToExtMap;
    }

    /**
     * @return the sig type to extension map
     */
    HashMap<String, String[]> getSigTypeToExtMap() {
        return sigTypeToExtMap;
    }
    
    
}
