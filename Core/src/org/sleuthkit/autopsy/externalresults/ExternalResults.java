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

import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.Content;

/**
 *
 */
final public class ExternalResults {

    private final Content dataSource;
    private final List<Artifact> artifacts = new ArrayList<>();
    private final List<Report> reports = new ArrayList<>();
    private final List<DerivedFile> derivedFiles = new ArrayList<>();

    ExternalResults(Content dataSource) {
        this.dataSource = dataSource;
    }

    Content getDataSource() {
        return this.dataSource;
    }

    Artifact addArtifact(String type, String sourceFilePath) {
        if (type.isEmpty()) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "ExternalResults.addArtifact.exception.msg1.text"));
        }
        if (sourceFilePath.isEmpty()) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "ExternalResults.addArtifact.exception.msg2.text"));
        }
        Artifact artifact = new Artifact(type, sourceFilePath);
        artifacts.add(artifact);
        return artifact;
    }

    List<Artifact> getArtifacts() {
        return Collections.unmodifiableList(artifacts);
    }

    void addReport(String localPath, String sourceModuleName, String reportName) {
        if (localPath.isEmpty()) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "ExternalResults.addReport.exception.msg1.text"));
        }
        if (sourceModuleName.isEmpty()) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "ExternalResults.addReport.exception.msg2.text"));
        }
        Report report = new Report(localPath, sourceModuleName, reportName);
        reports.add(report);
    }

    List<Report> getReports() {
        return Collections.unmodifiableList(reports);
    }

    void addDerivedFile(String localPath, String parentPath) {
        if (localPath.isEmpty()) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "ExternalResults.addDerivedFile.exception.msg1.text"));
        }
        if (parentPath.isEmpty()) {
            throw new IllegalArgumentException(
                    NbBundle.getMessage(this.getClass(), "ExternalResults.addDerivedFile.exception.msg2.text"));
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
                throw new IllegalArgumentException(NbBundle.getMessage(this.getClass(),
                        "ExternalResults.Artifact.addAttribute.exception.msg1.text"));
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException(NbBundle.getMessage(this.getClass(),
                        "ExternalResults.Artifact.addAttribute.exception.msg2.text"));
            }
            if (valueType.isEmpty()) {
                throw new IllegalArgumentException(NbBundle.getMessage(this.getClass(),
                        "ExternalResults.Artifact.addAttribute.exception.msg3.text"));
            }
            attributes.add(new ArtifactAttribute(type, value, valueType, sourceModule));
        }

        List<ArtifactAttribute> getAttributes() {
            return Collections.unmodifiableList(attributes);
        }
    }

    static final class ArtifactAttribute {

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

        private final String localPath;
        private final String sourceModuleName;
        private final String reportName;

        Report(String localPath, String sourceModuleName, String displayName) {
            this.localPath = localPath;
            this.sourceModuleName = sourceModuleName;
            this.reportName = displayName;
        }

        String getLocalPath() {
            return localPath;
        }

        String getSourceModuleName() {
            return sourceModuleName;
        }

        String getReportName() {
            return reportName;
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
