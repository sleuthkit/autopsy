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
package org.sleuthkit.autopsy.discovery.ui;

import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.discovery.search.ResultFile;
import org.sleuthkit.autopsy.textsummarizer.TextSummary;

/**
 * Class to wrap all the information necessary for a document summary to be
 * displayed.
 */
final class DocumentWrapper {

    private TextSummary summary;
    private final ResultFile resultFile;

    /**
     * Construct a new DocumentWrapper.
     *
     * @param file The ResultFile which represents the document which the
     *             summary is created for.
     */
    @Messages({"DocumentWrapper.previewInitialValue=Preview not generated yet."})
    DocumentWrapper(ResultFile file) {
        this.summary = new TextSummary(Bundle.DocumentWrapper_previewInitialValue(), null, 0);
        this.resultFile = file;
    }

    /**
     * Set the summary which exists.
     *
     * @param textSummary The TextSummary object which contains the text and
     *                    image which should be displayed as a summary for this
     *                    document.
     */
    void setSummary(TextSummary textSummary) {
        this.summary = textSummary;
    }

    /**
     * Get the ResultFile which represents the document the summary was created
     * for.
     *
     * @return The ResultFile which represents the document file which the
     *         summary was created for.
     */
    ResultFile getResultFile() {
        return resultFile;
    }

    /**
     * Get the summary of the document.
     *
     * @return The TextSummary which is the summary of the document.
     */
    TextSummary getSummary() {
        return summary;
    }
}
