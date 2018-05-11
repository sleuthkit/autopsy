/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.Component;
import java.util.List;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Defines an interface for application specific content viewer
 *
 */
interface FileTypeViewer {

    /**
     * Returns list of MIME types supported by this viewer
     */
    List<String> getSupportedMIMETypes();

    /**
     * Display the given file's content in the view panel
     */
    void setFile(AbstractFile file);

    /**
     * Returns panel
     */
    Component getComponent();

    /**
     * Clears the data in the panel
     *
     * IMPORTANT IF MAKING THIS PUBLIC: I (RC) am not sure that this method
     * belongs in this interface. If we are not going to use setFile(null) as a
     * reset method as in DataContentViewer and DataResultViewer, then this is
     * fine. Otherwise, it is ambiguous.
     */
    void resetComponent();
}
