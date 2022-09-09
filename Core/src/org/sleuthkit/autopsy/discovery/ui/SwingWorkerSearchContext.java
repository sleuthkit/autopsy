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
package org.sleuthkit.autopsy.discovery.ui;

import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.discovery.search.SearchContext;

/**
 * Implementation of SearchContext for searches being performed in the
 * background thread of a SwingWorker.
 */
class SwingWorkerSearchContext implements SearchContext {

    private final SwingWorker<Void, Void> searchWorker;

    /**
     * Construct a new SwingWorkerSearchContext.
     *
     * @param worker The SwingWorker the search is being performed in.
     */
    SwingWorkerSearchContext(SwingWorker<Void, Void> worker) {
        searchWorker = worker;
    }

    @Override
    public boolean searchIsCancelled() {
        return searchWorker.isCancelled();
    }
}
