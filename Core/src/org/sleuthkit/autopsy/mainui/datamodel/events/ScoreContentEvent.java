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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import org.sleuthkit.autopsy.mainui.datamodel.ScoreViewFilter;

/**
 * An event to signal that deleted files have been added to the given case on
 * the given data source.
 */
public class ScoreContentEvent implements DAOEvent {

    private final ScoreViewFilter filter;
    private final Long dataSourceId;

    public ScoreContentEvent(ScoreViewFilter filter, Long dataSourceId) {
        this.filter = filter;
        this.dataSourceId = dataSourceId;
    }

    public ScoreViewFilter getFilter() {
        return filter;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public Type getType() {
        return Type.RESULT;
    }
}
