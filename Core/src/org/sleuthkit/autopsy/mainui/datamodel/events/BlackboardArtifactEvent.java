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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import java.util.Objects;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * An event for an artifact added in a particular type.
 */
public class BlackboardArtifactEvent implements DAOEvent {
    private final BlackboardArtifact.Type artifactType;
    private final long dataSourceId;

    BlackboardArtifactEvent(BlackboardArtifact.Type artifactType, long dataSourceId) {
        this.artifactType = artifactType;
        this.dataSourceId = dataSourceId;
    }

    public BlackboardArtifact.Type getArtifactType() {
        return artifactType;
    }

    public long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 17 * hash + Objects.hashCode(this.artifactType);
        hash = 17 * hash + (int) (this.dataSourceId ^ (this.dataSourceId >>> 32));
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
        final BlackboardArtifactEvent other = (BlackboardArtifactEvent) obj;
        if (this.dataSourceId != other.dataSourceId) {
            return false;
        }
        if (!Objects.equals(this.artifactType, other.artifactType)) {
            return false;
        }
        return true;
    }

    
    @Override
    public Type getType() {
        return Type.RESULT;
    }
}
