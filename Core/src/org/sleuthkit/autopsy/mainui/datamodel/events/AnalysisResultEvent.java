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
 * An event for an Analysis Result that is organized by Set names to
 * signal that one has been added or removed on a given data source. 
 */
public class AnalysisResultEvent extends BlackboardArtifactEvent {
    private final String configuration;

    public AnalysisResultEvent(BlackboardArtifact.Type artifactType, String configuration, long dataSourceId) {
        super(artifactType, dataSourceId);
        this.configuration = configuration;
    }

    public String getConfiguration() {
        return configuration;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.configuration);
        hash = 53 * hash + super.hashCode();
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
        final AnalysisResultEvent other = (AnalysisResultEvent) obj;
        if (!Objects.equals(this.configuration, other.configuration)) {
            return false;
        }
        return super.equals(obj);
    }
    
    
}
