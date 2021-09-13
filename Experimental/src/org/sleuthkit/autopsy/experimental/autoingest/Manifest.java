/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;

@Immutable
public final class Manifest implements Serializable {

    private static final long serialVersionUID = 1L;
    
    // NOTE: Path is not Serializable. That's why we have to have a String as well,
    // for scenarios when we send Manifest via ActiveMQ to other nodes.
    private transient Path filePath;
    private final String filePathString;
    private transient Path dataSourcePath;
    private final String dataSourcePathString;
    
    private final Date dateFileCreated;
    private final String caseName;
    private final String deviceId;
    private final String dataSourceFileName;
    private final Map<String, String> manifestProperties;

    public Manifest(Path manifestFilePath, Date dateFileCreated, String caseName, String deviceId, Path dataSourcePath, Map<String, String> manifestProperties) {
        this.filePathString = manifestFilePath.toString();
        this.filePath = Paths.get(filePathString);

        this.dateFileCreated = new Date(dateFileCreated.getTime());
        this.caseName = caseName;
        this.deviceId = deviceId;
        if (null != dataSourcePath) {
            this.dataSourcePathString = dataSourcePath.toString();
            this.dataSourcePath = Paths.get(dataSourcePathString);
            dataSourceFileName = dataSourcePath.getFileName().toString();
        } else {
            this.dataSourcePathString = "";
            this.dataSourcePath = Paths.get("");
            dataSourceFileName = "";
        }
        this.manifestProperties = new HashMap<>(manifestProperties);
    }
    
    public Path getFilePath() {
        // after potential deserialization Path will be null because it is transient
        if (filePath == null) {
            this.filePath = Paths.get(filePathString);
        }
        return this.filePath;
    }

    public Date getDateFileCreated() {
        return dateFileCreated;
    }

    public String getCaseName() {
        return caseName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Path getDataSourcePath() {
        // after potential deserialization Path will be null because it is transient
        if (dataSourcePath == null) {
            this.dataSourcePath = Paths.get(dataSourcePathString);
        }
        return dataSourcePath;
    }

    public String getDataSourceFileName() {
        return dataSourceFileName;
    }
    
    public Map<String, String> getManifestProperties() {
        return new HashMap<>(manifestProperties);
    }

}
