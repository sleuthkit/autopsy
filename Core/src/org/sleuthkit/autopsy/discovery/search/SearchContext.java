/*
 * Autopsy
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
package org.sleuthkit.autopsy.discovery.search;

/**
 * Interface for providing feedback on if a search has been cancelled.
 *
 */
public interface SearchContext {

    /**
     * Returns true if the search has been cancelled, false otherwise.
     *
     * @return True if the search has been cancelled, false otherwise.
     */
    boolean searchIsCancelled();
}
