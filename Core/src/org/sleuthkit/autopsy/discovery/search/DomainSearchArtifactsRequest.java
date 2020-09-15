/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import java.util.Objects;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Requests artifacts of a specific type and domain from a given Case.
 */
public class DomainSearchArtifactsRequest {

    private final SleuthkitCase sleuthkitCase;
    private final String domain;
    private final ARTIFACT_TYPE artifactType;

    public DomainSearchArtifactsRequest(SleuthkitCase sleuthkitCase,
            String domain, ARTIFACT_TYPE artifactType) {
        this.sleuthkitCase = sleuthkitCase;
        this.domain = domain;
        this.artifactType = artifactType;
    }

    public SleuthkitCase getSleuthkitCase() {
        return sleuthkitCase;
    }

    public String getDomain() {
        return domain;
    }

    public ARTIFACT_TYPE getArtifactType() {
        return artifactType;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        if (!(other instanceof DomainSearchArtifactsRequest)) {
            return false;
        }

        DomainSearchArtifactsRequest otherRequest = (DomainSearchArtifactsRequest) other;
        return this.sleuthkitCase == otherRequest.getSleuthkitCase()
                && this.domain.equals(otherRequest.getDomain())
                && this.artifactType == otherRequest.getArtifactType();
    }

    @Override
    public int hashCode() {
        return 79 * 5 + Objects.hash(this.domain, this.artifactType);
    }
}
