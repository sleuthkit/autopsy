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

/**
 * Interface providing the category of a domain/host. Implementers of this class
 * should have a no-argument constructor in order to be properly instantiated,
 * and should have a class annotation of '(at)ServiceProvider(service =
 * DomainCategoryProvider.class)'.
 *
 * NOTE: The (at)SuppressWarnings("try") on the class is to suppress warnings
 * relating to the fact that the close method can throw an InterruptedException
 * since Exception can encompass the InterruptedException. See the following
 * github issue and bugs for more information:
 * https://github.com/joyent/java-manta/issues/322#issuecomment-326391239,
 * https://bugs.openjdk.java.net/browse/JDK-8155591,
 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=350279.
 */
@Beta
@SuppressWarnings("try")
public interface DomainCategorizer extends AutoCloseable {

    /**
     * Provides the DomainCategory for a given domain/host or null if none can
     * be determined.
     *
     * @param domain The domain of the url.
     * @param host The host of the url.
     * @return The domain category if the domain/host combination was found or
     * null if not.
     */
    DomainCategory getCategory(String domain, String host) throws DomainCategorizerException;

    /**
     * Initializes this provider in preparation to handle 'getCategory' requests
     * during ingest. Conceivably, the same instance of this class may have this
     * called multiple times and should handle that possibility gracefully.
     *
     * @throws DomainCategorizerException
     */
    default void initialize() throws DomainCategorizerException {
    }

    /**
     * These providers close methods are explicitly called when ingest is
     * finished. Conceivably, the same instance of this class may have this
     * method called multiple times and should handle that possibility
     * gracefully.
     *
     * @throws Exception
     */
    @Override
    default void close() throws Exception {
    }
}
