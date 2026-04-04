/*
 * Autopsy Forensic Browser
 *
 * Copyright 2025 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Extension point for adding supplemental property sheet sections to
 * BlackboardArtifactNode. Implementations are discovered via the global
 * NetBeans Lookup and should be registered with @ServiceProvider.
 *
 * Example registration in the implementing module:
 * <pre>
 *   {@literal @}ServiceProvider(service = ArtifactPropertyEnricher.class)
 *   public class MyEnricher implements ArtifactPropertyEnricher { ... }
 * </pre>
 */
public interface ArtifactPropertyEnricher {

    /**
     * Returns an additional Sheet.Set to append to the property sheet for the
     * given artifact, or null if this enricher has no supplemental data for it.
     *
     * Implementations must be efficient: this is called on every node
     * expansion. Results should be cached where appropriate.
     *
     * @param artifact The artifact whose property sheet is being built.
     *
     * @return A Sheet.Set to append, or null if none.
     *
     * @throws TskCoreException If there is an error accessing the case
     *                          database.
     */
    Sheet.Set getEnrichment(BlackboardArtifact artifact) throws TskCoreException;
}
