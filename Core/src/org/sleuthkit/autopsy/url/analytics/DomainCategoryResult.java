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
 * The result of finding a match for the host or domain provided as an argument.
 */
@Beta
public interface DomainCategoryResult {            
    /**
     * @return The portion of the suffix from the host or domain that was a
     * match (i.e. 'mail.google.com' or 'hotmail.com').
     */
    String getHostSuffix();

    /**
     * @return The category (i.e. 'Web Email').
     */
    String getCategory();

    /**
     * @return In the event that there would be different matches for additional
     * prefixes, this can return true. For instance, if there was an entry for
     * 'mail.google.com' and 'chatenabled.mail.google.com', a search for
     * 'mail.google.com' would return the host suffix: 'mail.google.com' and
     * 'true' for hasMorePrefixes since an additional category could be added
     * for the 'chatenabled' prefix.
     */
    default boolean hasMorePrefixes() {
        return true;
    }
}
