/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
    private final String filePath;
    private final Date dateFileCreated;
    private final String caseName;
    private final String deviceId;
    private final String dataSourcePath;
    private final Map<String, String> manifestProperties;

    public Manifest(Path manifestFilePath, Date dateFileCreated, String caseName, String deviceId, Path dataSourcePath, Map<String, String> manifestProperties) {
        this.filePath = manifestFilePath.toString();
        this.dateFileCreated = dateFileCreated;
        this.caseName = caseName;
        this.deviceId = deviceId;
        if (null != dataSourcePath) {
            this.dataSourcePath = dataSourcePath.toString(); 
        } else {
            this.dataSourcePath = "";
        }
        this.manifestProperties = new HashMap<>(manifestProperties);
    }    
    
    public Path getFilePath() {
        return Paths.get(this.filePath);
    }

    public Date getDateFileCreated() {
        return new Date(this.dateFileCreated.getTime());
    }

    public String getCaseName() {
        return caseName;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Path getDataSourcePath() {
        return Paths.get(dataSourcePath);
    }

    public String getDataSourceFileName() {
        return Paths.get(dataSourcePath).getFileName().toString();
    }
    
    public Map<String, String> getManifestProperties() {
        return new HashMap<>(manifestProperties);
    }

}
