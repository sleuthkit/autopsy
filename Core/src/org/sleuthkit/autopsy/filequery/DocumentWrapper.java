/*
 * Autopsy
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
package org.sleuthkit.autopsy.filequery;

import org.sleuthkit.autopsy.coreutils.Logger;


public class DocumentWrapper {

    private String preview;
    private final ResultFile resultFile;
    private static final Logger logger = Logger.getLogger(DocumentWrapper.class.getName());
    //string extract utility


    /**
     * Construct a new ImageThumbnailsWrapper.
     *
     * @param file The ResultFile which represents the document which the
     *             summary is created for.
     */
    DocumentWrapper(ResultFile file) {
        this.preview = "Preview being generated.";
        this.resultFile = file;
    }

    /**
     * Set the preview summary which exists.
     *
     * @param summary The String which summarizes this document.
     */
    void setSummary(String summary) {
        this.preview = summary;
    }

    /**
     * Get the ResultFile which represents the document the preview summary was
     * created for.
     *
     * @return The ResultFile which represents the image file which the
     *         thumbnail was created for.
     */
    ResultFile getResultFile() {
        return resultFile;
    }

    /**
     * Get the preview summary of the document.
     *
     * @return The String which is the preview of the document.
     */
    String getPreview() {
        return preview;
    }
}
