/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch.multicase;

import javax.annotation.concurrent.Immutable;

/**
 * A keyword search hit from a multi-case keyword search.
 */
@Immutable
final class SearchHit {

    private final String caseDisplayName;
    private final String caseDirectoryPath;
    private final String dataSourceName;
    private final SourceType sourceType;
    private final String sourceName;
    private final String sourcePath;

    /**
     * Constructs a keyword search hit from a multi-case search.
     * 
     * @param caseDisplayName   The display name of the case where the hit occurred.
     * @param caseDirectoryPath The path of the directory of the case where the hit occurred.
     * @param dataSourceName    The name of the data source within the case
     *                          where the hit occurred.
     * @param sourceType        The type of the source content object.
     * @param sourceName        The name of the source, e.g., a file name, an
     *                          artifact type name, or a report module name.
     * @param sourcePath        The path of the source content, or the path of
     *                          the parent source content object for an artifact
     *                          source.
     */
    SearchHit(String caseDisplayName, String caseDirectoryPath, String dataSourceName, SourceType sourceType, String sourceName, String sourcePath) {
        this.caseDisplayName = caseDisplayName;
        this.caseDirectoryPath = caseDirectoryPath;
        this.dataSourceName = dataSourceName;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.sourcePath = sourcePath;
    }

    /**
     * Gets the display name of the case where the hit
     * occurred.
     *
     * @return The case display name.
     */
    String getCaseDisplayName() {
        return this.caseDisplayName;
    }

    /**
     * Gets the path of the directory of the case where
     * the hit occurred.
     *
     * @return The case directory path.
     */
    String getCaseDirectoryPath() {
        return this.caseDirectoryPath;
    }

    /**
     * Gets the name of the data source within the case where the hit occurred.
     *
     * @return
     */
    String getDataSourceName() {
        return this.dataSourceName;
    }

    /**
     * Gets the type of the source content object.
     *
     * @return The source type.
     */
    SourceType getSourceType() {
        return this.sourceType;
    }

    /**
     * Gets the name of the source, e.g., a file name, an artifact type name, or
     * a report module name.
     *
     * @return The source name.
     */
    String getSourceName() {
        return this.sourceName;
    }

    /**
     * Gets the path of the source content, or the path of the parent source
     * content object for an artifact source.
     *
     * @return The source object path.
     */
    String getSourcePath() {
        return this.sourcePath;
    }

    /**
     * An enumeration of the source types for keyword search hits.
     */
    enum SourceType {
        FILE("File"),
        LOCAL_FILE("Local File"),
        ARTIFACT("Artifact"),
        REPORT("Report");

        private final String displayName;

        private SourceType(String displayName) {
            this.displayName = displayName;
        }

        String getDisplayName() {
            return this.displayName;
        }
    }

}
