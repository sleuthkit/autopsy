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

import org.openide.util.NbBundle.Messages;

/**
 * Class to wrap all the information necessary for a document preview to be
 * displayed.
 */
public class DocumentWrapper {

    private String preview;
    private final ResultFile resultFile;

    /**
     * Construct a new DocumentWrapper.
     *
     * @param file The ResultFile which represents the document which the
     *             preview summary is created for.
     */
    @Messages({"DocumentWrapper.previewInitialValue=Preview not generated yet."})
    DocumentWrapper(ResultFile file) {
        this.preview = Bundle.DocumentWrapper_previewInitialValue();
        this.resultFile = file;
    }

    /**
     * Set the preview summary which exists.
     *
     * @param preview The String which should be displayed as a preview for this
     *                document.
     */
    void setPreview(String preview) {
        this.preview = preview;
    }

    /**
     * Get the ResultFile which represents the document the preview summary was
     * created for.
     *
     * @return The ResultFile which represents the document file which the
     *         preview was created for.
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
