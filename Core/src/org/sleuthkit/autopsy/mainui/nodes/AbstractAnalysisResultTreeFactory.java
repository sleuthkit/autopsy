/*
 * Autopsy Forensic Browser
 *
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DeleteAnalysisResultEvent;

/**
 * An abstract analysis result factory that handles 'DeleteAnalysisResultEvent'
 * events and updates the tree accordingly.
 */
public abstract class AbstractAnalysisResultTreeFactory<T> extends TreeChildFactory<T> {

    @Override
    protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
        for (DAOEvent evt : aggEvt.getEvents()) {
            if (evt instanceof DeleteAnalysisResultEvent && evt.getType() == DAOEvent.Type.TREE) {
                super.update();
                return;
            }
        }

        super.handleDAOAggregateEvent(aggEvt);
    }
}
