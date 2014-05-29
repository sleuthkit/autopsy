/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.externalresults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
final class ExternalResults {

    // RJCTODO: Consider adding data source Content object to results
    private final List<Artifact> artifacts = new ArrayList<>();
    private final List<Report> reports = new ArrayList<>();
    private final List<DerivedFile> derivedFiles = new ArrayList<>();

    Artifact addArtifact(String type, String sourceFilePath) {
        if (type.isEmpty()) {
            throw new IllegalArgumentException("type argument is empty");
        }
        if (sourceFilePath.isEmpty()) {
            throw new IllegalArgumentException("source argument is empty");
        }
        Artifact artifact = new Artifact(type, sourceFilePath);
        artifacts.add(artifact);
        return artifact;
    }

    List<Artifact> getArtifacts() {
        return Collections.unmodifiableList(artifacts);
    }

    void addReport(String displayName, String localPath) {
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName argument is empty");
        }
        if (localPath.isEmpty()) {
            throw new IllegalArgumentException("localPath argument is empty");
        }
        Report report = new Report(displayName, localPath);
        reports.add(report);
    }

    List<Report> getReports() {
        return Collections.unmodifiableList(reports);
    }

    void addDerivedFile(String localPath, String parentPath) {
        if (localPath.isEmpty()) {
            throw new IllegalArgumentException("localPath argument is empty");
        }
        if (parentPath.isEmpty()) {
            throw new IllegalArgumentException("parentPath argument is empty");
        }
        DerivedFile file = new DerivedFile(localPath, parentPath);
        derivedFiles.add(file);
    }

    List<DerivedFile> getDerivedFiles() {
        return Collections.unmodifiableList(derivedFiles);
    }

    static final class Artifact {

        private final String type;
        private final String sourceFilePath;
        private final ArrayList<ArtifactAttribute> attributes = new ArrayList<>();

        Artifact(String type, String sourceFilePath) {
            this.type = type;
            this.sourceFilePath = sourceFilePath;
        }

        String getType() {
            return type;
        }

        String getSourceFilePath() {
            return sourceFilePath;
        }

        void addAttribute(String type, String value, String valueType, String sourceModule) {
            if (type.isEmpty()) {
                throw new IllegalArgumentException("type argument is empty");
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("value argument is empty");
            }
            if (value.isEmpty()) {
                valueType = ArtifactAttribute.DEFAULT_VALUE_TYPE;
            }
            if (sourceModule.isEmpty()) {
                throw new IllegalArgumentException("sourceModule argument is empty");
            }
            attributes.add(new ArtifactAttribute(type, value, valueType, sourceModule));
        }

        List<ArtifactAttribute> getAttributes() {
            return Collections.unmodifiableList(attributes);
        }
    }

    static final class ArtifactAttribute {

        private static final String DEFAULT_VALUE_TYPE = "text";
        private final String type;
        private final String valueType;
        private final String value;
        private final String sourceModule;

        private ArtifactAttribute(String type, String value, String valueType, String sourceModule) {
            this.type = type;
            this.value = value;
            this.valueType = valueType;
            this.sourceModule = sourceModule;
        }

        String getType() {
            return type;
        }
        
        String getValue() {
            return value;
        }

        String getValueType() {
            return valueType;
        }

        String getSourceModule() {
            return sourceModule;
        }
    }

    static final class Report {

        private final String displayName;
        private final String localPath;

        Report(String displayName, String localPath) {
            this.displayName = displayName;
            this.localPath = localPath;
        }

        String getDisplayName() {
            return displayName;
        }

        String getLocalPath() {
            return localPath;
        }
    }

    static final class DerivedFile {

        private final String localPath;
        private final String parentPath;

        DerivedFile(String localPath, String parentPath) {
            this.localPath = localPath;
            this.parentPath = parentPath;
        }

        String getLocalPath() {
            return localPath;
        }

        String getParentPath() {
            return parentPath;
        }
    }
}
