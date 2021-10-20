/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel;

import java.util.Objects;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Key for data artifact in order to retrieve data from DAO.
 */
public class DataArtifactSearchParam extends DataSourceFilteredSearchParams {
    private final BlackboardArtifact.Type artifactType;
    private final Long dataSourceId;

    public DataArtifactSearchParam(BlackboardArtifact.Type artifactType, Long dataSourceId, long startItem, Long maxResultsCount) {
        super(startItem, maxResultsCount, dataSourceId);
        this.artifactType = artifactType;
        this.dataSourceId = dataSourceId;
    }

    public BlackboardArtifact.Type getArtifactType() {
        return artifactType;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Objects.hashCode(this.artifactType);
        hash = 13 * hash + Objects.hashCode(this.dataSourceId);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DataArtifactSearchParam other = (DataArtifactSearchParam) obj;
        if (!Objects.equals(this.artifactType, other.artifactType)) {
            return false;
        }
        if (!Objects.equals(this.dataSourceId, other.dataSourceId)) {
            return false;
        }
        return true;
    }
    
    
}
