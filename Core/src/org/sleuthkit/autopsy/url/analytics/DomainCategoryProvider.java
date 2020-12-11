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
package org.sleuthkit.autopsy.url.analytics;

import com.google.common.annotations.Beta;
import org.sleuthkit.autopsy.ingest.IngestModule;
import java.io.Closeable;
import java.io.IOException;

/**
 * Interface providing the category of a domain/host for the purposes of
 * creating TSK_WEB_CATEGORIZATION artifacts. These implementations are used in
 * RecentActivity as a part of the ingest process. Implementers of this class
 * should have a no-argument constructor in order to be properly instantiated.
 */
@Beta
public interface DomainCategoryProvider extends Closeable {

    /**
     * Provides the DomainCategory for a given domain/host or null if none can
     * be determined.
     *
     * @param domain The domain of the url.
     * @param host The host of the url.
     * @return The domain category if the domain/host combination was found or
     * null if not.
     */
    DomainCategoryResult getCategory(String domain, String host);

    /**
     * Initializes this provider in preparation to handle 'getCategory' requests
     * during ingest. Conceivably, the same instance of this class may have this
     * called multiple times and should handle that possibility gracefully.
     *
     * @throws IngestModule.IngestModuleException
     */
    default void initialize() throws IngestModule.IngestModuleException {
    }

    /**
     * These providers close methods are explicitly called when ingest is
     * finished. Conceivably, the same instance of this class may have this
     * method called multiple times and should handle that possibility
     * gracefully.
     *
     * @throws IOException
     */
    @Override
    default void close() throws IOException {
    }
}
