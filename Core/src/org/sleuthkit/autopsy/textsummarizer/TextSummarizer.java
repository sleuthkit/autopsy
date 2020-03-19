/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textsummarizer;

import java.io.IOException;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Interface for implementation of summarizers for documents.
 */
public interface TextSummarizer {

    /**
     * Get the name of the TextSummarizer for identification purposes.
     *
     * @return The name of the TextSummarizer.
     */
    String getName();

    /**
     * Summarize the provided abstract file into a summary with a size no
     * greater than the size specified.
     *
     * @param file        The AbstractFile to summarize.
     * @param summarySize The size of the summary to create.
     *
     * @return The summary as a TextSummary object.
     *
     * @throws IOException
     */
    TextSummary summarize(AbstractFile file, int summarySize) throws IOException;
}
