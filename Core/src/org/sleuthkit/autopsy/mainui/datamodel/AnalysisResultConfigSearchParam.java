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
 * Base class for search params for analysis results that filter by configuration.
 */
public class AnalysisResultConfigSearchParam extends AnalysisResultSearchParam {

    private static final String TYPE_ID = "ANALYSIS_RESULT_CONFIG";

    /**
     * @return The type id for this search parameter.
     */
    public static String getTypeId() {
        return TYPE_ID;
    }

    final String configuration;

    public AnalysisResultConfigSearchParam(BlackboardArtifact.Type artifactType, Long dataSourceId, String configuration) {
        super(artifactType, dataSourceId);
        this.configuration = configuration;
    }

    public String getConfiguration() {
        return configuration;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.configuration);
        hash = 79 * hash + super.hashCode();
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
        final AnalysisResultConfigSearchParam other = (AnalysisResultConfigSearchParam) obj;
        if (!Objects.equals(this.configuration, other.configuration)) {
            return false;
        }
        return super.equals(obj);
    }

}
