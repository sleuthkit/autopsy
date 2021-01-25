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
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;

/**
 * Requests artifacts of a specific type and domain from a given Case.
 */
public class DomainSearchArtifactsRequest {

    private final SleuthkitCase sleuthkitCase;
    private final String domain;
    private final ARTIFACT_TYPE artifactType;

    /**
     * Construct a new DomainSearchArtifactsRequest object.
     *
     * @param sleuthkitCase The case database for the search.
     * @param domain        The domain that artifacts are being requested for.
     * @param artifactType  The type of artifact being requested.
     */
    public DomainSearchArtifactsRequest(SleuthkitCase sleuthkitCase,
            String domain, ARTIFACT_TYPE artifactType) {
        this.sleuthkitCase = sleuthkitCase;
        this.domain = domain;
        this.artifactType = artifactType;
    }

    /**
     * Get the case database for the search.
     *
     * @return The case database for the search.
     */
    public SleuthkitCase getSleuthkitCase() {
        return sleuthkitCase;
    }

    /**
     * Get the domain that artifacts are being requested for.
     *
     * @return The domain that artifacts are being requested for.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Get the type of artifact being requested.
     *
     * @return The type of artifact being requested.
     */
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

    @NbBundle.Messages({
        "# {0} - domain",
        "# {1} - artifactType",
        "DomainSearchArtifactsRequest.toString.text=Domain: {0} ArtifactType: {1}"})
    @Override
    public String toString() {
        return Bundle.DomainSearchArtifactsRequest_toString_text(domain, artifactType.getDisplayName());
    }
}
