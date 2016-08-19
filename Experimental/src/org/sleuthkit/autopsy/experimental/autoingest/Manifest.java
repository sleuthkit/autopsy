/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;

/**
 * RJCTODO
 */
@Immutable
public final class Manifest implements Serializable {

    private static final long serialVersionUID = 1L;
    private final String filePath;
    private final Date dateFileCreated;
    private final String caseName;
    private final String deviceId;
    private final String dataSourcePath;
    private final Map<String, String> manifestProperties;

    /**
     * RJCTODO
     *
     * @param manifestFilePath
     * @param caseName
     * @param deviceId
     * @param dataSourcePath
     * @param manifestProperties
     *
     * @throws IOException
     */
    public Manifest(Path manifestFilePath, String caseName, String deviceId, Path dataSourcePath, Map<String, String> manifestProperties) throws IOException {
        this.filePath = manifestFilePath.toString();
        BasicFileAttributes attrs = Files.readAttributes(manifestFilePath, BasicFileAttributes.class);
        this.dateFileCreated = new Date(attrs.creationTime().toMillis());
        this.caseName = caseName;
        this.deviceId = deviceId;
        this.dataSourcePath = dataSourcePath.toString();
        this.manifestProperties = new HashMap<>(manifestProperties);
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public Path getFilePath() {
        return Paths.get(this.filePath);
    }

    /**
     * RJCTODO
     * 
     * @return
     * @throws IOException 
     */
    public Date getDateFileCreated() {
        return this.dateFileCreated;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public String getCaseName() {
        return caseName;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    public Path getDataSourcePath() {
        return Paths.get(dataSourcePath);
    }

    /**
     * RJCTODO
     * @return 
     */
    public String getDataSourceFileName() {
        return Paths.get(dataSourcePath).getFileName().toString();
    }
    
    /**
     * RJCTODO
     *
     * @return
     */
    public Map<String, String> getManifestProperties() {
        return new HashMap<>(manifestProperties);
    }

}
